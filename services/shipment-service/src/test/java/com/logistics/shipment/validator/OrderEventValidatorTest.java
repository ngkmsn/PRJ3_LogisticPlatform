package com.logistics.shipment.validator;

import com.logistics.common.model.OrderStatus;
import com.logistics.shipment.dto.OrderCreatedPayload;
import com.logistics.shipment.exception.InvalidEventPayloadException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderEventValidatorTest {

    private OrderEventValidator validator;

    @BeforeEach
    void setUp() {
        validator = new OrderEventValidator();
    }

    private OrderCreatedPayload createValidPayload() {
        return new OrderCreatedPayload(
                "ord-uuid-12345",
                "ORD-20261016-0001",
                "cust-uuid-67890",
                "Nguyễn Văn A",
                "0901234567",
                "123 Nguyễn Huệ, Quận 1, TP.HCM",
                OrderStatus.PENDING,
                BigDecimal.valueOf(150000.00),
                "Giao giờ hành chính",
                Instant.now()
        );
    }

    @Test
    @DisplayName("Valid payload passes validation without exception")
    void validate_validPayload_success() {
        OrderCreatedPayload payload = createValidPayload();
        assertThatCode(() -> validator.validate(payload))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Null payload throws InvalidEventPayloadException")
    void validate_nullPayload_throwsException() {
        assertThatThrownBy(() -> validator.validate(null))
                .isInstanceOf(InvalidEventPayloadException.class)
                .hasMessageContaining("OrderCreated payload is null");
    }

    @Test
    @DisplayName("Missing orderId throws InvalidEventPayloadException")
    void validate_missingOrderId_throwsException() {
        OrderCreatedPayload payload = createValidPayload();
        payload.setOrderId(null);
        assertThatThrownBy(() -> validator.validate(payload))
                .isInstanceOf(InvalidEventPayloadException.class)
                .hasMessageContaining("missing mandatory 'orderId'");

        payload.setOrderId("   ");
        assertThatThrownBy(() -> validator.validate(payload))
                .isInstanceOf(InvalidEventPayloadException.class)
                .hasMessageContaining("missing mandatory 'orderId'");
    }

    @Test
    @DisplayName("Missing customerId throws InvalidEventPayloadException")
    void validate_missingCustomerId_throwsException() {
        OrderCreatedPayload payload = createValidPayload();
        payload.setCustomerId(null);
        assertThatThrownBy(() -> validator.validate(payload))
                .isInstanceOf(InvalidEventPayloadException.class)
                .hasMessageContaining("missing mandatory 'customerId'");

        payload.setCustomerId("");
        assertThatThrownBy(() -> validator.validate(payload))
                .isInstanceOf(InvalidEventPayloadException.class)
                .hasMessageContaining("missing mandatory 'customerId'");
    }

    @Test
    @DisplayName("Missing deliveryAddress throws InvalidEventPayloadException")
    void validate_missingDeliveryAddress_throwsException() {
        OrderCreatedPayload payload = createValidPayload();
        payload.setDeliveryAddress(null);
        assertThatThrownBy(() -> validator.validate(payload))
                .isInstanceOf(InvalidEventPayloadException.class)
                .hasMessageContaining("missing mandatory 'deliveryAddress'");

        payload.setDeliveryAddress("  ");
        assertThatThrownBy(() -> validator.validate(payload))
                .isInstanceOf(InvalidEventPayloadException.class)
                .hasMessageContaining("missing mandatory 'deliveryAddress'");
    }

    @Test
    @DisplayName("Negative totalAmount throws InvalidEventPayloadException")
    void validate_negativeTotalAmount_throwsException() {
        OrderCreatedPayload payload = createValidPayload();
        payload.setTotalAmount(BigDecimal.valueOf(-1.00));
        assertThatThrownBy(() -> validator.validate(payload))
                .isInstanceOf(InvalidEventPayloadException.class)
                .hasMessageContaining("totalAmount cannot be negative");
    }
}
