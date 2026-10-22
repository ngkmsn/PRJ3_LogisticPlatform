package com.logistics.notification.template;

import com.fasterxml.jackson.databind.JsonNode;
import com.logistics.notification.client.NotificationPlatformRequest;
import jakarta.enterprise.context.ApplicationScoped;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
public class NotificationTemplateMapper {

    private static final Logger log = LoggerFactory.getLogger(NotificationTemplateMapper.class);

    public static final String EVENT_TYPE_ORDER_DELIVERED = "ORDER_DELIVERED";
    public static final String EVENT_TYPE_ORDER_FAILED = "ORDER_FAILED";
    public static final String EVENT_TYPE_DRIVER_ASSIGNED = "DRIVER_ASSIGNED";
    public static final String EVENT_TYPE_DRIVER_REASSIGNED = "DRIVER_REASSIGNED";
    public static final String EVENT_TYPE_ORDER_PICKED_UP = "ORDER_PICKED_UP";
    public static final String EVENT_TYPE_ORDER_IN_TRANSIT = "ORDER_IN_TRANSIT";
    public static final String EVENT_TYPE_ORDER_CANCELLED = "ORDER_CANCELLED";

    public NotificationPlatformRequest mapEventToPlatformRequest(String eventType, String aggregateId, JsonNode payload) {
        if (eventType == null || payload == null) {
            return null;
        }

        String orderNumber = payload.hasNonNull("orderNumber") ? payload.get("orderNumber").asText() : aggregateId;
        String customerId = payload.hasNonNull("customerId") ? payload.get("customerId").asText() : "customer";
        String recipientName = payload.hasNonNull("recipientName") ? payload.get("recipientName").asText() : "Quý khách";
        String recipientPhone = payload.hasNonNull("recipientPhone") ? payload.get("recipientPhone").asText() : "";
        String deliveryAddress = payload.hasNonNull("deliveryAddress") ? payload.get("deliveryAddress").asText() : "";
        String driverId = payload.hasNonNull("driverId") ? payload.get("driverId").asText() : "";

        String recipient = (!recipientPhone.isBlank()) ? recipientPhone.trim() : customerId.trim();
        String channel = (!recipientPhone.isBlank()) ? "SMS" : "EMAIL";

        String subject;
        String content;
        String priority;

        switch (eventType) {
            case EVENT_TYPE_ORDER_DELIVERED:
                subject = "Đơn hàng " + orderNumber + " đã được giao thành công";
                content = String.format("Chào %s, đơn hàng %s đã được tài xế %s giao thành công đến địa chỉ %s. Cảm ơn quý khách!",
                        recipientName, orderNumber, driverId, deliveryAddress);
                priority = "NORMAL";
                break;

            case EVENT_TYPE_ORDER_FAILED:
                String failureReason = payload.hasNonNull("failureReason") ? payload.get("failureReason").asText() : "Không xác định";
                subject = "Thông báo giao hàng không thành công - Đơn hàng " + orderNumber;
                content = String.format("Chào %s, đơn hàng %s chưa thể giao thành công do: %s. Bộ phận CSKH sẽ sớm liên hệ hỗ trợ quý khách.",
                        recipientName, orderNumber, failureReason);
                priority = "HIGH";
                break;

            case EVENT_TYPE_DRIVER_ASSIGNED:
                subject = "Tài xế đã nhận giao đơn hàng " + orderNumber;
                content = String.format("Chào %s, tài xế %s đã nhận giao đơn hàng %s của quý khách.",
                        recipientName, driverId, orderNumber);
                priority = "NORMAL";
                break;

            case EVENT_TYPE_ORDER_PICKED_UP:
                subject = "Đơn hàng " + orderNumber + " đã được lấy";
                content = String.format("Chào %s, đơn hàng %s đã được lấy bởi tài xế %s và đang được xử lý.",
                        recipientName, orderNumber, driverId);
                priority = "NORMAL";
                break;

            case EVENT_TYPE_ORDER_IN_TRANSIT:
                subject = "Đơn hàng " + orderNumber + " đang trên đường giao";
                content = String.format("Chào %s, tài xế %s đang trên đường giao đơn hàng %s tới địa chỉ: %s.",
                        recipientName, driverId, orderNumber, deliveryAddress);
                priority = "NORMAL";
                break;

            case EVENT_TYPE_DRIVER_REASSIGNED:
                String newDriverId = payload.hasNonNull("newDriverId") ? payload.get("newDriverId").asText() : driverId;
                subject = "Cập nhật tài xế cho đơn hàng " + orderNumber;
                content = String.format("Chào %s, đơn hàng %s đã được chuyển giao cho tài xế mới: %s.",
                        recipientName, orderNumber, newDriverId);
                priority = "NORMAL";
                break;

            case EVENT_TYPE_ORDER_CANCELLED:
                String cancelReason = payload.hasNonNull("reason") ? payload.get("reason").asText() : "Khách hàng yêu cầu hủy";
                subject = "Đơn hàng " + orderNumber + " đã được hủy";
                content = String.format("Chào %s, đơn hàng %s đã được hủy thành công. Lý do: %s.",
                        recipientName, orderNumber, cancelReason);
                priority = "HIGH";
                break;

            default:
                log.info("Ignoring eventType '{}' as it does not require direct customer notification", eventType);
                return null;
        }

        return new NotificationPlatformRequest(recipient, channel, subject, content, priority);
    }
}
