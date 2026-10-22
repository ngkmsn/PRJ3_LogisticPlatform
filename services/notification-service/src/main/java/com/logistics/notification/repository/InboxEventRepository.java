package com.logistics.notification.repository;

import com.logistics.notification.entity.InboxEvent;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Optional;

@ApplicationScoped
public class InboxEventRepository implements PanacheRepositoryBase<InboxEvent, String> {

    public Optional<InboxEvent> findByEventId(String eventId) {
        return find("eventId", eventId).firstResultOptional();
    }

    public boolean existsByEventId(String eventId) {
        return count("eventId", eventId) > 0;
    }
}
