package com.logistics.order.publisher;

import com.logistics.common.event.KafkaTopics;
import com.logistics.order.entity.OutboxEvent;
import com.logistics.order.entity.OutboxStatus;
import com.logistics.order.repository.OutboxEventRepository;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@QuarkusTest
class OutboxPublisherIntegrationTest {

    @Inject
    OutboxEventRepository outboxEventRepository;

    @Inject
    OutboxPublisherService outboxPublisherService;

    @InjectMock
    KafkaOrderEventPublisher kafkaOrderEventPublisher;

    @BeforeEach
    @Transactional
    void cleanUp() {
        reset(kafkaOrderEventPublisher);
        outboxEventRepository.deleteAll();
    }

    private OutboxEvent createAndPersistEvent(String orderId, OutboxStatus status, int retryCount, Instant occurredAt) {
        OutboxEvent event = new OutboxEvent(
                UUID.randomUUID().toString(),
                "ORDER",
                orderId,
                "ORDER_CREATED",
                "{\"orderId\":\"" + orderId + "\",\"recipientName\":\"Nguyễn Văn A\",\"totalAmount\":250000.00}",
                status,
                retryCount,
                null,
                occurredAt,
                null,
                Instant.now()
        );
        persistInNewTx(event);
        return event;
    }

    @Transactional
    void persistInNewTx(OutboxEvent event) {
        outboxEventRepository.persist(event);
    }

    @Transactional
    OutboxEvent getFreshEvent(String eventId) {
        outboxEventRepository.getEntityManager().clear();
        return outboxEventRepository.findById(eventId);
    }

    @Test
    @DisplayName("PB-023: Outbox Publisher successfully publishes pending event to Kafka and marks as PUBLISHED in DB")
    void publishPendingEvents_success_persistsPublishedStatusInDb() throws Exception {
        String orderId = "ord-pub-success-" + UUID.randomUUID();
        OutboxEvent event = createAndPersistEvent(orderId, OutboxStatus.PENDING, 0, Instant.now());

        doNothing().when(kafkaOrderEventPublisher).publish(anyString(), anyString(), anyString());

        int count = outboxPublisherService.publishPendingEvents();

        assertThat(count).isEqualTo(1);

        // Verify Kafka publish call
        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(kafkaOrderEventPublisher).publish(eq(KafkaTopics.ORDER_EVENTS), eq(orderId), payloadCaptor.capture());

        assertThat(payloadCaptor.getValue()).contains("ORDER_CREATED");
        assertThat(payloadCaptor.getValue()).contains(orderId);
        assertThat(payloadCaptor.getValue()).contains("Nguyễn Văn A");

        // Verify Database state
        OutboxEvent updated = getFreshEvent(event.getId());
        assertThat(updated).isNotNull();
        assertThat(updated.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
        assertThat(updated.getPublishedAt()).isNotNull();
        assertThat(updated.getErrorMessage()).isNull();
        assertThat(updated.getRetryCount()).isZero();
    }

    @Test
    @DisplayName("PB-023: When Kafka publish fails, event remains PENDING with incremented retry count and error message")
    void publishPendingEvents_kafkaFails_eventRetainedForRetry() throws Exception {
        String orderId = "ord-pub-fail-" + UUID.randomUUID();
        OutboxEvent event = createAndPersistEvent(orderId, OutboxStatus.PENDING, 0, Instant.now());

        doThrow(new RuntimeException("Simulated Kafka Broker Unreachable"))
                .when(kafkaOrderEventPublisher).publish(anyString(), anyString(), anyString());

        int count = outboxPublisherService.publishPendingEvents();

        assertThat(count).isZero();

        // Verify Database state: NOT PUBLISHED, retained for retry
        OutboxEvent updated = getFreshEvent(event.getId());
        assertThat(updated).isNotNull();
        assertThat(updated.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(updated.getPublishedAt()).isNull();
        assertThat(updated.getRetryCount()).isEqualTo(1);
        assertThat(updated.getErrorMessage()).contains("Simulated Kafka Broker Unreachable");
    }

    @Test
    @DisplayName("PB-023: Event transitions to FAILED when maximum retry attempts (5) are reached")
    void publishPendingEvents_maxRetriesExceeded_transitionsToFailed() throws Exception {
        String orderId = "ord-pub-maxretry-" + UUID.randomUUID();
        // Event already retried 4 times
        OutboxEvent event = createAndPersistEvent(orderId, OutboxStatus.PENDING, 4, Instant.now());

        doThrow(new RuntimeException("Continuous Kafka Timeout Error"))
                .when(kafkaOrderEventPublisher).publish(anyString(), anyString(), anyString());

        int count = outboxPublisherService.publishPendingEvents();

        assertThat(count).isZero();

        OutboxEvent updated = getFreshEvent(event.getId());
        assertThat(updated).isNotNull();
        assertThat(updated.getStatus()).isEqualTo(OutboxStatus.FAILED);
        assertThat(updated.getRetryCount()).isEqualTo(5);
        assertThat(updated.getPublishedAt()).isNull();
        assertThat(updated.getErrorMessage()).contains("Continuous Kafka Timeout Error");
    }

    @Test
    @DisplayName("PB-023: Stale PROCESSING event older than timeout is automatically recovered and published")
    void publishPendingEvents_staleProcessingEvent_recoveredAndPublished() throws Exception {
        String orderId = "ord-pub-stale-" + UUID.randomUUID();
        // Event claimed by a crashed worker 10 minutes ago
        Instant tenMinutesAgo = Instant.now().minus(10, ChronoUnit.MINUTES);
        OutboxEvent event = createAndPersistEvent(orderId, OutboxStatus.PROCESSING, 0, tenMinutesAgo);

        doNothing().when(kafkaOrderEventPublisher).publish(anyString(), anyString(), anyString());

        int count = outboxPublisherService.publishPendingEvents();

        assertThat(count).isEqualTo(1);

        OutboxEvent updated = getFreshEvent(event.getId());
        assertThat(updated).isNotNull();
        assertThat(updated.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
        assertThat(updated.getPublishedAt()).isNotNull();
    }
}
