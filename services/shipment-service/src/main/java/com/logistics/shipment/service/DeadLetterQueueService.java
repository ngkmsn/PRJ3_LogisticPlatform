package com.logistics.shipment.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.logistics.common.dlq.DeadLetterEnvelope;
import com.logistics.common.dlq.DeadLetterSanitizer;
import com.logistics.common.dto.DeadLetterMetricsDto;
import com.logistics.common.event.KafkaTopics;
import com.logistics.shipment.consumer.OrderCreatedConsumer;
import com.logistics.shipment.entity.Assignment;
import com.logistics.shipment.entity.DeadLetterMessage;
import com.logistics.shipment.repository.DeadLetterMessageRepository;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.smallrye.reactive.messaging.kafka.Record;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Service managing Dead Letter Queue (DLQ) operations (PB-053).
 * Routes poison pills and retry-exhausted messages to DLQ with broker ACK confirmation,
 * provides forensic search and metrics, and enables idempotent replay capabilities.
 */
@ApplicationScoped
public class DeadLetterQueueService {

    private static final Logger log = LoggerFactory.getLogger(DeadLetterQueueService.class);

    @Inject
    DeadLetterMessageRepository repository;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    @Channel("order-events-dlq")
    Emitter<Record<String, String>> dlqEmitter;

    @Inject
    jakarta.enterprise.inject.Instance<OrderCreatedConsumer> consumerInstance;

    public DeadLetterQueueService() {
    }

