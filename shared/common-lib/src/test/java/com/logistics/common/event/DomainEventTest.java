package com.logistics.common.event;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class DomainEventTest {

    @Test
    void testDomainEventCreation() {
        DomainEvent<String> event = DomainEvent.of("OrderCreated", "ORD-12345", "Payload data");

        assertNotNull(event.getEventId());
        assertNotNull(event.getTimestamp());
        assertEquals("OrderCreated", event.getEventType());
        assertEquals("ORD-12345", event.getAggregateId());
        assertEquals("Payload data", event.getPayload());
    }
}
