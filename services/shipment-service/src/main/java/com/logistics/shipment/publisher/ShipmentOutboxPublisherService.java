package com.logistics.shipment.publisher;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.logistics.common.event.DomainEvent;
import com.logistics.common.event.KafkaTopics;
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

        for (OutboxEvent event : pendingEvents) {
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
