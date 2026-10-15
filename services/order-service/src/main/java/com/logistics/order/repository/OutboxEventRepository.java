package com.logistics.order.repository;

import com.logistics.order.entity.OutboxEvent;
import com.logistics.order.entity.OutboxStatus;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import io.quarkus.panache.common.Page;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.time.Instant;
import java.util.List;

@ApplicationScoped
public class OutboxEventRepository implements PanacheRepositoryBase<OutboxEvent, String> {

    public List<OutboxEvent> findPendingEvents() {
        return find("status = ?1 order by occurredAt asc", OutboxStatus.PENDING).list();
    }

    public List<OutboxEvent> findPendingEvents(int maxRetries, int limit) {
        return find("status = ?1 and retryCount < ?2 order by occurredAt asc", OutboxStatus.PENDING, maxRetries)
                .page(Page.of(0, limit))
                .list();
    }

    public List<OutboxEvent> findByAggregateId(String aggregateId) {
        return find("aggregateId = ?1 order by occurredAt asc", aggregateId).list();
    }

    public List<OutboxEvent> findByEventType(String eventType) {
        return find("eventType = ?1 order by occurredAt asc", eventType).list();
    }

    @Transactional
    public boolean claimEventForProcessing(String eventId) {
        int updated = update("status = ?1 where id = ?2 and status = ?3",
                OutboxStatus.PROCESSING, eventId, OutboxStatus.PENDING);
        return updated == 1;
    }

    @Transactional
    public void markPublished(String eventId, Instant publishedAt) {
        update("status = ?1, publishedAt = ?2, errorMessage = null where id = ?3",
                OutboxStatus.PUBLISHED, publishedAt, eventId);
    }

    @Transactional
    public void recordPublishFailure(String eventId, String errorMessage, int maxRetries) {
        OutboxEvent event = findById(eventId);
        if (event == null) {
            return;
        }
        int newRetryCount = event.getRetryCount() + 1;
        event.setRetryCount(newRetryCount);
        event.setErrorMessage(errorMessage != null && errorMessage.length() > 500
                ? errorMessage.substring(0, 497) + "..."
                : errorMessage);

        if (newRetryCount >= maxRetries) {
            event.setStatus(OutboxStatus.FAILED);
        } else {
            // Keep as PENDING so it can be retried in next cycle
            event.setStatus(OutboxStatus.PENDING);
        }
        persist(event);
    }

    @Transactional
    public int resetStaleProcessingEvents(Instant staleThreshold) {
        return update("status = ?1 where status = ?2 and occurredAt < ?3",
                OutboxStatus.PENDING, OutboxStatus.PROCESSING, staleThreshold);
    }

    @Transactional
    public int resetAllProcessingEventsToPending() {
        return update("status = ?1 where status = ?2",
                OutboxStatus.PENDING, OutboxStatus.PROCESSING);
    }
}
