package com.logistics.notification.repository;

import com.logistics.notification.entity.Notification;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

@ApplicationScoped
public class NotificationRepository implements PanacheRepositoryBase<Notification, String> {

    public List<Notification> findByOrderId(String orderId) {
        return find("orderId = ?1 order by createdAt desc", orderId).list();
    }

    public List<Notification> findByEventId(String eventId) {
        return find("eventId = ?1", eventId).list();
    }

    public List<Notification> findFailedForRetry(int maxRetryCount, int limit) {
        return find("status = 'FAILED' and retryCount < ?1 order by createdAt asc", maxRetryCount)
                .page(0, limit)
                .list();
    }

    @jakarta.transaction.Transactional
    public void updateRetryResult(String id, boolean success, String platformId, String errorMessage, int maxAttempts) {
        Notification current = findById(id);
        if (current == null) {
            return;
        }
        current.setRetryCount(current.getRetryCount() + 1);
        if (success) {
            current.setStatus("SENT");
            current.setPlatformNotificationId(platformId);
            current.setSentAt(java.time.Instant.now());
            current.setErrorMessage(null);
        } else {
            current.setErrorMessage(errorMessage);
            if (current.getRetryCount() >= maxAttempts) {
                current.setStatus("PERMANENTLY_FAILED");
            }
        }
    }
}
