package com.logistics.order.repository;

import com.logistics.order.entity.OutboxEvent;
import com.logistics.order.entity.OutboxStatus;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

@ApplicationScoped
public class OutboxEventRepository implements PanacheRepositoryBase<OutboxEvent, String> {

    public List<OutboxEvent> findPendingEvents() {
        return find("status = ?1 order by occurredAt asc", OutboxStatus.PENDING).list();
    }

    public List<OutboxEvent> findByAggregateId(String aggregateId) {
        return find("aggregateId = ?1 order by occurredAt asc", aggregateId).list();
    }

    public List<OutboxEvent> findByEventType(String eventType) {
        return find("eventType = ?1 order by occurredAt asc", eventType).list();
    }
}
