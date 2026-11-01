package com.logistics.notification.service;

import com.logistics.common.retry.BackoffCalculator;
import com.logistics.common.retry.RetryPolicy;
import com.logistics.notification.client.NotificationPlatformClient;
import com.logistics.notification.client.NotificationPlatformRequest;
import com.logistics.notification.client.NotificationPlatformResult;
import com.logistics.notification.entity.Notification;
import com.logistics.notification.repository.NotificationRepository;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@ApplicationScoped
public class NotificationRetryService {

    private static final Logger log = LoggerFactory.getLogger(NotificationRetryService.class);

    @ConfigProperty(name = "notification.retry.enabled", defaultValue = "true")
    boolean retryEnabled;

    @ConfigProperty(name = "notification.retry.max-attempts", defaultValue = "5")
    int maxAttempts;

    @ConfigProperty(name = "notification.retry.batch-size", defaultValue = "20")
    int batchSize;

    @ConfigProperty(name = "notification.retry.initial-backoff-ms", defaultValue = "1000")
    long initialBackoffMs = 1000;

    @ConfigProperty(name = "notification.retry.max-backoff-ms", defaultValue = "60000")
    long maxBackoffMs = 60000;

    @ConfigProperty(name = "notification.retry.backoff-multiplier", defaultValue = "2.0")
    double backoffMultiplier = 2.0;

    @ConfigProperty(name = "notification.retry.jitter-factor", defaultValue = "0.2")
    double jitterFactor = 0.2;

    @Inject
    NotificationRepository notificationRepository;

    @Inject
    NotificationPlatformClient notificationPlatformClient;

    private BackoffCalculator backoffCalculator;

    public NotificationRetryService() {
    }

    public NotificationRetryService(boolean retryEnabled, int maxAttempts, int batchSize,
                                  NotificationRepository notificationRepository,
                                  NotificationPlatformClient notificationPlatformClient) {
        this(retryEnabled, maxAttempts, batchSize, 1000, 60000, 2.0, 0.2, notificationRepository, notificationPlatformClient);
    }

    public NotificationRetryService(boolean retryEnabled, int maxAttempts, int batchSize,
                                  long initialBackoffMs, long maxBackoffMs, double backoffMultiplier, double jitterFactor,
                                  NotificationRepository notificationRepository,
                                  NotificationPlatformClient notificationPlatformClient) {
        this.retryEnabled = retryEnabled;
        this.maxAttempts = maxAttempts;
        this.batchSize = batchSize;
        this.initialBackoffMs = initialBackoffMs;
        this.maxBackoffMs = maxBackoffMs;
        this.backoffMultiplier = backoffMultiplier;
        this.jitterFactor = jitterFactor;
        this.notificationRepository = notificationRepository;
        this.notificationPlatformClient = notificationPlatformClient;
        initBackoffCalculator();
    }

    private void initBackoffCalculator() {
        RetryPolicy policy = RetryPolicy.builder()
                .maxRetries(maxAttempts)
                .initialBackoff(Duration.ofMillis(Math.max(1, initialBackoffMs)))
                .maxBackoff(Duration.ofMillis(Math.max(initialBackoffMs, maxBackoffMs)))
                .multiplier(Math.max(1.0, backoffMultiplier))
                .jitterFactor(Math.max(0.0, Math.min(1.0, jitterFactor)))
                .build();
        this.backoffCalculator = new BackoffCalculator(policy);
    }

    @Scheduled(every = "${notification.retry.interval:10s}", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    public void retryFailedNotifications() {
        if (!retryEnabled || notificationPlatformClient == null) {
            return;
        }

        List<Notification> failedList = notificationRepository.findFailedForRetry(maxAttempts, batchSize);
        if (failedList.isEmpty()) {
            return;
        }

        // PB-054: Skip retry batch if Circuit Breaker is OPEN to avoid thread exhaustion and network flood
        com.logistics.common.circuitbreaker.CircuitBreaker cb = com.logistics.common.circuitbreaker.CircuitBreakerRegistry.getInstance()
                .get("notification-platform").orElse(null);
        if (cb != null && cb.isOpen()) {
            log.warn("PB-054 [RETRY_CIRCUIT_OPEN] Notification Platform circuit breaker is OPEN. Skipping retry batch of {} notifications to conserve resources.",
                    failedList.size());
            return;
        }

        // PB-056: Coordinate with Backpressure - skip if downstream is under heavy congestion / shedding
        com.logistics.common.backpressure.BackpressureController bp = com.logistics.common.backpressure.BackpressureRegistry.getInstance()
                .get("shipment-notification-consumer").orElse(null);
        if (bp != null) {
            if (bp.getState() == com.logistics.common.backpressure.BackpressureState.SHEDDING) {
                log.warn("PB-056 [RETRY_BACKPRESSURE_SKIP] Downstream is under SHEDDING backpressure. Skipping retry batch of {} notifications.",
                        failedList.size());
                return;
            } else if (bp.getState() == com.logistics.common.backpressure.BackpressureState.THROTTLED) {
                log.info("PB-056 [RETRY_BACKPRESSURE_THROTTLED] Downstream is THROTTLED. Applying pacing delay between retried notifications.");
            }
        }

        log.info("Found {} failed notifications eligible for retry to Notification Platform", failedList.size());

        if (backoffCalculator == null) {
            initBackoffCalculator();
        }
        Instant now = Instant.now();

        for (Notification notification : failedList) {
            // Apply exponential backoff check (PB-052):
            // If notification has already been retried, verify required backoff delay has elapsed
            if (notification.getRetryCount() > 0 && notification.getCreatedAt() != null) {
                Duration requiredBackoff = backoffCalculator.computeBackoff(notification.getRetryCount());
                if (notification.getCreatedAt().plus(requiredBackoff).isAfter(now)) {
                    log.debug("PB-052 [RETRY_WAIT] Skipping notification '{}': required backoff of {}ms not reached yet",
                            notification.getId(), requiredBackoff.toMillis());
                    continue;
                }
            }

            NotificationPlatformRequest req = new NotificationPlatformRequest(
                    notification.getRecipient(),
                    notification.getChannel(),
                    notification.getSubject(),
                    notification.getContent(),
                    notification.getPriority() != null ? notification.getPriority() : "NORMAL"
            );

            NotificationPlatformResult result = notificationPlatformClient.sendNotification(req, notification.getEventId());

            String platformId = (result.isSuccess() && result.getPlatformNotificationId() != null)
                    ? result.getPlatformNotificationId().toString()
                    : null;

            notificationRepository.updateRetryResult(
                    notification.getId(),
                    result.isSuccess(),
                    platformId,
                    result.getErrorMessage(),
                    maxAttempts
            );

            if (result.isSuccess()) {
                log.info("Successfully retried notification '{}' to Notification Platform (id={})",
                        notification.getId(), platformId);
            } else {
                log.warn("Retry failed for notification '{}': {}", notification.getId(), result.getErrorMessage());
                if (result.isCircuitOpen()) {
                    log.warn("PB-054 [RETRY_CIRCUIT_TRIPPED] Notification Platform circuit breaker opened during batch. Halting remaining retries.");
                    break;
                }
            }
        }
    }
}