    public DeadLetterQueueService(DeadLetterMessageRepository repository,
                                  ObjectMapper objectMapper,
                                  Emitter<Record<String, String>> dlqEmitter) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.dlqEmitter = dlqEmitter;
    }

    /**
     * Routes an unprocessable or exhausted message into the Dead Letter Queue.
     * <p>
     * Guarantees:
     * 1. Sensitive security credentials (passwords, tokens, API keys) are sanitized before persistence.
     * 2. Message is persisted to dead_letter_messages table in a dedicated transaction.
     * 3. Message is emitted to Kafka DLQ topic (e.g. logistics.order.events.dlq).
     * 4. The returned CompletionStage completes only after both storage and broker publish are confirmed.
     *    If either fails, the stage completes exceptionally and the consumer must NOT acknowledge the original message.
     */
    public CompletionStage<Void> sendToDlq(String originalTopic, String rawJson, Throwable error, int attempts) {
        if (originalTopic == null || originalTopic.isBlank()) {
            originalTopic = KafkaTopics.ORDER_EVENTS;
        }

        String originalMessageId = "dlq-msg-" + UUID.randomUUID();
        String eventType = "UNKNOWN";
        String aggregateId = "UNKNOWN";

        // Extract metadata from raw JSON if possible
        if (rawJson != null && !rawJson.isBlank()) {
            try {
                JsonNode root = objectMapper.readTree(rawJson);
                if (root.hasNonNull("eventId")) {
                    originalMessageId = root.get("eventId").asText().trim();
                }
                if (root.hasNonNull("eventType")) {
                    eventType = root.get("eventType").asText().trim();
                }
                if (root.hasNonNull("aggregateId")) {
                    aggregateId = root.get("aggregateId").asText().trim();
                } else if (root.has("payload") && root.get("payload").hasNonNull("orderId")) {
                    aggregateId = root.get("payload").get("orderId").asText().trim();
                }
            } catch (Exception e) {
                log.warn("PB-053 [DLQ_PARSE_WARN] Raw message is not valid JSON, recording as malformed payload: {}", e.getMessage());
                eventType = "MALFORMED_JSON";
            }
        }

        // Sanitize payload and error message to protect secrets
        String sanitizedPayload = DeadLetterSanitizer.sanitize(rawJson != null ? rawJson : "");
        String exceptionClass = error != null ? error.getClass().getName() : "UnknownException";
        String sanitizedErrorMessage = DeadLetterSanitizer.sanitize(error != null ? error.getMessage() : "Unknown error");

        DeadLetterMessage entity = new DeadLetterMessage(
                originalMessageId,
                originalTopic,
                eventType,
                aggregateId,
                sanitizedPayload,
                exceptionClass,
                sanitizedErrorMessage,
                attempts
        );

        // 1. Persist to Database within a separate transaction
        if (repository != null) {
            try {
                if (io.quarkus.arc.Arc.container() != null) {
                    QuarkusTransaction.requiringNew().run(() -> repository.persist(entity));
                } else {
                    repository.persist(entity);
                }
            } catch (Exception dbEx) {
                log.error("PB-053 [DLQ_DB_FAIL] Failed to persist DeadLetterMessage to database: {}", dbEx.getMessage(), dbEx);
                return CompletableFuture.failedFuture(dbEx);
            }
        }

        // 2. Build Kafka DeadLetterEnvelope and publish to Kafka DLQ topic
        DeadLetterEnvelope<String> envelope = new DeadLetterEnvelope<>(
                originalMessageId,
                originalTopic,
                eventType,
                aggregateId,
                sanitizedPayload,
                exceptionClass,
                sanitizedErrorMessage,
                attempts
        );
        envelope.setDeadLetterId(entity.getId());

        String serializedEnvelope;
        try {
            serializedEnvelope = objectMapper.writeValueAsString(envelope);
        } catch (Exception jsonEx) {
            serializedEnvelope = sanitizedPayload;
        }

        log.warn("PB-053 [DLQ_DISPATCH] Routing poison/exhausted message to DLQ: id='{}', eventId='{}', eventType='{}', error='{}', attempts={}",
                entity.getId(), originalMessageId, eventType, sanitizedErrorMessage, attempts);

        if (dlqEmitter != null) {
            try {
                return dlqEmitter.send(Record.of(originalMessageId, serializedEnvelope))
                        .thenRun(() -> log.info("PB-053 [DLQ_ACK_CONFIRMED] Broker acknowledged DLQ publish for message '{}'", entity.getId()));
            } catch (Exception pubEx) {
                log.error("PB-053 [DLQ_PUBLISH_FAIL] Failed to emit record to DLQ topic: {}", pubEx.getMessage(), pubEx);
                return CompletableFuture.failedFuture(pubEx);
            }
        }

        return CompletableFuture.completedFuture(null);
    }

    public List<DeadLetterMessage> listMessages(String status, String topic, int page, int size) {
        if (repository == null) {
            return List.of();
        }
        return repository.findByFilters(status, topic, Math.max(0, page), Math.max(1, size));
    }

    public Optional<DeadLetterMessage> getById(String id) {
        if (repository == null || id == null) {
            return Optional.empty();
        }
        return repository.findByIdOptional(id);
    }

    public DeadLetterMetricsDto getMetrics() {
        if (repository == null) {
            return new DeadLetterMetricsDto(0, 0, 0);
        }
        long pending = repository.countPending();
        long replayed = repository.countByStatus(DeadLetterMessage.STATUS_REPLAYED);
        long discarded = repository.countByStatus(DeadLetterMessage.STATUS_DISCARDED);
        return new DeadLetterMetricsDto(pending, replayed, discarded);
    }

    /**
     * Controlled replay of a dead-letter message using resolved consumer.
     */
    public boolean replayMessage(String id, String replayedBy) {
        OrderCreatedConsumer consumer = consumerInstance != null && consumerInstance.isResolvable() ? consumerInstance.get() : null;
        if (consumer == null) {
            log.error("PB-053 [DLQ_REPLAY_FAIL] OrderCreatedConsumer is not resolvable for DLQ replay of id='{}'", id);
            throw new IllegalStateException("Consumer is not available for replaying DLQ message");
        }
        return replayMessage(id, replayedBy, consumer);
    }

    /**
     * Controlled replay of a dead-letter message.
     * Guarantees idempotency via Inbox Pattern so that previously completed business
     * operations are not duplicated upon replay.
     */
    public boolean replayMessage(String id, String replayedBy, OrderCreatedConsumer consumer) {
        if (repository == null || id == null || consumer == null) {
            return false;
        }

        DeadLetterMessage message = repository.findById(id);
        if (message == null) {
            log.warn("Cannot replay non-existent DLQ message id='{}'", id);
            return false;
        }

        log.info("PB-053 [DLQ_REPLAY_START] Replaying DLQ message id='{}' (originalEventId='{}') by user='{}'",
                id, message.getOriginalMessageId(), replayedBy);

        try {
            // Re-execute through consumer pipeline (Inbox Pattern guarantees idempotency)
            Assignment result = consumer.processOrderEvent(message.getPayload());

            // Mark message as REPLAYED in DLQ
            repository.markReplayed(id, replayedBy != null ? replayedBy : "SYSTEM");

            log.info("PB-053 [DLQ_REPLAY_SUCCESS] DLQ message id='{}' successfully replayed, assignmentId='{}'",
                    id, result != null ? result.getId() : "NONE");
            return true;

        } catch (Exception e) {
            log.error("PB-053 [DLQ_REPLAY_FAIL] Replay failed for message id='{}': {}", id, e.getMessage(), e);
            throw new RuntimeException("Replay failed for DLQ message: " + e.getMessage(), e);
        }
    }

    /**
     * Marks a poison message as DISCARDED after administrative investigation.
     */
    public boolean discardMessage(String id, String reason) {
        if (repository == null || id == null) {
            return false;
        }
        return repository.markDiscarded(id, reason != null ? reason : "Administrative discard");
    }
}
