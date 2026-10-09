package com.logistics.order;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.logistics.common.event.DomainEvent;
import com.logistics.common.event.KafkaTopics;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class KafkaProducerConsumerIntegrationTest {

    @Inject
    ObjectMapper objectMapper;

    @Test
    void testDomainEventContractAndJsonSerialization() throws JsonProcessingException {
        DomainEvent<String> event = DomainEvent.of("SmokeTestCreated", "SMOKE-001", "Foundation message test");

        assertNotNull(event.getEventId());
        assertNotNull(event.getTimestamp());
        assertEquals("SmokeTestCreated", event.getEventType());
        assertEquals("SMOKE-001", event.getAggregateId());
        assertEquals("Foundation message test", event.getPayload());

        // Verify JSON serialization and deserialization
        String json = objectMapper.writeValueAsString(event);
        assertNotNull(json);
        assertTrue(json.contains("SmokeTestCreated"));
        assertTrue(json.contains("SMOKE-001"));

        DomainEvent<?> deserialized = objectMapper.readValue(json, DomainEvent.class);
        assertEquals(event.getEventId(), deserialized.getEventId());
        assertEquals(event.getEventType(), deserialized.getEventType());
        assertEquals(event.getAggregateId(), deserialized.getAggregateId());
        assertEquals(event.getPayload(), deserialized.getPayload());
        assertEquals(KafkaTopics.ORDER_EVENTS, "logistics.order.events");
    }
}
