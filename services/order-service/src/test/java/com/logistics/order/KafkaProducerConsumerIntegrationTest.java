package com.logistics.order;

import com.logistics.common.event.DomainEvent;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@EnableKafka
@EmbeddedKafka(
        partitions = 1,
        topics = { "test-order-events" },
        brokerProperties = { "listeners=PLAINTEXT://localhost:9099", "port=9099" }
)
@DirtiesContext
class KafkaProducerConsumerIntegrationTest {

    private static final String TEST_TOPIC = "test-order-events";

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    private TestKafkaConsumer testKafkaConsumer;

    @TestConfiguration
    static class TestConsumerConfiguration {
        @Bean
        public TestKafkaConsumer testKafkaConsumer() {
            return new TestKafkaConsumer();
        }
    }

    static class TestKafkaConsumer {
        private final CountDownLatch latch = new CountDownLatch(1);
        private final AtomicReference<DomainEvent<?>> receivedEvent = new AtomicReference<>();

        @KafkaListener(topics = TEST_TOPIC, groupId = "test-smoke-group")
        public void consume(DomainEvent<String> event) {
            receivedEvent.set(event);
            latch.countDown();
        }

        public boolean awaitMessage(long timeout, TimeUnit unit) throws InterruptedException {
            return latch.await(timeout, unit);
        }

        public DomainEvent<?> getReceivedEvent() {
            return receivedEvent.get();
        }
    }

    @Test
    void testKafkaProducerAndConsumerFlow() throws InterruptedException {
        DomainEvent<String> event = DomainEvent.of("SmokeTestCreated", "SMOKE-001", "Foundation message test");

        kafkaTemplate.send(TEST_TOPIC, event.getAggregateId(), event);

        boolean messageReceived = testKafkaConsumer.awaitMessage(10, TimeUnit.SECONDS);
        assertTrue(messageReceived, "Consumer must receive the event within timeout");

        DomainEvent<?> consumed = testKafkaConsumer.getReceivedEvent();
        assertNotNull(consumed, "Consumed event must not be null");
        assertEquals("SmokeTestCreated", consumed.getEventType());
        assertEquals("SMOKE-001", consumed.getAggregateId());
        assertEquals("Foundation message test", consumed.getPayload());
    }
}
