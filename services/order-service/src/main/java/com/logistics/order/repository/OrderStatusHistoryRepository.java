package com.logistics.order.repository;

import com.logistics.order.entity.OrderStatusHistory;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

@ApplicationScoped
public class OrderStatusHistoryRepository implements PanacheRepositoryBase<OrderStatusHistory, String> {

    public List<OrderStatusHistory> findByOrderId(String orderId) {
        return find("order.id = ?1 order by createdAt asc", orderId).list();
    }

    public List<OrderStatusHistory> findByOrderNumber(String orderNumber) {
        return find("orderNumber = ?1 order by createdAt asc", orderNumber).list();
    }
}
