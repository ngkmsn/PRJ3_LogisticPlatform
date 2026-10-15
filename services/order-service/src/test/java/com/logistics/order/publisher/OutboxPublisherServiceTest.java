package com.logistics.order.publisher;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.logistics.common.event.KafkaTopics;
import com.logistics.order.entity.OutboxEvent;
import com.logistics.order.entity.OutboxStatus;
import com.logistics.order.repository.OutboxEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxPublisherServiceTest {

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private KafkaOrderEventPublisher kafkaOrderEventPublisher;

    private ObjectMapper objectMapper;
    private OutboxPublisherService publisherService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().findAndRegisterModules();
        publisherService = new OutboxPublisherService(
                outboxEventRepository,
                kafkaOrderEventPublisher,
                objectMapper
        );
    }

    @Test
    @DisplayName("PB-023: Publish pending outbox event successfully sends to Kafka and marks as PUBLISHED")
    void publishPendingEvents_success_marksPublished() throws Exception {
        OutboxEvent event = new OutboxEvent(
                "evt-101",
                "ORDER",
                "ord-101",
                "ORDER_CREATED",
                "{\"orderId\":\"ord-101\",\"orderNumber\":\"ORD-001\",\"totalAmount\":150000.00}",
                OutboxStatus.PENDING,
                0,
                null,
                Instant.now(),
                null,
                Instant.now()
        );

        when(outboxEventRepository.findPendingEvents(anyInt(), anyInt()))
                .thenReturn(List.of(event));
        when(outboxEventRepository.claimEventForProcessing("evt-101"))
                .thenReturn(true);

        int published = publisherService.publishPendingEvents();

        assertThat(published).isEqualTo(1);

        // Verify Kafka publish called with proper topic and message
        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(kafkaOrderEventPublisher).publish(eq(KafkaTopics.ORDER_EVENTS), eq("ord-101"), messageCaptor.capture());

        String message = messageCaptor.getValue();
        assertThat(message).contains("\"eventType\":\"ORDER_CREATED\"");
        assertThat(message).contains("\"aggregateId\":\"ord-101\"");
        assertThat(message).contains("\"eventId\":\"evt-101\"");
        assertThat(message).contains("ORD-001");

        // Verify marked as published
        verify(outboxEventRepository).markPublished(eq("evt-101"), any(Instant.class));
        verify(outboxEventRepository, never()).recordPublishFailure(anyString(), anyString(), anyInt());
    }

    @Test
    @DisplayName("PB-023: When Kafka fails, event is NOT marked as published and failure is recorded for retry")
    void publishPendingEvents_kafkaFails_recordsFailureAndNeverMarksPublished() throws Exception {
        OutboxEvent event = new OutboxEvent(
                "evt-102",
                "ORDER",
                "ord-102",
                "ORDER_CREATED",
                "{\"orderId\":\"ord-102\"}",
                OutboxStatus.PENDING,
                0,
                null,
                Instant.now(),
                null,
                Instant.now()
        );

        when(outboxEventRepository.findPendingEvents(anyInt(), anyInt()))
                .thenReturn(List.of(event));
        when(outboxEventRepository.claimEventForProcessing("evt-102"))
                .thenReturn(true);

        doThrow(new RuntimeException("Kafka broker connection timeout"))
                .when(kafkaOrderEventPublisher).publish(anyString(), anyString(), anyString());

        int published = publisherService.publishPendingEvents();

        assertThat(published).isZero();

        // Must NEVER mark published on failure
        verify(outboxEventRepository, never()).markPublished(anyString(), any(Instant.class));

        // Must record failure for retry
        verify(outboxEventRepository).recordPublishFailure(eq("evt-102"), eq("Kafka broker connection timeout"), eq(5));
    }

    @Test
    @DisplayName("PB-023: Concurrent claim conflict skips event and avoids duplicate processing")
    void publishPendingEvents_claimConflict_skipsEvent() throws Exception {
        OutboxEvent event = new OutboxEvent(
                "evt-103",
                "ORDER",
                "ord-103",
                "ORDER_CREATED",
                "{\"orderId\":\"ord-103\"}",
                OutboxStatus.PENDING,
                0,
                null,
                Instant.now(),
                null,
                Instant.now()
        );

        when(outboxEventRepository.findPendingEvents(anyInt(), anyInt()))
                .thenReturn(List.of(event));
        // Another instance claimed it first
        when(outboxEventRepository.claimEventForProcessing("evt-103"))
                .thenReturn(false);

        int published = publisherService.publishPendingEvents();

        assertThat(published).isZero();
        verify(kafkaOrderEventPublisher, never()).publish(anyString(), anyString(), anyString());
        verify(outboxEventRepository, never()).markPublished(anyString(), any(Instant.class));
    }

    @Test
    @DisplayName("PB-023: On startup, recover all lingering PROCESSING events back to PENDING")
    void onStart_recoversProcessingEvents() {
        when(outboxEventRepository.resetAllProcessingEventsToPending()).thenReturn(3);

        publisherService.onStart(null);

        verify(outboxEventRepository).resetAllProcessingEventsToPending();
    }
}
