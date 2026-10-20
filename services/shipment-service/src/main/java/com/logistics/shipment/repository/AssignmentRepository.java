package com.logistics.shipment.repository;

import com.logistics.shipment.entity.Assignment;
import com.logistics.shipment.model.AssignmentStatus;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Optional;

@ApplicationScoped
public class AssignmentRepository implements PanacheRepositoryBase<Assignment, String> {

    public Optional<Assignment> findByOrderId(String orderId) {
        return find("orderId", orderId).firstResultOptional();
    }

    public Optional<Assignment> findActiveAssignmentByOrderId(String orderId) {
        if (orderId == null || orderId.trim().isEmpty()) {
            return Optional.empty();
        }
        return find("orderId = ?1 and status in (?2, ?3, ?4, ?5)",
                orderId.trim(),
                AssignmentStatus.PENDING,
                AssignmentStatus.SEARCHING_DRIVER,
                AssignmentStatus.ASSIGNED,
                AssignmentStatus.UNASSIGNED
        ).firstResultOptional();
    }

    public boolean hasActiveAssignmentForOrder(String orderId) {
        return findActiveAssignmentByOrderId(orderId).isPresent();
    }

    public java.util.List<Assignment> findUnassignedAssignments() {
        return find("status = ?1 order by unassignedAt desc, createdAt desc", AssignmentStatus.UNASSIGNED).list();
    }

    public com.logistics.shipment.dto.PagedResponse<Assignment> findUnassigned(AssignmentStatus status, int page, int size, String sort) {
        AssignmentStatus targetStatus = status != null ? status : AssignmentStatus.UNASSIGNED;
        int pageIndex = Math.max(0, page);
        int pageSize = size > 0 ? size : 10;

        String orderBy;
        if ("newest_first".equalsIgnoreCase(sort) || "desc".equalsIgnoreCase(sort) || "createdAt_desc".equalsIgnoreCase(sort)) {
            orderBy = "order by coalesce(unassignedAt, createdAt) desc, createdAt desc";
        } else {
            // default: oldest_first (orders waiting the longest)
            orderBy = "order by coalesce(unassignedAt, createdAt) asc, createdAt asc";
        }

        io.quarkus.panache.common.Page panachePage = io.quarkus.panache.common.Page.of(pageIndex, pageSize);
        var query = find("status = ?1 " + orderBy, targetStatus).page(panachePage);
        long totalElements = query.count();
        java.util.List<Assignment> content = query.list();

        return com.logistics.shipment.dto.PagedResponse.of(content, pageIndex, pageSize, totalElements);
    }

    public Optional<Assignment> findUnassignedByOrderId(String orderId) {
        if (orderId == null || orderId.trim().isEmpty()) {
            return Optional.empty();
        }
        return find("orderId = ?1 and status = ?2", orderId.trim(), AssignmentStatus.UNASSIGNED).firstResultOptional();
    }

    public long countActiveAssignmentsByDriverId(String driverId) {
        if (driverId == null || driverId.trim().isEmpty()) {
            return 0;
        }
        return count("driverId = ?1 and status = ?2", driverId.trim(), AssignmentStatus.ASSIGNED);
    }
}

