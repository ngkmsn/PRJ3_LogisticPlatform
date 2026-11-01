package com.logistics.shipment.publisher;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.logistics.common.event.DomainEvent;
import com.logistics.common.event.KafkaTopics;
import com.logistics.common.retry.BackoffCalculator;
import com.logistics.common.retry.RetryPolicy;
import com.logistics.shipment.entity.OutboxEvent;
import com.logistics.shipment.repository.OutboxEventRepository;
import io.quarkus.runtime.StartupEvent;
import io.quarkus.scheduler.Scheduled;
import io.smallrye.reactive.messaging.kafka.Record;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@ApplicationScoped
public class ShipmentOutboxPublisherService {

    private static final Logger log = LoggerFactory.getLogger(ShipmentOutboxPublisherService.class);

    @Inject
    OutboxEventRepository outboxEventRepository;

    @Inject
    @Channel("shipment-events")
    Emitter<Record<String, String>> emitter;

    @Inject
    ObjectMapper objectMapper;

    @ConfigProperty(name = "outbox.publisher.enabled", defaultValue = "true")
    boolean publisherEnabled = true;

    @ConfigProperty(name = "outbox.publisher.batch-size", defaultValue = "50")
    int batchSize = 50;

    @ConfigProperty(name = "outbox.publisher.max-retries", defaultValue = "5")
    int maxRetries = 5;

    @ConfigProperty(name = "outbox.publisher.stale-timeout-minutes", defaultValue = "5")
    long staleTimeoutMinutes = 5;

    @ConfigProperty(name = "outbox.publisher.initial-backoff-ms", defaultValue = "1000")
    long initialBackoffMs = 1000;

    @ConfigProperty(name = "outbox.publisher.max-backoff-ms", defaultValue = "30000")
    long maxBackoffMs = 30000;

    @ConfigProperty(name = "outbox.publisher.backoff-multiplier", defaultValue = "2.0")
    double backoffMultiplier = 2.0;

    @ConfigProperty(name = "outbox.publisher.jitter-factor", defaultValue = "0.2")
    double jitterFactor = 0.2;

    private BackoffCalculator backoffCalculator;

    public ShipmentOutboxPublisherService() {
    }

    public ShipmentOutboxPublisherService(OutboxEventRepository outboxEventRepository,
                                          Emitter<Record<String, String>> emitter,
                                          ObjectMapper objectMapper) {
        this.outboxEventRepository = outboxEventRepository;
        this.emitter = emitter;
        this.objectMapper = objectMapper;
        this.publisherEnabled = true;
        this.batchSize = 50;
        this.maxRetries = 5;
        this.staleTimeoutMinutes = 5;
        this.initialBackoffMs = 1000;
        this.maxBackoffMs = 30000;
        this.backoffMultiplier = 2.0;
        this.jitterFactor = 0.2;
        initBackoffCalculator();
    }

    private void initBackoffCalculator() {
        RetryPolicy policy = RetryPolicy.builder()
                .maxRetries(maxRetries)
                .initialBackoff(Duration.ofMillis(Math.max(1, initialBackoffMs)))
                .maxBackoff(Duration.ofMillis(Math.max(initialBackoffMs, maxBackoffMs)))
                .multiplier(Math.max(1.0, backoffMultiplier))
                .jitterFactor(Math.max(0.0, Math.min(1.0, jitterFactor)))
                .build();
        this.backoffCalculator = new BackoffCalculator(policy);
    }

    public void onStart(@Observes StartupEvent ev) {
        if (outboxEventRepository != null) {
            int recovered = outboxEventRepository.resetAllProcessingEventsToPending();
            if (recovered > 0) {
                log.info("Shipment outbox publisher onStart: recovered {} stuck PROCESSING events back to PENDING", recovered);
            }
        }
    }

    @Scheduled(
            every = "${outbox.publisher.interval:5s}",
            concurrentExecution = Scheduled.ConcurrentExecution.SKIP
    )
    public void publishPendingEventsScheduled() {
        if (!publisherEnabled) {
            return;
        }
        publishPendingEvents();
    }

    public int publishPendingEvents() {
        if (outboxEventRepository == null || emitter == null) {
            return 0;
        }

        // 1. Recover stale processing events
        Instant staleThreshold = Instant.now().minus(staleTimeoutMinutes, ChronoUnit.MINUTES);
        int recoveredStale = outboxEventRepository.resetStaleProcessingEvents(staleThreshold);
        if (recoveredStale > 0) {
            log.warn("Recovered {} stale PROCESSING outbox events back to PENDING in shipment-service", recoveredStale);
        }

        // 2. Fetch pending events
        List<OutboxEvent> pendingEvents = outboxEventRepository.findPendingEvents(maxRetries, batchSize);
        if (pendingEvents == null || pendingEvents.isEmpty()) {
            return 0;
        }

        int publishedCount = 0;
        Instant now = Instant.now();
        if (backoffCalculator == null) {
            initBackoffCalculator();
        }

        for (OutboxEvent event : pendingEvents) {
            // PB-052: Check exponential backoff for previously failed events
            if (event.getRetryCount() > 0 && event.getOccurredAt() != null) {
                Duration requiredBackoff = backoffCalculator.computeBackoff(event.getRetryCount());
                if (event.getOccurredAt().plus(requiredBackoff).isAfter(now)) {
                    log.debug("PB-052 [SHIPMENT_OUTBOX_BACKOFF] Event '{}' (retryCount={}) backoff of {}ms not reached yet, skipping",
                            event.getId(), event.getRetryCount(), requiredBackoff.toMillis());
                    continue;
                }
            }

            boolean claimed = outboxEventRepository.claimEventForProcessing(event.getId());
            if (!claimed) {
                log.debug("Shipment event '{}' was already claimed, skipping", event.getId());
                continue;
            }

            try {
                // If the payload is already serialized JSON or raw payload, wrap or parse appropriately
                String messageJson = event.getPayload();
                if (!messageJson.contains("\"eventId\"")) {
                    // Wrap if needed
                    Object payloadJsonNode;
                    try {
                        payloadJsonNode = objectMapper.readTree(event.getPayload());
                    } catch (Exception e) {
                        payloadJsonNode = event.getPayload();
                    }

                    DomainEvent<Object> domainEvent = new DomainEvent<>(
                            event.getEventType(),
                            event.getAggregateId(),
                            payloadJsonNode
                    );
                    domainEvent.setEventId(event.getId());
                    domainEvent.setTimestamp(event.getOccurredAt());
                    messageJson = objectMapper.writeValueAsString(domainEvent);
                }

                // Send to Kafka
                String partitionKey = event.getAggregateId();
                emitter.send(Record.of(partitionKey, messageJson))
                        .toCompletableFuture()
                        .join();

                outboxEventRepository.markPublished(event.getId(), Instant.now());
                publishedCount++;
                log.info("Successfully published shipment outbox event '{}' ({}) for orderId '{}' to Kafka",
                        event.getId(), event.getEventType(), partitionKey);

            } catch (Exception e) {
                log.error("Failed to publish shipment outbox event '{}' to Kafka: {}", event.getId(), e.getMessage());
                outboxEventRepository.recordPublishFailure(event.getId(), e.getMessage(), maxRetries);
            }
        }

        return publishedCount;
    }
}
