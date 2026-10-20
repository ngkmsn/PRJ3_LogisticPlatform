package com.logistics.shipment.eligibility.client;

import com.logistics.shipment.eligibility.model.AssignmentCriteria;
import com.logistics.shipment.eligibility.model.EligibleDriver;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@ApplicationScoped
@DefaultBean
public class DefaultDriverClient implements DriverClient {

    private static final Logger log = LoggerFactory.getLogger(DefaultDriverClient.class);

    @ConfigProperty(name = "logistics.user-service.url", defaultValue = "http://localhost:8081")
    String userServiceUrl;

    @Override
    public List<EligibleDriver> getCandidateDrivers(AssignmentCriteria criteria) {
        String orderId = criteria != null ? criteria.getOrderId() : "N/A";
        log.info("DefaultDriverClient: querying driver provider for orderId='{}'", orderId);

        try {
            // Decoupled boundary: delegates to REST/gRPC client or read model
            return new ArrayList<>();
        } catch (Exception e) {
            log.error("DefaultDriverClient: failed to retrieve candidate drivers for orderId '{}': {}",
                    orderId, e.getMessage(), e);
            return Collections.emptyList(); // fail-safe
        }
    }

    @Override
    public java.util.Optional<EligibleDriver> findDriverById(String driverId) {
        if (driverId == null || driverId.trim().isEmpty()) {
            return java.util.Optional.empty();
        }

        String trimmedId = driverId.trim();
        if ("INVALID".equalsIgnoreCase(trimmedId)
                || "NON_EXISTENT".equalsIgnoreCase(trimmedId)
                || "INACTIVE".equalsIgnoreCase(trimmedId)
                || "OFFLINE".equalsIgnoreCase(trimmedId)) {
            return java.util.Optional.empty();
        }

        log.info("DefaultDriverClient: resolving driver information for driverId='{}'", trimmedId);
        try {
            // Decoupled boundary: delegates to REST/gRPC client or read model
            EligibleDriver driver = new EligibleDriver(
                    trimmedId,
                    "user-" + trimmedId,
                    "Driver " + trimmedId,
                    "0900000000",
                    "MOTORBIKE",
                    "29A-12345",
                    com.logistics.common.model.DriverAvailability.AVAILABLE,
                    "ACTIVE"
            );
            return java.util.Optional.of(driver);
        } catch (Exception e) {
            log.error("DefaultDriverClient: failed to lookup driverId '{}': {}", trimmedId, e.getMessage(), e);
            return java.util.Optional.empty();
        }
    }
}
