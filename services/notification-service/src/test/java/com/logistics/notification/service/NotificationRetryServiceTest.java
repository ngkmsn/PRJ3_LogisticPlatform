package com.logistics.notification.service;

import com.logistics.notification.client.NotificationPlatformClient;
import com.logistics.notification.client.NotificationPlatformResult;
import com.logistics.notification.entity.Notification;
import com.logistics.notification.repository.NotificationRepository;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.time.Duration;

@QuarkusTest
class NotificationRetryServiceTest {

    @Inject
    NotificationRepository notificationRepository;

    private NotificationPlatformClient mockClient;
    private NotificationRetryService retryService;

    @BeforeEach
    @Transactional
    void setUp() {
        notificationRepository.deleteAll();
        mockClient = mock(NotificationPlatformClient.class);
        retryService = new NotificationRetryService(true, 5, 10, notificationRepository, mockClient);
    }

    @Test
    @Transactional
    @DisplayName("PB-051: Retry service resubmits FAILED notification and marks SENT upon success")
    void testRetryFailedNotification_success() {
        Notification notification = new Notification();
        notification.setId(UUID.randomUUID().toString());
        notification.setRecipient("0988776655");
        notification.setChannel("SMS");
        notification.setSubject("Don hang ORD-RETRY");
        notification.setContent("Noi dung don hang");
        notification.setPriority("NORMAL");
        notification.setStatus("FAILED");
        notification.setErrorMessage("503 Service Unavailable");
        notification.setRetryCount(1);
        notification.setEventId("evt-retry-01");
        notification.setCreatedAt(Instant.now().minus(Duration.ofMinutes(10)));
        notificationRepository.persist(notification);

        UUID platformId = UUID.randomUUID();
        when(mockClient.sendNotification(any(), eq("evt-retry-01")))
                .thenReturn(NotificationPlatformResult.success(platformId, "QUEUED", 202, 1));

        retryService.retryFailedNotifications();

        Notification updated = notificationRepository.findById(notification.getId());
        assertThat(updated).isNotNull();
        assertThat(updated.getStatus()).isEqualTo("SENT");
        assertThat(updated.getPlatformNotificationId()).isEqualTo(platformId.toString());
        assertThat(updated.getErrorMessage()).isNull();
        assertThat(updated.getRetryCount()).isEqualTo(2);
    }

    @Test
    @Transactional
    @DisplayName("PB-051: Retry service transitions to PERMANENTLY_FAILED when max attempts exceeded")
    void testRetryFailedNotification_maxAttemptsExceeded() {
        Notification notification = new Notification();
        notification.setId(UUID.randomUUID().toString());
        notification.setRecipient("0988776655");
        notification.setChannel("SMS");
        notification.setSubject("Don hang ORD-EXCEED");
        notification.setContent("Noi dung don hang");
        notification.setPriority("NORMAL");
        notification.setStatus("FAILED");
        notification.setErrorMessage("Connection refused");
        notification.setRetryCount(4); // maxAttempts is 5
        notification.setEventId("evt-retry-02");
        notification.setCreatedAt(Instant.now().minus(Duration.ofMinutes(10)));
        notificationRepository.persist(notification);

        when(mockClient.sendNotification(any(), eq("evt-retry-02")))
                .thenReturn(NotificationPlatformResult.failure(503, "Still unavailable", null, 1));

        retryService.retryFailedNotifications();

        Notification updated = notificationRepository.findById(notification.getId());
        assertThat(updated).isNotNull();
        assertThat(updated.getStatus()).isEqualTo("PERMANENTLY_FAILED");
        assertThat(updated.getRetryCount()).isEqualTo(5);
        assertThat(updated.getErrorMessage()).contains("Still unavailable");
    }

    @Test
    @Transactional
    @DisplayName("PB-052: Retry service skips notifications whose exponential backoff delay has not elapsed")
    void testRetryFailedNotification_skippedWhenBackoffNotElapsed() {
        Notification notification = new Notification();
        notification.setId(UUID.randomUUID().toString());
        notification.setRecipient("0988776655");
        notification.setChannel("SMS");
        notification.setSubject("Don hang ORD-WAIT");
        notification.setContent("Noi dung don hang");
        notification.setPriority("NORMAL");
        notification.setStatus("FAILED");
        notification.setErrorMessage("503 Service Unavailable");
        notification.setRetryCount(2); // Requires backoff > 0
        notification.setEventId("evt-retry-backoff-skip");
        notification.setCreatedAt(Instant.now()); // Just created now -> backoff window still active
        notificationRepository.persist(notification);

        retryService.retryFailedNotifications();

        // Must NOT attempt to call downstream client
        verify(mockClient, never()).sendNotification(any(), eq("evt-retry-backoff-skip"));

        Notification updated = notificationRepository.findById(notification.getId());
        assertThat(updated).isNotNull();
        assertThat(updated.getStatus()).isEqualTo("FAILED");
        assertThat(updated.getRetryCount()).isEqualTo(2); // unchanged
    }

