package com.logistics.shipment.eligibility.service;

import com.logistics.shipment.eligibility.client.DriverClient;
import com.logistics.shipment.eligibility.model.AssignmentCriteria;
import com.logistics.shipment.eligibility.model.EligibleDriver;
import com.logistics.shipment.eligibility.pipeline.DriverEligibilityPipeline;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;

@ApplicationScoped
@DefaultBean
public class DefaultDriverEligibilityService implements DriverEligibilityService {

    private static final Logger log = LoggerFactory.getLogger(DefaultDriverEligibilityService.class);

    @Inject
    DriverClient driverClient;

    @Inject
    DriverEligibilityPipeline pipeline;

    public DefaultDriverEligibilityService() {
    }

    public DefaultDriverEligibilityService(DriverClient driverClient, DriverEligibilityPipeline pipeline) {
        this.driverClient = driverClient;
        this.pipeline = pipeline;
    }

    @Override
    public List<EligibleDriver> findEligibleDrivers(AssignmentCriteria criteria) {
        if (criteria == null || criteria.getOrderId() == null || criteria.getOrderId().trim().isEmpty()) {
            log.warn("Invalid assignment criteria: criteria or orderId is null/empty. Returning empty candidate list.");
            return Collections.emptyList();
        }

        log.info("Evaluating driver eligibility for orderId='{}', pickupLocation='{}', serviceType='{}'",
                criteria.getOrderId(), criteria.getPickupLocation(), criteria.getServiceType());

        try {
            // 1. Fetch initial candidate pool via client interface (respecting service boundary)
            List<EligibleDriver> initialCandidates = driverClient.getCandidateDrivers(criteria);
            if (initialCandidates == null || initialCandidates.isEmpty()) {
                log.info("No initial driver candidates available from provider for orderId '{}'. Returning empty list.",
                        criteria.getOrderId());
                return Collections.emptyList();
            }

            // 2. Execute eligibility pipeline stages
            List<EligibleDriver> eligibleDrivers = pipeline.execute(initialCandidates, criteria);

            if (eligibleDrivers.isEmpty()) {
                log.info("No eligible drivers matched after pipeline execution for orderId '{}'.", criteria.getOrderId());
                return Collections.emptyList();
            }

            log.info("Successfully identified {} eligible candidate drivers for orderId '{}'",
                    eligibleDrivers.size(), criteria.getOrderId());
            return eligibleDrivers;

        } catch (Exception e) {
            log.error("Unexpected error during driver eligibility evaluation for orderId '{}': {}",
                    criteria.getOrderId(), e.getMessage(), e);
            // Fail-safe handling: log error and return empty list cleanly without throwing unhandled exceptions
            return Collections.emptyList();
        }
    }
}
