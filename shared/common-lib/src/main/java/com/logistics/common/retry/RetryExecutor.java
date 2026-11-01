package com.logistics.common.retry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Robust execution engine for resilient operations with exponential backoff,
 * jitter, downstream Retry-After obedience, non-blocking asynchronous support,
 * and structured observability.
 */
public class RetryExecutor {

    private static final Logger log = LoggerFactory.getLogger(RetryExecutor.class);

    private final RetryMetrics metrics;

    public RetryExecutor() {
        this(RetryMetrics.global());
    }

    public RetryExecutor(RetryMetrics metrics) {
        this.metrics = metrics != null ? metrics : RetryMetrics.global();
    }

    /**
     * Executes a synchronous callable task under the given RetryPolicy.
     *
     * @param operationName human-readable identifier for structured logging & metrics
     * @param policy        the RetryPolicy defining limits, backoff, and error predicates
     * @param action        the task to execute
     * @param <T>           the return type
     * @return the result of the successful task
     * @throws RetryExhaustedException if all retry attempts are exhausted
     * @throws Exception               if a non-retryable error occurs or interrupted
     */
    public <T> T execute(String operationName, RetryPolicy policy, Callable<T> action) throws Exception {
        Objects.requireNonNull(operationName, "operationName must not be null");
        Objects.requireNonNull(policy, "policy must not be null");
        Objects.requireNonNull(action, "action must not be null");

        RetryContext context = new RetryContext(operationName, policy.getMaxRetries());
        BackoffCalculator backoffCalculator = new BackoffCalculator(policy);
        metrics.recordInvocation();

        while (true) {
            context.incrementAttempt();

            try {
                T result = action.call();
                context.recordSuccess();
                metrics.recordSuccess(context.getAttempt());

                if (context.getAttempt() > 1) {
                    log.info("PB-052 [RETRY_SUCCESS] op='{}', attempt={}/{}, totalDelay={}ms, elapsed={}ms",
                            operationName, context.getAttempt(), policy.getMaxRetries() + 1,
                            context.getTotalDelay().toMillis(), context.getElapsedTime().toMillis());
                } else {
                    log.debug("PB-052 [SUCCESS_FIRST_TRY] op='{}', elapsed={}ms",
                            operationName, context.getElapsedTime().toMillis());
                }

                return result;

            } catch (Throwable t) {
                // Check if error is retryable according to policy
                boolean retryable = policy.isRetryable(t);

                if (!retryable) {
                    context.recordNonRetryable(t);
                    metrics.recordNonRetryable();
                    log.warn("PB-052 [NON_RETRYABLE_ERROR] op='{}', attempt={}/{}, errorType={}, message='{}'",
                            operationName, context.getAttempt(), policy.getMaxRetries() + 1,
                            t.getClass().getSimpleName(), t.getMessage());
                    throw unwrapOrRethrow(t);
                }

                // Check if maximum retry attempts are exhausted
                // e.g. If maxRetries = 3: attempt 1 (initial), attempt 2 (retry 1), attempt 3 (retry 2), attempt 4 (retry 3).
                if (context.getAttempt() > policy.getMaxRetries()) {
                    context.recordExhausted(t);
                    metrics.recordExhausted();
                    log.error("PB-052 [RETRY_EXHAUSTED] op='{}', attempts={}/{}, totalDelay={}ms, elapsed={}ms, lastError='{}'",
                            operationName, context.getAttempt(), policy.getMaxRetries() + 1,
                            context.getTotalDelay().toMillis(), context.getElapsedTime().toMillis(), t.getMessage());
                    throw new RetryExhaustedException(operationName, context.getAttempt(), context.getElapsedTime(), t);
                }

                // Compute exponential backoff delay with jitter and optional Retry-After
                Duration downstreamRetryAfter = extractRetryAfter(t);
                Duration backoff = backoffCalculator.computeBackoff(context.getAttempt(), downstreamRetryAfter);

                context.recordDelay(backoff);
                context.recordRetrying(t);
                metrics.recordRetry(operationName);

                log.warn("PB-052 [RETRYING] op='{}', failedAttempt={}/{}, nextDelay={}ms (downstreamRetryAfter={}), error='{}'",
                        operationName, context.getAttempt(), policy.getMaxRetries() + 1,
                        backoff.toMillis(), downstreamRetryAfter != null ? downstreamRetryAfter.toMillis() + "ms" : "none",
                        t.getMessage());

                try {
                    Thread.sleep(backoff.toMillis());
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RetryExhaustedException(operationName, context.getAttempt(), context.getElapsedTime(), ie);
                }
            }
        }
    }

    /**
     * Executes a runnable action under the given RetryPolicy.
     */
    public void run(String operationName, RetryPolicy policy, Runnable action) throws Exception {
        execute(operationName, policy, () -> {
            action.run();
            return null;
        });
    }

