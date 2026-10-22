package com.logistics.shipment.repository;

import com.logistics.common.dto.ActionableOrderDto;
import com.logistics.common.dto.ActionableOrdersPageDto;
import com.logistics.shipment.entity.Assignment;
import com.logistics.shipment.model.AssignmentStatus;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import io.quarkus.panache.common.Parameters;
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

    public ActionableOrdersPageDto findActionableOrders(String category, String status, String search, int page, int size, String sort) {
        int pageIndex = Math.max(0, page);
        int pageSize = size > 0 ? size : 10;

        StringBuilder queryStr = new StringBuilder();
        Parameters params = new Parameters();

        // 1. Filter by status / category
        AssignmentStatus targetStatus = null;
        if (status != null && !status.trim().isEmpty() && !"ALL".equalsIgnoreCase(status.trim())) {
            try {
                targetStatus = AssignmentStatus.valueOf(status.trim().toUpperCase());
            } catch (IllegalArgumentException ignored) {}
        } else if (category != null && !category.trim().isEmpty() && !"ALL".equalsIgnoreCase(category.trim())) {
            try {
                targetStatus = AssignmentStatus.valueOf(category.trim().toUpperCase());
            } catch (IllegalArgumentException ignored) {}
        }

        if (targetStatus == AssignmentStatus.UNASSIGNED) {
            queryStr.append("status = :status");
            params.and("status", AssignmentStatus.UNASSIGNED);
        } else if (targetStatus == AssignmentStatus.FAILED) {
            queryStr.append("status = :status");
            params.and("status", AssignmentStatus.FAILED);
        } else {
            queryStr.append("status in (:st1, :st2)");
            params.and("st1", AssignmentStatus.UNASSIGNED).and("st2", AssignmentStatus.FAILED);
        }

        // 2. Search filter
        if (search != null && !search.trim().isEmpty()) {
            String searchPattern = "%" + search.trim().toLowerCase() + "%";
            queryStr.append(" and (lower(orderId) like :search or lower(orderNumber) like :search or lower(deliveryAddress) like :search or lower(recipientName) like :search or lower(failureReason) like :search or lower(unassignedReason) like :search or lower(driverId) like :search)");
            params.and("search", searchPattern);
        }

        // 3. Sorting
        if ("oldest_first".equalsIgnoreCase(sort) || "asc".equalsIgnoreCase(sort)) {
            queryStr.append(" order by coalesce(unassignedAt, failedAt, createdAt) asc, createdAt asc");
        } else {
            queryStr.append(" order by coalesce(failedAt, unassignedAt, updatedAt, createdAt) desc, createdAt desc");
        }

        io.quarkus.panache.common.Page panachePage = io.quarkus.panache.common.Page.of(pageIndex, pageSize);
        var panacheQuery = find(queryStr.toString(), params).page(panachePage);
        long totalElements = panacheQuery.count();
        java.util.List<Assignment> assignments = panacheQuery.list();

        java.util.List<ActionableOrderDto> dtos = assignments.stream()
                .map(AssignmentRepository::toActionableDto)
                .toList();

        // 4. Global counters for Operations UI badges
        long unassignedCount = count("status = ?1", AssignmentStatus.UNASSIGNED);
        long failedCount = count("status = ?1", AssignmentStatus.FAILED);
        long totalActionable = unassignedCount + failedCount;

        return ActionableOrdersPageDto.of(dtos, pageIndex, pageSize, totalElements,
                totalActionable, unassignedCount, failedCount);
    }

    public Optional<Assignment> findActionableByOrderId(String orderId) {
        if (orderId == null || orderId.trim().isEmpty()) {
            return Optional.empty();
        }
        return find("orderId = ?1 and status in (?2, ?3)",
                orderId.trim(), AssignmentStatus.UNASSIGNED, AssignmentStatus.FAILED)
                .firstResultOptional();
    }

    public static ActionableOrderDto toActionableDto(Assignment a) {
        if (a == null) return null;
        ActionableOrderDto dto = new ActionableOrderDto();
        dto.setOrderId(a.getOrderId());
        dto.setOrderNumber(a.getOrderNumber());
        dto.setStatus(a.getStatus() != null ? a.getStatus().name() : null);
        dto.setCustomerId(a.getCustomerId());
        dto.setRecipientName(a.getRecipientName());
        dto.setRecipientPhone(a.getRecipientPhone());
        dto.setDeliveryAddress(a.getDeliveryAddress());
        dto.setTotalAmount(a.getTotalAmount());
        dto.setCreatedAt(a.getCreatedAt());
        dto.setUpdatedAt(a.getUpdatedAt());
        dto.setDriverId(a.getDriverId());
        dto.setPreviousDriverId(a.getPreviousDriverId());
        dto.setAssignedAt(a.getAssignedAt());
        dto.setAssignmentSource(a.getAssignmentSource());
        dto.setAssignedBy(a.getAssignedBy());
        dto.setUnassignedReason(a.getUnassignedReason());
        dto.setUnassignedAt(a.getUnassignedAt());
        dto.setFailedAt(a.getFailedAt());
        dto.setFailureReason(a.getFailureReason());
        dto.setFailureNotes(a.getFailureNotes());
        return dto;
    }
}

