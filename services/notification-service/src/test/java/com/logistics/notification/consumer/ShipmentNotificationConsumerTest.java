package com.logistics.notification.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.logistics.notification.client.NotificationPlatformClient;
import com.logistics.notification.client.NotificationPlatformResult;
import com.logistics.notification.entity.Notification;
import com.logistics.notification.repository.InboxEventRepository;
import com.logistics.notification.repository.NotificationRepository;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@QuarkusTest
class ShipmentNotificationConsumerTest {

    @Inject
    NotificationRepository notificationRepository;

    @Inject
    InboxEventRepository inboxEventRepository;

    @Inject
    ObjectMapper objectMapper;

    @InjectMock
    NotificationPlatformClient notificationPlatformClient;

    @Inject
    ShipmentNotificationConsumer consumer;

    @BeforeEach
    @Transactional
    void setUp() {
        notificationRepository.deleteAll();
        inboxEventRepository.deleteAll();
    }

    @Test
    @DisplayName("PB-051: ORDER_DELIVERED event maps contract, calls Notification Platform, and saves platform ID")
    void testProcessOrderDelivered_success() throws Exception {
        UUID platformId = UUID.randomUUID();
        when(notificationPlatformClient.sendNotification(any(), any()))
                .thenReturn(NotificationPlatformResult.success(platformId, "QUEUED", 202, 1));

        String eventId = "evt-del-" + UUID.randomUUID();
        String orderId = "ord-del-1001";
        String json = "{\n" +
                "  \"eventId\": \"" + eventId + "\",\n" +
                "  \"eventType\": \"ORDER_DELIVERED\",\n" +
                "  \"aggregateId\": \"" + orderId + "\",\n" +
                "  \"timestamp\": \"2026-10-22T10:00:00Z\",\n" +
                "  \"payload\": {\n" +
                "    \"assignmentId\": \"asg-101\",\n" +
                "    \"orderId\": \"" + orderId + "\",\n" +
                "    \"orderNumber\": \"ORD-20261022-DEL01\",\n" +
                "    \"customerId\": \"cust-999\",\n" +
                "    \"recipientName\": \"Nguyen Van Nam\",\n" +
                "    \"recipientPhone\": \"0988776655\",\n" +
                "    \"driverId\": \"drv-quick-01\",\n" +
                "    \"status\": \"DELIVERED\",\n" +
                "    \"deliveredAt\": \"2026-10-22T10:00:00Z\",\n" +
                "    \"deliveryAddress\": \"123 Le Loi, Q1, HCMC\",\n" +
                "    \"totalAmount\": 180000.00\n" +
                "  }\n" +
                "}";

        Notification notification = consumer.processEvent(json);

        assertThat(notification).isNotNull();
        assertThat(notification.getEventId()).isEqualTo(eventId);
        assertThat(notification.getEventType()).isEqualTo("ORDER_DELIVERED");
        assertThat(notification.getOrderId()).isEqualTo(orderId);
        assertThat(notification.getRecipient()).isEqualTo("0988776655");
        assertThat(notification.getChannel()).isEqualTo("SMS");
        assertThat(notification.getPriority()).isEqualTo("NORMAL");
        assertThat(notification.getSubject()).contains("ORD-20261022-DEL01");
        assertThat(notification.getContent()).contains("Nguyen Van Nam");
        assertThat(notification.getContent()).contains("123 Le Loi");
        assertThat(notification.getStatus()).isEqualTo("SENT");
        assertThat(notification.getPlatformNotificationId()).isEqualTo(platformId.toString());

        // Verify inbox event was recorded
        assertThat(inboxEventRepository.existsByEventId(eventId)).isTrue();
        verify(notificationPlatformClient, times(1)).sendNotification(any(), eq(eventId));
    }

    @Test
    @DisplayName("PB-051: ORDER_FAILED event creates alert notification with failure reason")
    void testProcessOrderFailed_success() throws Exception {
        UUID platformId = UUID.randomUUID();
        when(notificationPlatformClient.sendNotification(any(), any()))
                .thenReturn(NotificationPlatformResult.success(platformId, "QUEUED", 202, 1));

        String eventId = "evt-fail-" + UUID.randomUUID();
        String orderId = "ord-fail-2002";
        String json = "{\n" +
                "  \"eventId\": \"" + eventId + "\",\n" +
                "  \"eventType\": \"ORDER_FAILED\",\n" +
                "  \"aggregateId\": \"" + orderId + "\",\n" +
                "  \"timestamp\": \"2026-10-22T10:15:00Z\",\n" +
                "  \"payload\": {\n" +
                "    \"assignmentId\": \"asg-202\",\n" +
                "    \"orderId\": \"" + orderId + "\",\n" +
                "    \"orderNumber\": \"ORD-20261022-FAIL02\",\n" +
                "    \"customerId\": \"cust-888\",\n" +
                "    \"recipientName\": \"Tran Thi Mai\",\n" +
                "    \"recipientPhone\": \"0911223344\",\n" +
                "    \"driverId\": \"drv-alpha-02\",\n" +
                "    \"status\": \"FAILED\",\n" +
                "    \"failedAt\": \"2026-10-22T10:15:00Z\",\n" +
                "    \"failureReason\": \"Khách hàng không nghe máy sau 3 lần gọi\",\n" +
                "    \"deliveryAddress\": \"456 Dien Bien Phu, Binh Thanh, HCMC\"\n" +
                "  }\n" +
                "}";

        Notification notification = consumer.processEvent(json);

        assertThat(notification).isNotNull();
        assertThat(notification.getEventId()).isEqualTo(eventId);
        assertThat(notification.getEventType()).isEqualTo("ORDER_FAILED");
        assertThat(notification.getPriority()).isEqualTo("HIGH");
        assertThat(notification.getSubject()).contains("ORD-20261022-FAIL02");
        assertThat(notification.getContent()).contains("Khách hàng không nghe máy sau 3 lần gọi");
        assertThat(notification.getPlatformNotificationId()).isEqualTo(platformId.toString());

        assertThat(inboxEventRepository.existsByEventId(eventId)).isTrue();
    }