    @Test
    @Transactional
    @DisplayName("PB-054: Retry service skips retry batch when circuit breaker is OPEN to conserve resources")
    void testRetrySkipped_whenCircuitBreakerOpen() {
        Notification notification = new Notification();
        notification.setId(UUID.randomUUID().toString());
        notification.setRecipient("0988776655");
        notification.setChannel("SMS");
        notification.setSubject("Don hang ORD-CB");
        notification.setContent("Noi dung don hang");
        notification.setPriority("NORMAL");
        notification.setStatus("FAILED");
        notification.setErrorMessage("503 Service Unavailable");
        notification.setRetryCount(1);
        notification.setEventId("evt-retry-cb");
        notification.setCreatedAt(Instant.now().minus(Duration.ofMinutes(10)));
        notificationRepository.persist(notification);

        // Trip notification-platform circuit breaker
        com.logistics.common.circuitbreaker.CircuitBreaker cb = new com.logistics.common.circuitbreaker.CircuitBreaker(
                "notification-platform",
                com.logistics.common.circuitbreaker.CircuitBreakerConfig.builder()
                        .failureThreshold(1)
                        .openDuration(Duration.ofMinutes(5))
                        .build()
        );
        cb.recordFailure(new java.io.IOException("Downstream outage"));
        assertThat(cb.isOpen()).isTrue();
        com.logistics.common.circuitbreaker.CircuitBreakerRegistry.getInstance().register("notification-platform", cb);

        try {
            retryService.retryFailedNotifications();

            // Client must NEVER be called while circuit is OPEN
            verify(mockClient, never()).sendNotification(any(), eq("evt-retry-cb"));

            // Notification must NOT be lost - stays FAILED
            Notification notif = notificationRepository.findById(notification.getId());
            assertThat(notif).isNotNull();
            assertThat(notif.getStatus()).isEqualTo("FAILED");

        } finally {
            com.logistics.common.circuitbreaker.CircuitBreakerRegistry.getInstance().clear();
        }
    }

    @Test
    @Transactional
    @DisplayName("PB-056: Retry service skips retry batch when downstream backpressure is in SHEDDING state")
    void testRetrySkipped_whenDownstreamBackpressureShedding() {
        Notification notification = new Notification();
        notification.setId(UUID.randomUUID().toString());
        notification.setRecipient("0988776655");
        notification.setChannel("SMS");
        notification.setSubject("Don hang ORD-BP");
        notification.setContent("Noi dung don hang");
        notification.setPriority("NORMAL");
        notification.setStatus("FAILED");
        notification.setErrorMessage("503 Downstream Overloaded");
        notification.setRetryCount(1);
        notification.setEventId("evt-retry-bp");
        notification.setCreatedAt(Instant.now().minus(Duration.ofMinutes(10)));
        notificationRepository.persist(notification);

        // Saturate shipment-notification-consumer backpressure controller
        com.logistics.common.backpressure.BackpressureConfig bpConfig = com.logistics.common.backpressure.BackpressureConfig.builder()
                .name("shipment-notification-consumer")
                .maxConcurrency(1)
                .build();
        com.logistics.common.backpressure.BackpressureController bp = new com.logistics.common.backpressure.BackpressureController(bpConfig);
        assertThat(bp.tryAcquire()).isTrue();
        assertThat(bp.tryAcquire()).isFalse(); // Trigger SHEDDING state!
        assertThat(bp.getState()).isEqualTo(com.logistics.common.backpressure.BackpressureState.SHEDDING);
        com.logistics.common.backpressure.BackpressureRegistry.getInstance().register("shipment-notification-consumer", bp);

        try {
            retryService.retryFailedNotifications();

            // Client must not be called while downstream is shedding
            verify(mockClient, never()).sendNotification(any(), eq("evt-retry-bp"));

            // Record preserved
            Notification notif = notificationRepository.findById(notification.getId());
            assertThat(notif).isNotNull();
            assertThat(notif.getStatus()).isEqualTo("FAILED");
        } finally {
            com.logistics.common.backpressure.BackpressureRegistry.getInstance().clear();
        }
    }
}
