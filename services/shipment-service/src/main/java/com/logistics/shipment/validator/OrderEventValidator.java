package com.logistics.shipment.validator;

import com.logistics.shipment.dto.OrderCreatedPayload;
import com.logistics.shipment.exception.InvalidEventPayloadException;
import jakarta.enterprise.context.ApplicationScoped;

import java.math.BigDecimal;

@ApplicationScoped
public class OrderEventValidator {

    /**
     * Validates that the OrderCreatedPayload conforms to the minimum required specification
     * for assignment processing.
     *
     * @param payload the payload to validate
     * @throws InvalidEventPayloadException if validation fails
     */
    public void validate(OrderCreatedPayload payload) {
        if (payload == null) {
            throw new InvalidEventPayloadException("OrderCreated payload is null");
        }
        if (payload.getOrderId() == null || payload.getOrderId().trim().isEmpty()) {
            throw new InvalidEventPayloadException("OrderCreated payload missing mandatory 'orderId'");
        }
        if (payload.getCustomerId() == null || payload.getCustomerId().trim().isEmpty()) {
            throw new InvalidEventPayloadException("OrderCreated payload missing mandatory 'customerId'");
        }
        if (payload.getDeliveryAddress() == null || payload.getDeliveryAddress().trim().isEmpty()) {
            throw new InvalidEventPayloadException("OrderCreated payload missing mandatory 'deliveryAddress'");
        }
        if (payload.getTotalAmount() != null && payload.getTotalAmount().compareTo(BigDecimal.ZERO) < 0) {
            throw new InvalidEventPayloadException("OrderCreated totalAmount cannot be negative");
        }
    }
}
