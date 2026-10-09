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

    public boolean existsByOrderNumber(String orderNumber) {
        return count("orderNumber", orderNumber) > 0;
    }
}
