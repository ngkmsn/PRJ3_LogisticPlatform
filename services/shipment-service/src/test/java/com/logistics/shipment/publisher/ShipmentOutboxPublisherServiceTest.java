package com.logistics.shipment.publisher;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.logistics.shipment.entity.OutboxEvent;
import com.logistics.shipment.entity.OutboxStatus;
import com.logistics.shipment.repository.OutboxEventRepository;
import io.smallrye.reactive.messaging.kafka.Record;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShipmentOutboxPublisherServiceTest {

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private Emitter<Record<String, String>> emitter;

    private ObjectMapper objectMapper;
    private ShipmentOutboxPublisherService publisherService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        publisherService = new ShipmentOutboxPublisherService(
                outboxEventRepository,
                emitter,
                objectMapper
        );
    }

    @Test
    @DisplayName("PB-050: publishPendingEvents publishes pending event to Kafka and marks as PUBLISHED on ACK")
    void testPublishPendingEvents_success() throws Exception {
        OutboxEvent event = new OutboxEvent(
                "evt-ship-001",
                "SHIPMENT",
                "ord-1001",
                "ORDER_DELIVERED",
                "{\"orderId\":\"ord-1001\",\"status\":\"DELIVERED\"}",
                OutboxStatus.PENDING,
                0,
                null,
                Instant.now(),
                null,
                Instant.now()
        );

        when(outboxEventRepository.findPendingEvents(anyInt(), anyInt()))
                .thenReturn(List.of(event));
        when(outboxEventRepository.claimEventForProcessing("evt-ship-001"))
                .thenReturn(true);
        when(emitter.send(any(Record.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

        int published = publisherService.publishPendingEvents();

        assertThat(published).isEqualTo(1);

        ArgumentCaptor<Record<String, String>> captor = ArgumentCaptor.forClass(Record.class);
        verify(emitter).send(captor.capture());
        Record<String, String> record = captor.getValue();
        assertThat(record.key()).isEqualTo("ord-1001");
        assertThat(record.value()).contains("ORDER_DELIVERED");

        verify(outboxEventRepository).markPublished(eq("evt-ship-001"), any(Instant.class));
        verify(outboxEventRepository, never()).recordPublishFailure(anyString(), anyString(), anyInt());
    }

    @Test
    @DisplayName("PB-050: When Kafka emitter fails, outbox event is NOT marked published and records failure for retry")
    void testPublishPendingEvents_kafkaFailure_recordsFailure() {
        OutboxEvent event = new OutboxEvent(
                "evt-ship-002",
                "SHIPMENT",
                "ord-2002",
                "ORDER_FAILED",
                "{\"orderId\":\"ord-2002\",\"status\":\"FAILED\"}",
                OutboxStatus.PENDING,
                1,
                null,
                Instant.now(),
                null,
                Instant.now()
        );

        when(outboxEventRepository.findPendingEvents(anyInt(), anyInt()))
                .thenReturn(List.of(event));
        when(outboxEventRepository.claimEventForProcessing("evt-ship-002"))
                .thenReturn(true);

        CompletableFuture<Void> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new RuntimeException("Kafka broker connection timeout"));
        when(emitter.send(any(Record.class))).thenReturn(failedFuture);

        int published = publisherService.publishPendingEvents();

        assertThat(published).isEqualTo(0);
        verify(outboxEventRepository, never()).markPublished(anyString(), any(Instant.class));
        verify(outboxEventRepository).recordPublishFailure(eq("evt-ship-002"), anyString(), eq(5));
    }

    @Test
    @DisplayName("PB-050: Stale PROCESSING events are recovered back to PENDING on publisher cycle")
    void testPublishPendingEvents_recoversStaleProcessingEvents() {
        when(outboxEventRepository.resetStaleProcessingEvents(any(Instant.class))).thenReturn(2);
        when(outboxEventRepository.findPendingEvents(anyInt(), anyInt())).thenReturn(List.of());

        int published = publisherService.publishPendingEvents();

        assertThat(published).isEqualTo(0);
        verify(outboxEventRepository).resetStaleProcessingEvents(any(Instant.class));
    }
}