    /**
     * Non-blocking asynchronous execution using ScheduledExecutorService.
     * Guarantees worker threads and connections are NOT held blocked during the backoff interval.
     */
    public <T> CompletableFuture<T> executeAsync(String operationName,
                                                 RetryPolicy policy,
                                                 ScheduledExecutorService scheduler,
                                                 Supplier<CompletableFuture<T>> actionSupplier) {
        Objects.requireNonNull(operationName, "operationName must not be null");
        Objects.requireNonNull(policy, "policy must not be null");
        Objects.requireNonNull(scheduler, "scheduler must not be null");
        Objects.requireNonNull(actionSupplier, "actionSupplier must not be null");

        RetryContext context = new RetryContext(operationName, policy.getMaxRetries());
        BackoffCalculator backoffCalculator = new BackoffCalculator(policy);
        metrics.recordInvocation();

        CompletableFuture<T> resultFuture = new CompletableFuture<>();
        executeAsyncInternal(operationName, policy, scheduler, actionSupplier, context, backoffCalculator, resultFuture);
        return resultFuture;
    }

    private <T> void executeAsyncInternal(String operationName,
                                          RetryPolicy policy,
                                          ScheduledExecutorService scheduler,
                                          Supplier<CompletableFuture<T>> actionSupplier,
                                          RetryContext context,
                                          BackoffCalculator backoffCalculator,
                                          CompletableFuture<T> resultFuture) {
        context.incrementAttempt();

        CompletableFuture<T> actionFuture;
        try {
            actionFuture = actionSupplier.get();
        } catch (Throwable t) {
            actionFuture = CompletableFuture.failedFuture(t);
        }

        actionFuture.whenComplete((result, throwable) -> {
            if (throwable == null) {
                context.recordSuccess();
                metrics.recordSuccess(context.getAttempt());

                if (context.getAttempt() > 1) {
                    log.info("PB-052 [ASYNC_RETRY_SUCCESS] op='{}', attempt={}/{}, totalDelay={}ms, elapsed={}ms",
                            operationName, context.getAttempt(), policy.getMaxRetries() + 1,
                            context.getTotalDelay().toMillis(), context.getElapsedTime().toMillis());
                }
                resultFuture.complete(result);
                return;
            }

            Throwable actualError = (throwable instanceof java.util.concurrent.CompletionException && throwable.getCause() != null)
                    ? throwable.getCause()
                    : throwable;

            boolean retryable = policy.isRetryable(actualError);

            if (!retryable) {
                context.recordNonRetryable(actualError);
                metrics.recordNonRetryable();
                log.warn("PB-052 [ASYNC_NON_RETRYABLE_ERROR] op='{}', attempt={}/{}, errorType={}, message='{}'",
                        operationName, context.getAttempt(), policy.getMaxRetries() + 1,
                        actualError.getClass().getSimpleName(), actualError.getMessage());
                resultFuture.completeExceptionally(actualError);
                return;
            }

            if (context.getAttempt() > policy.getMaxRetries()) {
                context.recordExhausted(actualError);
                metrics.recordExhausted();
                log.error("PB-052 [ASYNC_RETRY_EXHAUSTED] op='{}', attempts={}/{}, totalDelay={}ms, elapsed={}ms, lastError='{}'",
                        operationName, context.getAttempt(), policy.getMaxRetries() + 1,
                        context.getTotalDelay().toMillis(), context.getElapsedTime().toMillis(), actualError.getMessage());
                resultFuture.completeExceptionally(new RetryExhaustedException(operationName, context.getAttempt(), context.getElapsedTime(), actualError));
                return;
            }

            Duration downstreamRetryAfter = extractRetryAfter(actualError);
            Duration backoff = backoffCalculator.computeBackoff(context.getAttempt(), downstreamRetryAfter);

            context.recordDelay(backoff);
            context.recordRetrying(actualError);
            metrics.recordRetry(operationName);

            log.warn("PB-052 [ASYNC_RETRYING_SCHEDULED] op='{}', failedAttempt={}/{}, nextDelay={}ms, error='{}'",
                    operationName, context.getAttempt(), policy.getMaxRetries() + 1,
                    backoff.toMillis(), actualError.getMessage());

            // Non-blocking timer: frees the calling thread during the sleep period
            scheduler.schedule(() -> executeAsyncInternal(operationName, policy, scheduler, actionSupplier, context, backoffCalculator, resultFuture),
                    backoff.toMillis(), TimeUnit.MILLISECONDS);
        });
    }

    private Duration extractRetryAfter(Throwable t) {
        if (t instanceof RetryAfterProvider provider) {
            return provider.getRetryAfter();
        }
        if (t != null && t.getCause() instanceof RetryAfterProvider provider) {
            return provider.getRetryAfter();
        }
        return null;
    }

    private Exception unwrapOrRethrow(Throwable t) {
        if (t instanceof Exception e) {
            return e;
        }
        return new RuntimeException(t);
    }
}
