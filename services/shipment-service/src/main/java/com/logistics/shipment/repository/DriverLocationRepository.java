package com.logistics.shipment.repository;

import com.logistics.shipment.entity.DriverLocation;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Optional;

@ApplicationScoped
public class DriverLocationRepository implements PanacheRepositoryBase<DriverLocation, String> {

    public Optional<DriverLocation> findByDriverId(String driverId) {
        if (driverId == null || driverId.trim().isEmpty()) {
            return Optional.empty();
        }
        return find("driverId", driverId.trim()).firstResultOptional();
    }

    public Optional<DriverLocation> findByUserId(String userId) {
        if (userId == null || userId.trim().isEmpty()) {
            return Optional.empty();
        }
        return find("userId", userId.trim()).firstResultOptional();
    }

    public long deleteByDriverId(String driverId) {
        if (driverId == null || driverId.trim().isEmpty()) {
            return 0;
        }
        return delete("driverId", driverId.trim());
    }
}
