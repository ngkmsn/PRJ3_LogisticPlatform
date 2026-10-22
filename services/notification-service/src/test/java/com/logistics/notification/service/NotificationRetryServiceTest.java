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
        notification.setCreatedAt(Instant.now());
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
        notification.setCreatedAt(Instant.now());
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
}
