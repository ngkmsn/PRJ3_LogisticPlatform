package com.logistics.notification.service;

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

    @Inject
    NotificationRepository notificationRepository;

    @Inject
    NotificationPlatformClient notificationPlatformClient;

    public NotificationRetryService() {
    }

    public NotificationRetryService(boolean retryEnabled, int maxAttempts, int batchSize,
                                  NotificationRepository notificationRepository,
                                  NotificationPlatformClient notificationPlatformClient) {
        this.retryEnabled = retryEnabled;
        this.maxAttempts = maxAttempts;
        this.batchSize = batchSize;
        this.notificationRepository = notificationRepository;
        this.notificationPlatformClient = notificationPlatformClient;
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

        log.info("Found {} failed notifications eligible for retry to Notification Platform", failedList.size());

        for (Notification notification : failedList) {
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
            }
        }
    }
}
