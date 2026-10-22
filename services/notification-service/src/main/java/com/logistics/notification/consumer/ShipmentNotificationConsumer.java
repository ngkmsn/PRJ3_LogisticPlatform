package com.logistics.notification.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.logistics.notification.client.NotificationPlatformClient;
import com.logistics.notification.client.NotificationPlatformRequest;
import com.logistics.notification.client.NotificationPlatformResult;
import com.logistics.notification.entity.InboxEvent;
import com.logistics.notification.entity.Notification;
import com.logistics.notification.repository.InboxEventRepository;
import com.logistics.notification.repository.NotificationRepository;
import com.logistics.notification.template.NotificationTemplateMapper;
import io.smallrye.common.annotation.Blocking;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.reactive.messaging.Acknowledgment;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

@ApplicationScoped
public class ShipmentNotificationConsumer {

    private static final Logger log = LoggerFactory.getLogger(ShipmentNotificationConsumer.class);

    @Inject
    NotificationRepository notificationRepository;

    @Inject
    InboxEventRepository inboxEventRepository;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    NotificationPlatformClient notificationPlatformClient;

    @Inject
    NotificationTemplateMapper templateMapper;

    public ShipmentNotificationConsumer() {
    }

    public ShipmentNotificationConsumer(NotificationRepository notificationRepository,
                                      InboxEventRepository inboxEventRepository,
                                      ObjectMapper objectMapper,
                                      NotificationPlatformClient notificationPlatformClient,
                                      NotificationTemplateMapper templateMapper) {
        this.notificationRepository = notificationRepository;
        this.inboxEventRepository = inboxEventRepository;
        this.objectMapper = objectMapper;
        this.notificationPlatformClient = notificationPlatformClient;
        this.templateMapper = templateMapper;
    }

    @Incoming("shipment-events")
    @Acknowledgment(Acknowledgment.Strategy.MANUAL)
    @Blocking
    public CompletionStage<Void> consume(Message<String> message) {
        String rawJson = message.getPayload();
        log.info("Received shipment business event on channel 'shipment-events': {}", rawJson);

        try {
            processEvent(rawJson);
            return message.ack();
        } catch (Exception e) {
            log.error("Failed to process shipment notification event, nacking: {}", e.getMessage(), e);
            return message.nack(e);
        }
    }

    /**
     * Idempotent business event processor.
     * Uses Inbox Pattern to ensure re-delivered events with duplicate eventId
     * do not produce duplicate outbound requests to the Notification Platform.
     */
    @Transactional
    public Notification processEvent(String rawJson) {
        if (rawJson == null || rawJson.trim().isEmpty()) {
            log.warn("Ignoring empty event message");
            return null;
        }

        try {
            JsonNode root = objectMapper.readTree(rawJson);
            String eventId = root.hasNonNull("eventId") ? root.get("eventId").asText() : null;
            String eventType = root.hasNonNull("eventType") ? root.get("eventType").asText() : null;
            String aggregateId = root.hasNonNull("aggregateId") ? root.get("aggregateId").asText() : null;
            JsonNode payload = root.has("payload") ? root.get("payload") : root;

            if (eventId == null || eventId.trim().isEmpty()) {
                log.warn("Event has no eventId, ignoring: {}", rawJson);
                return null;
            }

            // 1. Idempotency Guard: Deduplicate by eventId via inbox_events
            if (inboxEventRepository.existsByEventId(eventId)) {
                log.info("Event '{}' ({}) for orderId '{}' was already processed. Skipping duplicate idempotently.",
                        eventId, eventType, aggregateId);
                return null;
            }

            if (eventType == null) {
                log.warn("Event '{}' has null eventType, ignoring", eventId);
                return null;
            }

            // 2. Map event to standard NotificationPlatformRequest using predefined templates
            if (templateMapper == null) {
                templateMapper = new NotificationTemplateMapper();
            }
            NotificationPlatformRequest platformRequest = templateMapper.mapEventToPlatformRequest(eventType, aggregateId, payload);
            if (platformRequest == null) {
                log.info("Event '{}' ({}) does not require customer notification", eventId, eventType);
                return null;
            }

            // 3. Dispatch to Notification Platform via resilient HTTP client
            NotificationPlatformResult result = null;
            if (notificationPlatformClient != null) {
                result = notificationPlatformClient.sendNotification(platformRequest, eventId);
            }

            // 4. Record Notification in local database
            Notification notification = new Notification();
            notification.setId(UUID.randomUUID().toString());
            notification.setRecipient(platformRequest.getRecipient());
            notification.setChannel(platformRequest.getChannel());
            notification.setSubject(platformRequest.getSubject());
            notification.setContent(platformRequest.getContent());
            notification.setPriority(platformRequest.getPriority());
            notification.setEventId(eventId);
            notification.setEventType(eventType);
            notification.setOrderId(aggregateId);
            notification.setCreatedAt(Instant.now());

            boolean isSuccess = (result != null && result.isSuccess());
            if (isSuccess) {
                notification.setStatus("SENT");
                if (result.getPlatformNotificationId() != null) {
                    notification.setPlatformNotificationId(result.getPlatformNotificationId().toString());
                }
                notification.setSentAt(Instant.now());
                log.info("Successfully dispatched event '{}' ({}) to Notification Platform (id={})",
                        eventId, eventType, notification.getPlatformNotificationId());
            } else {
                notification.setStatus("FAILED");
                if (result != null) {
                    notification.setErrorMessage(result.getErrorMessage());
                    notification.setRetryCount(result.getAttempts());
                } else {
                    notification.setErrorMessage("NotificationPlatformClient unavailable");
                }
                log.warn("Dispatched event '{}' ({}) to Notification Platform resulted in FAILED: {}",
                        eventId, eventType, notification.getErrorMessage());
            }

            notificationRepository.persist(notification);

            // 5. Record in inbox_events to guarantee idempotency on retries
            InboxEvent inboxEvent = new InboxEvent(
                    eventId,
                    eventType,
                    aggregateId != null ? aggregateId : "UNKNOWN",
                    isSuccess ? "PROCESSED" : "FAILED"
            );
            inboxEventRepository.persist(inboxEvent);

            return notification;

        } catch (Exception e) {
            log.error("Error parsing/processing shipment event JSON: {}", e.getMessage(), e);
            throw new RuntimeException("Error processing event: " + e.getMessage(), e);
        }
    }
}
