package com.logistics.common.ratelimit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Token Bucket Rate Limiter designed specifically for event processing, message consumers,
 * and outbound worker tasks.
 * <p>
 * Regulates throughput and prevents downstream saturation without dropping or losing events.
 */
public class EventRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(EventRateLimiter.class);

    private final double permitsPerSecond;
    private final double maxBurstPermits;
    private double availablePermits;
    private long lastRefillTimeNanos;
    private final ReentrantLock lock = new ReentrantLock();

    public EventRateLimiter(double permitsPerSecond, double maxBurstPermits) {
        if (permitsPerSecond <= 0) {
            throw new IllegalArgumentException("Permits per second must be positive: " + permitsPerSecond);
        }
        if (maxBurstPermits < 1.0) {
            throw new IllegalArgumentException("Max burst permits must be >= 1.0: " + maxBurstPermits);
        }
        this.permitsPerSecond = permitsPerSecond;
        this.maxBurstPermits = maxBurstPermits;
        this.availablePermits = maxBurstPermits;
        this.lastRefillTimeNanos = System.nanoTime();
    }

    public static EventRateLimiter of(double permitsPerSecond) {
        return new EventRateLimiter(permitsPerSecond, Math.max(1.0, permitsPerSecond));
    }

    private void refill() {
        long now = System.nanoTime();
        long elapsedNanos = now - lastRefillTimeNanos;
        if (elapsedNanos > 0) {
            double newPermits = (elapsedNanos / 1_000_000_000.0) * permitsPerSecond;
            availablePermits = Math.min(maxBurstPermits, availablePermits + newPermits);
            lastRefillTimeNanos = now;
        }
    }

    /**
     * Attempts to acquire 1 permit without waiting.
     *
     * @return true if permit was acquired, false if rate limit reached
     */
    public boolean tryAcquire() {
        lock.lock();
        try {
            refill();
            if (availablePermits >= 1.0) {
                availablePermits -= 1.0;
                return true;
            }
            return false;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Attempts to acquire 1 permit, waiting up to the specified timeout if necessary.
     *
     * @param timeout maximum time to wait
     * @param unit time unit
     * @return true if permit acquired, false if timed out
     * @throws InterruptedException if interrupted while waiting
     */
    public boolean tryAcquire(long timeout, TimeUnit unit) throws InterruptedException {
        long timeoutNanos = unit.toNanos(timeout);
        long deadline = System.nanoTime() + timeoutNanos;

        while (true) {
            lock.lock();
            try {
                refill();
                if (availablePermits >= 1.0) {
                    availablePermits -= 1.0;
                    return true;
                }

                double deficit = 1.0 - availablePermits;
                long waitNanos = (long) Math.ceil((deficit / permitsPerSecond) * 1_000_000_000.0);
                long remainingNanos = deadline - System.nanoTime();

                if (waitNanos > remainingNanos || remainingNanos <= 0) {
                    return false;
                }

                long sleepMs = TimeUnit.NANOSECONDS.toMillis(Math.min(waitNanos, remainingNanos));
                int sleepNanos = (int) (Math.min(waitNanos, remainingNanos) % 1_000_000);
                if (sleepMs > 0 || sleepNanos > 0) {
                    Thread.sleep(Math.max(1, sleepMs), sleepNanos);
                }
            } finally {
                lock.unlock();
            }
        }
    }

    /**
     * Blocks until 1 permit becomes available. Guarantees no event is dropped.
     */
    public void acquire() {
        try {
            while (!tryAcquire(10, TimeUnit.SECONDS)) {
                log.debug("Waiting for rate limit permit to process event...");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while waiting for rate limiter permit", e);
        }
    }

    /**
     * Asynchronously acquires 1 permit using a ScheduledExecutorService without blocking threads.
     *
     * @param executor scheduler to schedule delayed permit grants
     * @return CompletableFuture completed when permit is acquired
     */
    public CompletableFuture<Void> acquireAsync(ScheduledExecutorService executor) {
        if (tryAcquire()) {
            return CompletableFuture.completedFuture(null);
        }

        CompletableFuture<Void> future = new CompletableFuture<>();
        long delayMs = (long) Math.ceil((1.0 / permitsPerSecond) * 1000.0);

        executor.schedule(() -> {
            acquireAsync(executor).whenComplete((res, err) -> {
                if (err != null) future.completeExceptionally(err);
                else future.complete(null);
            });
        }, Math.max(10, delayMs), TimeUnit.MILLISECONDS);

        return future;
    }

    public double getPermitsPerSecond() {
        return permitsPerSecond;
    }

    public double getMaxBurstPermits() {
        return maxBurstPermits;
    }

    public double getAvailablePermits() {
        lock.lock();
        try {
            refill();
            return availablePermits;
        } finally {
            lock.unlock();
        }
    }
}

