package com.logistics.shipment.repository;

import com.logistics.shipment.entity.Assignment;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Optional;

@ApplicationScoped
public class AssignmentRepository implements PanacheRepositoryBase<Assignment, String> {

    public Optional<Assignment> findByOrderId(String orderId) {
        return find("orderId", orderId).firstResultOptional();
    }
}
