package com.logistics.user.repository;

import com.logistics.user.entity.DriverProfile;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Optional;

@ApplicationScoped
public class DriverProfileRepository implements PanacheRepositoryBase<DriverProfile, String> {

    public Optional<DriverProfile> findByUserId(String userId) {
        return find("userId", userId).firstResultOptional();
    }

    public boolean existsByUserId(String userId) {
        return count("userId", userId) > 0;
    }

    public boolean existsByLicenseNumber(String licenseNumber) {
        return count("licenseNumber", licenseNumber) > 0;
    }

    public boolean existsByLicenseNumberAndIdNot(String licenseNumber, String id) {
        return count("licenseNumber = ?1 and id != ?2", licenseNumber, id) > 0;
    }
}
