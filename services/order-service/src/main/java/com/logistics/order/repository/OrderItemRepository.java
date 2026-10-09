package com.logistics.order.repository;

import com.logistics.order.entity.OrderItem;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class OrderItemRepository implements PanacheRepositoryBase<OrderItem, String> {
}
