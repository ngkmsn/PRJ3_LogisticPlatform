package com.logistics.shipment.repository;

import com.logistics.shipment.entity.DeadLetterMessage;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import io.quarkus.panache.common.Page;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class DeadLetterMessageRepository implements PanacheRepositoryBase<DeadLetterMessage, String> {

    public List<DeadLetterMessage> findPending(int page, int size) {
        return find("status = ?1", Sort.descending("failedAt"), DeadLetterMessage.STATUS_PENDING_INSPECTION)
                .page(Page.of(page, size))
                .list();
    }

    public List<DeadLetterMessage> findByFilters(String status, String topic, int page, int size) {
        if (status != null && !status.isBlank() && topic != null && !topic.isBlank()) {
            return find("status = ?1 and originalTopic = ?2", Sort.descending("failedAt"), status, topic)
                    .page(Page.of(page, size))
                    .list();
        } else if (status != null && !status.isBlank()) {
            return find("status = ?1", Sort.descending("failedAt"), status)
                    .page(Page.of(page, size))
                    .list();
        } else if (topic != null && !topic.isBlank()) {
            return find("originalTopic = ?1", Sort.descending("failedAt"), topic)
                    .page(Page.of(page, size))
                    .list();
        }
        return findAll(Sort.descending("failedAt")).page(Page.of(page, size)).list();
    }

    public Optional<DeadLetterMessage> findByOriginalMessageId(String originalMessageId) {
        return find("originalMessageId = ?1", originalMessageId).firstResultOptional();
    }

    public long countPending() {
        return count("status = ?1", DeadLetterMessage.STATUS_PENDING_INSPECTION);
    }

    public long countByStatus(String status) {
        return count("status = ?1", status);
    }

    @Transactional
    public boolean markReplayed(String id, String replayedBy) {
        int updated = update("status = ?1, replayedAt = ?2, replayedBy = ?3 where id = ?4",
                DeadLetterMessage.STATUS_REPLAYED, Instant.now(), replayedBy, id);
        return updated > 0;
    }

    @Transactional
    public boolean markDiscarded(String id, String reason) {
        int updated = update("status = ?1, errorMessage = concat(errorMessage, ' | DISCARDED: ', ?2) where id = ?3",
                DeadLetterMessage.STATUS_DISCARDED, reason, id);
        return updated > 0;
    }
}
