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
        return find("orderId = ?1 and status in (?2, ?3)", orderId.trim(), AssignmentStatus.UNASSIGNED, AssignmentStatus.FAILED).firstResultOptional();
    }

    public com.logistics.shipment.dto.PagedResponse<Assignment> findFailed(int page, int size, String sort) {
        int pageIndex = Math.max(0, page);
        int pageSize = size > 0 ? size : 10;

        String orderBy;
        if ("oldest_first".equalsIgnoreCase(sort) || "asc".equalsIgnoreCase(sort)) {
            orderBy = "order by coalesce(failedAt, updatedAt, createdAt) asc, createdAt asc";
        } else {
            // default: newest_first for failed orders so operations sees recent failures first
            orderBy = "order by coalesce(failedAt, updatedAt, createdAt) desc, createdAt desc";
        }

        io.quarkus.panache.common.Page panachePage = io.quarkus.panache.common.Page.of(pageIndex, pageSize);
        var query = find("status = ?1 " + orderBy, AssignmentStatus.FAILED).page(panachePage);
        long totalElements = query.count();
        java.util.List<Assignment> content = query.list();

        return com.logistics.shipment.dto.PagedResponse.of(content, pageIndex, pageSize, totalElements);
    }

    public Optional<Assignment> findFailedByOrderId(String orderId) {
        if (orderId == null || orderId.trim().isEmpty()) {
            return Optional.empty();
        }
        return find("orderId = ?1 and status = ?2", orderId.trim(), AssignmentStatus.FAILED).firstResultOptional();
    }

    public long countActiveAssignmentsByDriverId(String driverId) {
        if (driverId == null || driverId.trim().isEmpty()) {
            return 0;
        }
        return count("driverId = ?1 and status = ?2", driverId.trim(), AssignmentStatus.ASSIGNED);
    }

    public com.logistics.shipment.dto.PagedResponse<Assignment> findAssignedOrdersByDriver(
            String driverId,
            String altDriverId,
            AssignmentStatus status,
            int page,
            int size) {
        int pageIndex = Math.max(0, page);
        int pageSize = (size <= 0 || size > 100) ? 10 : size;

        StringBuilder queryStr = new StringBuilder();
        io.quarkus.panache.common.Parameters params = new io.quarkus.panache.common.Parameters();

        String trimmedDriverId = driverId != null ? driverId.trim() : "";
        String trimmedAltId = altDriverId != null ? altDriverId.trim() : "";

        if (!trimmedAltId.isEmpty() && !trimmedAltId.equalsIgnoreCase(trimmedDriverId)) {
            queryStr.append("(driverId = :driverId or driverId = :altDriverId)");
            params.and("driverId", trimmedDriverId).and("altDriverId", trimmedAltId);
        } else {
            queryStr.append("driverId = :driverId");
            params.and("driverId", trimmedDriverId);
        }

        if (status != null) {
            queryStr.append(" and status = :status");
            params.and("status", status);
        }

        queryStr.append(" order by coalesce(assignedAt, createdAt) desc, createdAt desc");

        io.quarkus.panache.common.Page panachePage = io.quarkus.panache.common.Page.of(pageIndex, pageSize);
        var panacheQuery = find(queryStr.toString(), params).page(panachePage);
        long totalElements = panacheQuery.count();
        java.util.List<Assignment> content = panacheQuery.list();

        return com.logistics.shipment.dto.PagedResponse.of(content, pageIndex, pageSize, totalElements);
    }

    public java.util.List<Assignment> findAllActiveAssignments() {
        return find("status in (?1, ?2, ?3)",
                AssignmentStatus.ASSIGNED, AssignmentStatus.PICKED_UP, AssignmentStatus.IN_TRANSIT).list();
    }

    public Optional<Assignment> findActiveByDriverId(String driverId, String userId) {
        if ((driverId == null || driverId.trim().isEmpty()) && (userId == null || userId.trim().isEmpty())) {
            return Optional.empty();
        }
        String dId = driverId != null ? driverId.trim() : "";
        String uId = userId != null ? userId.trim() : "";
        return find("(driverId = ?1 or driverId = ?2) and status in (?3, ?4, ?5) order by assignedAt desc, createdAt desc",
                dId, uId,
                AssignmentStatus.ASSIGNED, AssignmentStatus.PICKED_UP, AssignmentStatus.IN_TRANSIT)
                .firstResultOptional();
    }
}