    @Test
    @DisplayName("PB-051: DRIVER_ASSIGNED event creates driver assignment notification")
    void testProcessDriverAssigned_success() throws Exception {
        UUID platformId = UUID.randomUUID();
        when(notificationPlatformClient.sendNotification(any(), any()))
                .thenReturn(NotificationPlatformResult.success(platformId, "QUEUED", 202, 1));

        String eventId = "evt-asg-" + UUID.randomUUID();
        String orderId = "ord-asg-3003";
        String json = "{\n" +
                "  \"eventId\": \"" + eventId + "\",\n" +
                "  \"eventType\": \"DRIVER_ASSIGNED\",\n" +
                "  \"aggregateId\": \"" + orderId + "\",\n" +
                "  \"timestamp\": \"2026-10-22T09:30:00Z\",\n" +
                "  \"payload\": {\n" +
                "    \"orderId\": \"" + orderId + "\",\n" +
                "    \"orderNumber\": \"ORD-20261022-ASG03\",\n" +
                "    \"customerId\": \"cust-777\",\n" +
                "    \"recipientName\": \"Pham Hoang\",\n" +
                "    \"recipientPhone\": \"0933445566\",\n" +
                "    \"driverId\": \"drv-speedy-05\",\n" +
                "    \"status\": \"ASSIGNED\"\n" +
                "  }\n" +
                "}";

        Notification notification = consumer.processEvent(json);

        assertThat(notification).isNotNull();
        assertThat(notification.getEventType()).isEqualTo("DRIVER_ASSIGNED");
        assertThat(notification.getSubject()).contains("ORD-20261022-ASG03");
        assertThat(notification.getContent()).contains("drv-speedy-05");
    }

    @Test
    @DisplayName("PB-051: Consumer handles duplicate events IDEMPOTENTLY without re-calling Notification Platform")
    void testIdempotentConsumer_duplicateEventIgnored() throws Exception {
        UUID platformId = UUID.randomUUID();
        when(notificationPlatformClient.sendNotification(any(), any()))
                .thenReturn(NotificationPlatformResult.success(platformId, "QUEUED", 202, 1));

        String eventId = "evt-dup-" + UUID.randomUUID();
        String orderId = "ord-dup-4004";
        String json = "{\n" +
                "  \"eventId\": \"" + eventId + "\",\n" +
                "  \"eventType\": \"ORDER_DELIVERED\",\n" +
                "  \"aggregateId\": \"" + orderId + "\",\n" +
                "  \"payload\": {\n" +
                "    \"orderId\": \"" + orderId + "\",\n" +
                "    \"orderNumber\": \"ORD-DUP-01\",\n" +
                "    \"customerId\": \"cust-dup\",\n" +
                "    \"recipientName\": \"Le Van Dup\",\n" +
                "    \"recipientPhone\": \"0977889900\",\n" +
                "    \"status\": \"DELIVERED\"\n" +
                "  }\n" +
                "}";

        // First delivery: processes and calls Notification Platform
        Notification firstResult = consumer.processEvent(json);
        assertThat(firstResult).isNotNull();

        // Second delivery with identical eventId: must be recognized as duplicate and ignored
        Notification secondResult = consumer.processEvent(json);
        assertThat(secondResult).isNull();

        // Verify Notification Platform was called only ONCE
        verify(notificationPlatformClient, times(1)).sendNotification(any(), eq(eventId));

        // Ensure database has exactly ONE notification record for this eventId
        List<Notification> notifications = notificationRepository.findByEventId(eventId);
        assertThat(notifications).hasSize(1);
    }

    @Test
    @DisplayName("PB-051: When Notification Platform is unavailable, records FAILED notification without crashing")
    void testNotificationPlatformFailure_recordsFailedNotification() throws Exception {
        when(notificationPlatformClient.sendNotification(any(), any()))
                .thenReturn(NotificationPlatformResult.failure(503, "Service Unavailable (Emergency Cutoff)", 60L, 2));

        String eventId = "evt-fail-platform-" + UUID.randomUUID();
        String orderId = "ord-fail-5005";
        String json = "{\n" +
                "  \"eventId\": \"" + eventId + "\",\n" +
                "  \"eventType\": \"ORDER_DELIVERED\",\n" +
                "  \"aggregateId\": \"" + orderId + "\",\n" +
                "  \"payload\": {\n" +
                "    \"orderId\": \"" + orderId + "\",\n" +
                "    \"orderNumber\": \"ORD-5005\",\n" +
                "    \"recipientName\": \"Nguyen Van Loi\",\n" +
                "    \"recipientPhone\": \"0912345678\",\n" +
                "    \"status\": \"DELIVERED\"\n" +
                "  }\n" +
                "}";

        Notification result = consumer.processEvent(json);

        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo("FAILED");
        assertThat(result.getErrorMessage()).contains("Service Unavailable");
        assertThat(result.getRetryCount()).isEqualTo(2);

        // Record still preserved in database for later retry
        List<Notification> saved = notificationRepository.findByEventId(eventId);
        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).getStatus()).isEqualTo("FAILED");
    }
}
