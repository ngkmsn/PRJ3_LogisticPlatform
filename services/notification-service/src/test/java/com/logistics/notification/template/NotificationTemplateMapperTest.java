package com.logistics.notification.template;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.logistics.notification.client.NotificationPlatformRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationTemplateMapperTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
    }

    @Test
    @DisplayName("PB-051: Default PUSH mode routes to customerId userId for FCM real delivery")
    void testOrderDelivered_pushMode() {
        NotificationTemplateMapper mapper = new NotificationTemplateMapper("PUSH");
        ObjectNode payload = objectMapper.createObjectNode()
                .put("orderNumber", "ORD-101")
                .put("customerId", "cust-user-123")
                .put("recipientName", "Nguyen Van A")
                .put("recipientPhone", "0988112233")
                .put("driverId", "drv-01")
                .put("deliveryAddress", "123 Hang Bai, Hanoi");

        NotificationPlatformRequest req = mapper.mapEventToPlatformRequest("ORDER_DELIVERED", "ord-101", payload);

        assertThat(req).isNotNull();
        assertThat(req.getRecipient()).isEqualTo("cust-user-123");
        assertThat(req.getChannel()).isEqualTo("PUSH");
        assertThat(req.getPriority()).isEqualTo("NORMAL");
        assertThat(req.getSubject()).isEqualTo("Đơn hàng ORD-101 đã được giao thành công");
        assertThat(req.getContent()).contains("Nguyen Van A")
                .contains("drv-01")
                .contains("123 Hang Bai, Hanoi");
    }

    @Test
    @DisplayName("PB-051: Configured SMS mode maps to phone number recipient")
    void testOrderFailed_smsMode() {
        NotificationTemplateMapper mapper = new NotificationTemplateMapper("SMS");
        ObjectNode payload = objectMapper.createObjectNode()
                .put("orderNumber", "ORD-102")
                .put("recipientName", "Tran Thi B")
                .put("recipientPhone", "0911223344")
                .put("failureReason", "Dia chi khong tim thay");

        NotificationPlatformRequest req = mapper.mapEventToPlatformRequest("ORDER_FAILED", "ord-102", payload);

        assertThat(req).isNotNull();
        assertThat(req.getRecipient()).isEqualTo("0911223344");
        assertThat(req.getChannel()).isEqualTo("SMS");
        assertThat(req.getPriority()).isEqualTo("HIGH");
        assertThat(req.getSubject()).isEqualTo("Thông báo giao hàng không thành công - Đơn hàng ORD-102");
        assertThat(req.getContent()).contains("Dia chi khong tim thay");
    }

    @Test
    @DisplayName("PB-051: Configured EMAIL mode maps to customerId with EMAIL channel")
    void testEmailMode() {
        NotificationTemplateMapper mapper = new NotificationTemplateMapper("EMAIL");
        ObjectNode payload = objectMapper.createObjectNode()
                .put("orderNumber", "ORD-103")
                .put("customerId", "cust-abc@example.com")
                .put("recipientName", "Le Van C");

        NotificationPlatformRequest req = mapper.mapEventToPlatformRequest("ORDER_DELIVERED", "ord-103", payload);

        assertThat(req).isNotNull();
        assertThat(req.getRecipient()).isEqualTo("cust-abc@example.com");
        assertThat(req.getChannel()).isEqualTo("EMAIL");
    }

    @Test
    @DisplayName("PB-051: ORDER_CANCELLED maps to HIGH priority")
    void testOrderCancelledMapping() {
        NotificationTemplateMapper mapper = new NotificationTemplateMapper("PUSH");
        ObjectNode payload = objectMapper.createObjectNode()
                .put("orderNumber", "ORD-104")
                .put("customerId", "cust-004")
                .put("reason", "Khach hang muon doi san pham");

        NotificationPlatformRequest req = mapper.mapEventToPlatformRequest("ORDER_CANCELLED", "ord-104", payload);

        assertThat(req).isNotNull();
        assertThat(req.getPriority()).isEqualTo("HIGH");
        assertThat(req.getSubject()).isEqualTo("Đơn hàng ORD-104 đã được hủy");
        assertThat(req.getContent()).contains("Khach hang muon doi san pham");
    }

    @Test
    @DisplayName("PB-051: Unrecognized event types return null (no customer spam)")
    void testUnrecognizedEventReturnsNull() {
        NotificationTemplateMapper mapper = new NotificationTemplateMapper("PUSH");
        ObjectNode payload = objectMapper.createObjectNode().put("foo", "bar");
        NotificationPlatformRequest req = mapper.mapEventToPlatformRequest("SOME_INTERNAL_EVENT", "ord-999", payload);
        assertThat(req).isNull();
    }
}
