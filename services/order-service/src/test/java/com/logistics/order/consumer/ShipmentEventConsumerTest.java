package com.logistics.order.consumer;

import com.logistics.common.model.OrderStatus;
import com.logistics.order.entity.Order;
import com.logistics.order.repository.OrderRepository;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
class ShipmentEventConsumerTest {

    @Inject
    ShipmentEventConsumer consumer;

    @Inject
    OrderRepository orderRepository;

    private String createOrder(OrderStatus status) {
        String orderId = UUID.randomUUID().toString(); // orders.id is VARCHAR(36)
        QuarkusTransaction.requiringNew().run(() -> orderRepository.persist(new Order(
                orderId, "ORD-" + orderId, "cust-sync-01", status,
                "Recipient", "0909090909", "123 Street", new BigDecimal("100000"), "notes")));
        return orderId;
    }

    private OrderStatus statusOf(String orderId) {
        return QuarkusTransaction.requiringNew().call(() ->
                orderRepository.findByIdOrOrderNumber(orderId).orElseThrow().getStatus());
    }

    private static String event(String eventType, String orderId) {
        return "{\"eventId\":\"" + UUID.randomUUID() + "\",\"eventType\":\"" + eventType + "\","
                + "\"aggregateId\":\"" + orderId + "\",\"payload\":{\"orderId\":\"" + orderId + "\"}}";
    }

    @Test
    @DisplayName("Full delivery flow moves order PENDING -> ASSIGNED -> IN_TRANSIT -> DELIVERED")
    void fullDeliveryFlow_syncsOrderStatus() {
        String orderId = createOrder(OrderStatus.PENDING);

        assertThat(consumer.process(event("DRIVER_ASSIGNED", orderId))).isTrue();
        assertThat(statusOf(orderId)).isEqualTo(OrderStatus.ASSIGNED);

        assertThat(consumer.process(event("ORDER_PICKED_UP", orderId))).isFalse();
        assertThat(statusOf(orderId)).isEqualTo(OrderStatus.ASSIGNED);

        assertThat(consumer.process(event("ORDER_IN_TRANSIT", orderId))).isTrue();
        assertThat(statusOf(orderId)).isEqualTo(OrderStatus.IN_TRANSIT);

        assertThat(consumer.process(event("ORDER_DELIVERED", orderId))).isTrue();
        assertThat(statusOf(orderId)).isEqualTo(OrderStatus.DELIVERED);
    }

    @Test
    @DisplayName("Duplicate or out-of-order events never move the order backwards")
    void staleEvents_areIgnored() {
        String orderId = createOrder(OrderStatus.PENDING);
        consumer.process(event("ORDER_IN_TRANSIT", orderId));

        assertThat(consumer.process(event("DRIVER_ASSIGNED", orderId))).isFalse();
        assertThat(consumer.process(event("ORDER_IN_TRANSIT", orderId))).isFalse();
        assertThat(statusOf(orderId)).isEqualTo(OrderStatus.IN_TRANSIT);
    }

    @Test
    @DisplayName("Failed delivery re-opens dispatch and a reassignment moves it back to ASSIGNED")
    void failedThenReassigned() {
        String orderId = createOrder(OrderStatus.IN_TRANSIT);

        assertThat(consumer.process(event("ORDER_FAILED", orderId))).isTrue();
        assertThat(statusOf(orderId)).isEqualTo(OrderStatus.CONFIRMED);

        assertThat(consumer.process(event("DRIVER_REASSIGNED", orderId))).isTrue();
        assertThat(statusOf(orderId)).isEqualTo(OrderStatus.ASSIGNED);
    }

    @Test
    @DisplayName("Terminal orders, unknown orders and malformed messages are skipped without error")
    void ignoredEvents() {
        String cancelled = createOrder(OrderStatus.CANCELLED);
        assertThat(consumer.process(event("DRIVER_ASSIGNED", cancelled))).isFalse();
        assertThat(statusOf(cancelled)).isEqualTo(OrderStatus.CANCELLED);

        assertThat(consumer.process(event("DRIVER_ASSIGNED", "ord-does-not-exist"))).isFalse();
        assertThat(consumer.process("not json")).isFalse();
        assertThat(consumer.process(null)).isFalse();
    }
}
