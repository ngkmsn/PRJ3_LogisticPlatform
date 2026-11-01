package com.logistics.shipment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.logistics.common.dto.DeadLetterMetricsDto;
import com.logistics.shipment.consumer.OrderCreatedConsumer;
import com.logistics.shipment.entity.Assignment;
import com.logistics.shipment.entity.DeadLetterMessage;
import com.logistics.shipment.repository.DeadLetterMessageRepository;
import io.smallrye.reactive.messaging.kafka.Record;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeadLetterQueueServiceTest {

    @Mock
    private DeadLetterMessageRepository repository;

    @Mock
    private Emitter<Record<String, String>> dlqEmitter;

    @Mock
    private OrderCreatedConsumer consumer;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private DeadLetterQueueService service;

    @BeforeEach
    void setUp() {
        service = new DeadLetterQueueService(repository, objectMapper, dlqEmitter);
    }

    @Test
    @DisplayName("sendToDlq: valid event json extracts metadata, persists entity, and publishes to Kafka DLQ")
    void sendToDlq_validPayload_persistsAndPublishes() throws Exception {
        String json = """
                {
                    "eventId": "evt-12345",
                    "eventType": "ORDER_CREATED",
                    "aggregateId": "ord-8888",
                    "payload": {
                        "orderId": "ord-8888",
                        "customerName": "Alice"
                    }
                }
                """;
        Throwable err = new RuntimeException("Validation failed for order");

        when(dlqEmitter.send(any(Record.class))).thenReturn(CompletableFuture.completedFuture(null));

        CompletionStage<Void> stage = service.sendToDlq("logistics.order.events", json, err, 3);
        stage.toCompletableFuture().get();

        ArgumentCaptor<DeadLetterMessage> entityCaptor = ArgumentCaptor.forClass(DeadLetterMessage.class);
        verify(repository).persist(entityCaptor.capture());
        DeadLetterMessage saved = entityCaptor.getValue();

        assertThat(saved.getOriginalMessageId()).isEqualTo("evt-12345");
        assertThat(saved.getEventType()).isEqualTo("ORDER_CREATED");
        assertThat(saved.getAggregateId()).isEqualTo("ord-8888");
        assertThat(saved.getRetryCount()).isEqualTo(3);
        assertThat(saved.getExceptionClass()).isEqualTo(RuntimeException.class.getName());
        assertThat(saved.getErrorMessage()).isEqualTo("Validation failed for order");
        assertThat(saved.getStatus()).isEqualTo(DeadLetterMessage.STATUS_PENDING_INSPECTION);

        ArgumentCaptor<Record<String, String>> recordCaptor = ArgumentCaptor.forClass(Record.class);
        verify(dlqEmitter).send(recordCaptor.capture());
        Record<String, String> record = recordCaptor.getValue();
        assertThat(record.key()).isEqualTo("evt-12345");
        assertThat(record.value()).contains("evt-12345").contains("ORDER_CREATED");
    }

    @Test
    @DisplayName("sendToDlq: sanitizes passwords and auth tokens from payload and error message")
    void sendToDlq_sanitizesSensitiveSecrets() throws Exception {
        String sensitiveJson = """
                {
                    "eventId": "evt-secret",
                    "eventType": "ORDER_CREATED",
                    "aggregateId": "ord-secret",
                    "payload": {
                        "password": "SuperSecretPassword123!",
                        "apiKey": "key_abcdef123456",
                        "token": "bearer.jwt.secret.token"
                    }
                }
                """;
        Throwable sensitiveError = new RuntimeException("Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9 failed");

        when(dlqEmitter.send(any(Record.class))).thenReturn(CompletableFuture.completedFuture(null));

        CompletionStage<Void> stage = service.sendToDlq("logistics.order.events", sensitiveJson, sensitiveError, 1);
        stage.toCompletableFuture().get();

        ArgumentCaptor<DeadLetterMessage> entityCaptor = ArgumentCaptor.forClass(DeadLetterMessage.class);
        verify(repository).persist(entityCaptor.capture());
        DeadLetterMessage saved = entityCaptor.getValue();

        assertThat(saved.getPayload()).doesNotContain("SuperSecretPassword123!");
        assertThat(saved.getPayload()).contains("[REDACTED]");
        assertThat(saved.getErrorMessage()).doesNotContain("eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9");
        assertThat(saved.getErrorMessage()).contains("[REDACTED]");
    }

    @Test
    @DisplayName("sendToDlq: malformed non-JSON payload is recorded gracefully as MALFORMED_JSON")
    void sendToDlq_malformedPayload_recordsGracefully() throws Exception {
        String malformed = "{ this is not valid json : [";
        Throwable error = new RuntimeException("Unparseable JSON");

        when(dlqEmitter.send(any(Record.class))).thenReturn(CompletableFuture.completedFuture(null));

        CompletionStage<Void> stage = service.sendToDlq("logistics.order.events", malformed, error, 1);
        stage.toCompletableFuture().get();

        ArgumentCaptor<DeadLetterMessage> entityCaptor = ArgumentCaptor.forClass(DeadLetterMessage.class);
        verify(repository).persist(entityCaptor.capture());
        DeadLetterMessage saved = entityCaptor.getValue();

        assertThat(saved.getEventType()).isEqualTo("MALFORMED_JSON");
        assertThat(saved.getPayload()).isEqualTo(malformed);
        verify(dlqEmitter).send(any(Record.class));
    }

    @Test
    @DisplayName("sendToDlq: broker failure causes CompletionStage to complete exceptionally (original message not ACKed)")
    void sendToDlq_brokerFailure_completesExceptionally() {
        String json = "{\"eventId\":\"evt-err\"}";
        RuntimeException brokerError = new RuntimeException("Kafka cluster unreachable");

        when(dlqEmitter.send(any(Record.class)))
                .thenReturn(CompletableFuture.failedFuture(brokerError));

        CompletionStage<Void> stage = service.sendToDlq("logistics.order.events", json, new RuntimeException("Err"), 1);

        assertThatThrownBy(() -> stage.toCompletableFuture().get())
                .isInstanceOf(ExecutionException.class)
                .hasCause(brokerError);
    }

    @Test
    @DisplayName("sendToDlq: database persistence failure fails stage immediately and prevents Kafka emission")
    void sendToDlq_databaseFailure_failsStage() {
        String json = "{\"eventId\":\"evt-db-err\"}";
        doThrow(new RuntimeException("DB unique constraint violation")).when(repository).persist(any(DeadLetterMessage.class));

        CompletionStage<Void> stage = service.sendToDlq("logistics.order.events", json, new RuntimeException("Err"), 1);

        assertThatThrownBy(() -> stage.toCompletableFuture().get())
                .isInstanceOf(ExecutionException.class)
                .hasMessageContaining("DB unique constraint violation");
        verify(dlqEmitter, never()).send(any(Record.class));
    }

    @Test
    @DisplayName("replayMessage: successfully invokes consumer and marks message REPLAYED")
    void replayMessage_success_marksReplayed() {
        String msgId = "dlq-msg-1";
        DeadLetterMessage msg = new DeadLetterMessage(
                "evt-100", "logistics.order.events", "ORDER_CREATED", "ord-100",
                "{\"payload\":{}}", "Exception", "Error", 1
        );
        msg.setId(msgId);

        when(repository.findById(msgId)).thenReturn(msg);
        when(consumer.processOrderEvent(anyString())).thenReturn(new Assignment());
        when(repository.markReplayed(eq(msgId), eq("operator_bob"))).thenReturn(true);

        boolean result = service.replayMessage(msgId, "operator_bob", consumer);

        assertThat(result).isTrue();
        verify(consumer).processOrderEvent(msg.getPayload());
        verify(repository).markReplayed(eq(msgId), eq("operator_bob"));
    }

    @Test
    @DisplayName("replayMessage: returns false when message not found in repository")
    void replayMessage_notFound_returnsFalse() {
        when(repository.findById("unknown-id")).thenReturn(null);

        boolean result = service.replayMessage("unknown-id", "operator_bob", consumer);

        assertThat(result).isFalse();
        verify(consumer, never()).processOrderEvent(anyString());
        verify(repository, never()).markReplayed(anyString(), anyString());
    }

    @Test
    @DisplayName("replayMessage: if consumer re-throw exception, message status is NOT updated to REPLAYED")
    void replayMessage_consumerFails_rethrowsException() {
        String msgId = "dlq-msg-err";
        DeadLetterMessage msg = new DeadLetterMessage(
                "evt-err", "logistics.order.events", "ORDER_CREATED", "ord-err",
                "{\"payload\":{}}", "Exception", "Error", 1
        );
        when(repository.findById(msgId)).thenReturn(msg);
        when(consumer.processOrderEvent(anyString())).thenThrow(new IllegalArgumentException("Still invalid"));

        assertThatThrownBy(() -> service.replayMessage(msgId, "operator_bob", consumer))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Replay failed for DLQ message");

        verify(repository, never()).markReplayed(anyString(), anyString());
    }

    @Test
    @DisplayName("discardMessage: marks status DISCARDED with audit reason")
    void discardMessage_marksDiscarded() {
        when(repository.markDiscarded("dlq-discard-1", "Test discard reason")).thenReturn(true);

        boolean result = service.discardMessage("dlq-discard-1", "Test discard reason");

        assertThat(result).isTrue();
        verify(repository).markDiscarded("dlq-discard-1", "Test discard reason");
    }

    @Test
    @DisplayName("getMetrics: aggregates pending, replayed, and discarded counts")
    void getMetrics_returnsAggregatedCounts() {
        when(repository.countPending()).thenReturn(5L);
        when(repository.countByStatus(DeadLetterMessage.STATUS_REPLAYED)).thenReturn(12L);
        when(repository.countByStatus(DeadLetterMessage.STATUS_DISCARDED)).thenReturn(3L);

        DeadLetterMetricsDto metrics = service.getMetrics();

        assertThat(metrics.getTotalPending()).isEqualTo(5L);
        assertThat(metrics.getTotalReplayed()).isEqualTo(12L);
        assertThat(metrics.getTotalDiscarded()).isEqualTo(3L);
        assertThat(metrics.getTotalMessages()).isEqualTo(20L);
    }
}
