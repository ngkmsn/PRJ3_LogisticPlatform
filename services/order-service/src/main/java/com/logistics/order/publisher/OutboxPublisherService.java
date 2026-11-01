package com.logistics.order.publisher;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.logistics.common.event.DomainEvent;
import com.logistics.common.event.KafkaTopics;
import com.logistics.common.retry.BackoffCalculator;
import com.logistics.common.retry.RetryPolicy;
import com.logistics.order.entity.OutboxEvent;
import com.logistics.order.repository.OutboxEventRepository;
import io.quarkus.runtime.StartupEvent;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@ApplicationScoped
public class OutboxPublisherService {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisherService.class);

    @Inject
    OutboxEventRepository outboxEventRepository;

    @Inject
    KafkaOrderEventPublisher kafkaOrderEventPublisher;

    @Inject
    ObjectMapper objectMapper;

    @ConfigProperty(name = "outbox.publisher.enabled", defaultValue = "true")
    boolean publisherEnabled;

    @ConfigProperty(name = "outbox.publisher.batch-size", defaultValue = "50")
    int batchSize;

    @ConfigProperty(name = "outbox.publisher.max-retries", defaultValue = "5")
    int maxRetries;

    @ConfigProperty(name = "outbox.publisher.stale-timeout-minutes", defaultValue = "5")
    long staleTimeoutMinutes;

    @ConfigProperty(name = "outbox.publisher.initial-backoff-ms", defaultValue = "1000")
    long initialBackoffMs = 1000;

    @ConfigProperty(name = "outbox.publisher.max-backoff-ms", defaultValue = "30000")
    long maxBackoffMs = 30000;

    @ConfigProperty(name = "outbox.publisher.backoff-multiplier", defaultValue = "2.0")
    double backoffMultiplier = 2.0;

    @ConfigProperty(name = "outbox.publisher.jitter-factor", defaultValue = "0.2")
    double jitterFactor = 0.2;

    private BackoffCalculator backoffCalculator;

    public OutboxPublisherService() {
    }

    public OutboxPublisherService(OutboxEventRepository outboxEventRepository,
                                  KafkaOrderEventPublisher kafkaOrderEventPublisher,
                                  ObjectMapper objectMapper) {
        this.outboxEventRepository = outboxEventRepository;
        this.kafkaOrderEventPublisher = kafkaOrderEventPublisher;
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
        // Recover any events that were in PROCESSING when service was restarted or abruptly stopped
        int recovered = outboxEventRepository.resetAllProcessingEventsToPending();
        if (recovered > 0) {
            log.info("Outbox publisher onStart: recovered {} stuck PROCESSING events back to PENDING", recovered);
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
        // 1. Recover stale processing events (timed-out workers)
        Instant staleThreshold = Instant.now().minus(staleTimeoutMinutes, ChronoUnit.MINUTES);
        int recoveredStale = outboxEventRepository.resetStaleProcessingEvents(staleThreshold);
        if (recoveredStale > 0) {
            log.warn("Recovered {} stale PROCESSING outbox events back to PENDING", recoveredStale);
        }

        // 2. Fetch pending events eligible for publish
        List<OutboxEvent> pendingEvents = outboxEventRepository.findPendingEvents(maxRetries, batchSize);
        if (pendingEvents.isEmpty()) {
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
                    log.debug("PB-052 [OUTBOX_BACKOFF] Event '{}' (retryCount={}) backoff of {}ms not reached yet, skipping",
                            event.getId(), event.getRetryCount(), requiredBackoff.toMillis());
                    continue;
                }
            }

            // Atomic claim to prevent duplicate processing by concurrent publisher nodes
            boolean claimed = outboxEventRepository.claimEventForProcessing(event.getId());
            if (!claimed) {
                log.debug("Event '{}' was already claimed by another worker, skipping", event.getId());
                continue;
            }

            try {
                // Build standard DomainEvent envelope matching contract
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

                String serializedMessage = objectMapper.writeValueAsString(domainEvent);

                // Publish to Kafka and await confirmation
                kafkaOrderEventPublisher.publish(
                        KafkaTopics.ORDER_EVENTS,
                        event.getAggregateId(),
                        serializedMessage
                );

                // Only mark as PUBLISHED after Kafka acknowledges receipt
                outboxEventRepository.markPublished(event.getId(), Instant.now());
                publishedCount++;
                log.info("Successfully published outbox event '{}' ({}) to topic '{}'",
                        event.getId(), event.getEventType(), KafkaTopics.ORDER_EVENTS);

            } catch (Exception e) {
                log.error("Failed to publish outbox event '{}' to Kafka: {}", event.getId(), e.getMessage());
                // Event is NOT marked published; record failure and retry
                outboxEventRepository.recordPublishFailure(event.getId(), e.getMessage(), maxRetries);
            }
        }

        return publishedCount;
    }
}
