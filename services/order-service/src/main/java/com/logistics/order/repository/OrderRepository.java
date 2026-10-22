package com.logistics.order.repository;

import com.logistics.order.entity.Order;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Optional;

@ApplicationScoped
public class OrderRepository implements PanacheRepositoryBase<Order, String> {

    public Optional<Order> findByOrderNumber(String orderNumber) {
        return find("orderNumber", orderNumber).firstResultOptional();
    }

    public Optional<Order> findByIdOrOrderNumber(String identifier) {
        return find("id = ?1 or orderNumber = ?1", identifier).firstResultOptional();
    }

    public boolean existsByOrderNumber(String orderNumber) {
        return count("orderNumber", orderNumber) > 0;
    }

    public long countByStatus(com.logistics.common.model.OrderStatus status) {
        if (status == null) return 0;
        return count("status", status);
    }

    public long countCreatedSince(java.time.Instant since) {
        if (since == null) return 0;
        return count("createdAt >= ?1", since);
    }

    public java.util.List<Order> findRecentOrders(int limit) {
        return find("order by createdAt desc").page(0, Math.max(1, limit)).list();
    }
}
