package com.logistics.shipment.repository;

import com.logistics.shipment.entity.InboxEvent;
import com.logistics.shipment.model.InboxStatus;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Optional;

@ApplicationScoped
public class InboxEventRepository implements PanacheRepositoryBase<InboxEvent, String> {

    public Optional<InboxEvent> findByEventId(String eventId) {
        return find("eventId", eventId).firstResultOptional();
    }

    public boolean isEventProcessed(String eventId) {
        return find("eventId = ?1 and status = ?2", eventId, InboxStatus.PROCESSED).count() > 0;
    }
}
