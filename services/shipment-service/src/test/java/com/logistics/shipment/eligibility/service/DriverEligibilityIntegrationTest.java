package com.logistics.shipment.eligibility.service;

import com.logistics.shipment.eligibility.model.AssignmentCriteria;
import com.logistics.shipment.eligibility.model.EligibleDriver;
import com.logistics.shipment.eligibility.pipeline.DriverEligibilityPipeline;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
class DriverEligibilityIntegrationTest {

    @Inject
    DriverEligibilityService driverEligibilityService;

    @Inject
    DriverEligibilityPipeline pipeline;

    @Test
    @DisplayName("CDI context wires DriverEligibilityService and pipeline with registered filters")
    void cdiContext_wiresEligibilityServiceAndPipeline() {
        assertThat(driverEligibilityService).isNotNull();
        assertThat(pipeline).isNotNull();
        assertThat(pipeline.getFilters()).hasSizeGreaterThanOrEqualTo(4);
        assertThat(pipeline.getFilters())
                .extracting(filter -> filter.getClass().getSimpleName())
                .anyMatch(name -> name.contains("BaseEligibilityFilter"))
                .anyMatch(name -> name.contains("DriverAvailabilityFilter"))
                .anyMatch(name -> name.contains("DriverWorkloadFilter"))
                .anyMatch(name -> name.contains("DriverProximityFilter"));
    }

    @Test
    @DisplayName("findEligibleDrivers handles assignment request safely via default client")
    void findEligibleDrivers_defaultClientIntegration() {
        AssignmentCriteria criteria = AssignmentCriteria.builder()
                .orderId("ord-int-test-01")
                .orderNumber("ORD-INT-001")
                .pickupLocation("District 1, HCMC")
                .deliveryAddress("District 7, HCMC")
                .serviceType("STANDARD")
                .totalAmount(BigDecimal.valueOf(50000.00))
                .build();

        List<EligibleDriver> result = driverEligibilityService.findEligibleDrivers(criteria);

        // Default client returns empty list; pipeline and service execute safely without exception
        assertThat(result).isNotNull();
    }
}
