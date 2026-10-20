package com.logistics.shipment.repository;

import com.logistics.shipment.entity.AssignmentHistory;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

@ApplicationScoped
public class AssignmentHistoryRepository implements PanacheRepositoryBase<AssignmentHistory, String> {

    public List<AssignmentHistory> findByOrderIdOrderByCreatedAtAsc(String orderId) {
        if (orderId == null || orderId.trim().isEmpty()) {
            return List.of();
        }
        return list("orderId = ?1 order by createdAt asc", orderId.trim());
    }

    public List<AssignmentHistory> findByAssignmentIdOrderByCreatedAtAsc(String assignmentId) {
        if (assignmentId == null || assignmentId.trim().isEmpty()) {
            return List.of();
        }
        return list("assignmentId = ?1 order by createdAt asc", assignmentId.trim());
    }

    public long deleteByOrderId(String orderId) {
        if (orderId == null || orderId.trim().isEmpty()) {
            return 0;
        }
        return delete("orderId", orderId.trim());
    }
}
