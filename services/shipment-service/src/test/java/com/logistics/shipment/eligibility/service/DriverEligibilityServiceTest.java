package com.logistics.shipment.eligibility.service;

import com.logistics.common.model.DriverAvailability;
import com.logistics.shipment.eligibility.client.DriverClient;
import com.logistics.shipment.eligibility.model.AssignmentCriteria;
import com.logistics.shipment.eligibility.model.EligibleDriver;
import com.logistics.shipment.eligibility.pipeline.BaseEligibilityFilter;
import com.logistics.shipment.eligibility.pipeline.DriverAvailabilityFilter;
import com.logistics.shipment.eligibility.pipeline.DriverEligibilityFilter;
import com.logistics.shipment.eligibility.pipeline.DriverEligibilityPipeline;
import com.logistics.shipment.eligibility.pipeline.DriverProximityFilter;
import com.logistics.shipment.eligibility.pipeline.DriverWorkloadFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DriverEligibilityServiceTest {

    @Mock
    private DriverClient driverClient;

    private DriverEligibilityPipeline pipeline;
    private DriverEligibilityService service;

    @BeforeEach
    void setUp() {
        pipeline = new DriverEligibilityPipeline(List.of(new BaseEligibilityFilter()));
        service = new DefaultDriverEligibilityService(driverClient, pipeline);
    }

    private AssignmentCriteria createCriteria(String orderId) {
        return AssignmentCriteria.builder()
                .orderId(orderId)
                .orderNumber("ORD-20261019-001")
                .pickupLocation("123 Lê Duẩn, Quận 1, TP.HCM")
                .deliveryAddress("456 Nguyễn Thị Minh Khai, Quận 3, TP.HCM")
                .serviceType("EXPRESS")
                .requiredVehicleType("MOTORBIKE")
                .totalAmount(BigDecimal.valueOf(120000.00))
                .build();
    }

    private EligibleDriver createDriver(String id, String name, String status, DriverAvailability availability) {
        EligibleDriver driver = new EligibleDriver(
                id, "user-" + id, name, "0901234567", "MOTORBIKE", "59A-12345", availability, status
        );
        return driver;
    }

    @Test
    @DisplayName("findEligibleDrivers: successfully retrieves and filters candidate drivers through the pipeline")
    void findEligibleDrivers_success() {
        AssignmentCriteria criteria = createCriteria("ord-100");

        EligibleDriver activeDriver1 = createDriver("drv-1", "Nguyễn Văn An", "ACTIVE", DriverAvailability.AVAILABLE);
        EligibleDriver activeDriver2 = createDriver("drv-2", "Trần Văn Bình", "ACTIVE", DriverAvailability.AVAILABLE);
        EligibleDriver inactiveDriver = createDriver("drv-3", "Lê Văn Cường", "SUSPENDED", DriverAvailability.AVAILABLE);
        EligibleDriver nullIdDriver = createDriver("  ", "Invalid Driver", "ACTIVE", DriverAvailability.AVAILABLE);

        when(driverClient.getCandidateDrivers(criteria))
                .thenReturn(List.of(activeDriver1, activeDriver2, inactiveDriver, nullIdDriver));

        List<EligibleDriver> eligible = service.findEligibleDrivers(criteria);

        assertThat(eligible).hasSize(2);
        assertThat(eligible).extracting(EligibleDriver::getDriverId)
                .containsExactlyInAnyOrder("drv-1", "drv-2");

        verify(driverClient).getCandidateDrivers(criteria);
    }

    @Test
    @DisplayName("findEligibleDrivers: returns empty list cleanly when client finds no drivers")
    void findEligibleDrivers_noDriversAvailable_returnsEmptyList() {
        AssignmentCriteria criteria = createCriteria("ord-200");
        when(driverClient.getCandidateDrivers(criteria)).thenReturn(Collections.emptyList());

        List<EligibleDriver> eligible = service.findEligibleDrivers(criteria);

        assertThat(eligible).isEmpty();
        verify(driverClient).getCandidateDrivers(criteria);
    }

    @Test
    @DisplayName("findEligibleDrivers: fail-safe handling returns empty list without exception when client fails")
    void findEligibleDrivers_clientThrowsException_handledSafely() {
        AssignmentCriteria criteria = createCriteria("ord-300");
        when(driverClient.getCandidateDrivers(criteria))
                .thenThrow(new RuntimeException("Simulated connection timeout to user-service"));

        List<EligibleDriver> eligible = service.findEligibleDrivers(criteria);

        assertThat(eligible).isEmpty();
        verify(driverClient).getCandidateDrivers(criteria);
    }

    @Test
    @DisplayName("findEligibleDrivers: returns empty list when criteria or orderId is null or empty")
    void findEligibleDrivers_invalidCriteria_returnsEmptyList() {
        assertThat(service.findEligibleDrivers(null)).isEmpty();

        AssignmentCriteria criteriaWithoutOrderId = new AssignmentCriteria();
        assertThat(service.findEligibleDrivers(criteriaWithoutOrderId)).isEmpty();

        criteriaWithoutOrderId.setOrderId("   ");
        assertThat(service.findEligibleDrivers(criteriaWithoutOrderId)).isEmpty();
    }

    @Test
    @DisplayName("Pipeline: executes filter stages in sequential order defined by getOrder()")
    void pipeline_stagesExecuteInOrder() {
        List<String> executionOrder = new ArrayList<>();

        DriverEligibilityFilter stageA = new DriverEligibilityFilter() {
            @Override
            public List<EligibleDriver> filter(List<EligibleDriver> candidates, AssignmentCriteria criteria) {
                executionOrder.add("stageA");
                return candidates;
            }

            @Override
            public int getOrder() {
                return 10;
            }
        };

        DriverEligibilityFilter stageB = new DriverEligibilityFilter() {
            @Override
            public List<EligibleDriver> filter(List<EligibleDriver> candidates, AssignmentCriteria criteria) {
                executionOrder.add("stageB");
                return candidates;
            }

            @Override
            public int getOrder() {
                return 5;
            }
        };

        DriverEligibilityPipeline customPipeline = new DriverEligibilityPipeline();
        customPipeline.addFilter(stageA).addFilter(stageB);

        EligibleDriver driver = createDriver("drv-custom", "Custom Driver", "ACTIVE", DriverAvailability.AVAILABLE);
        List<EligibleDriver> result = customPipeline.execute(List.of(driver), createCriteria("ord-pipeline"));

        assertThat(result).hasSize(1);
        assertThat(executionOrder).containsExactly("stageB", "stageA"); // stageB (5) runs before stageA (10)
    }

    @Test
    @DisplayName("Pipeline: halts early if a filter stage exhausts all candidates")
    void pipeline_haltsEarlyWhenPoolExhausted() {
        boolean[] stageRan = new boolean[]{false, false};

        DriverEligibilityFilter exhaustingStage = new DriverEligibilityFilter() {
            @Override
            public List<EligibleDriver> filter(List<EligibleDriver> candidates, AssignmentCriteria criteria) {
                stageRan[0] = true;
                return Collections.emptyList(); // Filter out all candidates
            }

            @Override
            public int getOrder() {
                return 1;
            }
        };

        DriverEligibilityFilter subsequentStage = new DriverEligibilityFilter() {
            @Override
            public List<EligibleDriver> filter(List<EligibleDriver> candidates, AssignmentCriteria criteria) {
                stageRan[1] = true;
                return candidates;
            }

            @Override
            public int getOrder() {
                return 2;
            }
        };

        DriverEligibilityPipeline pipelineWithStages = new DriverEligibilityPipeline();
        pipelineWithStages.addFilter(exhaustingStage).addFilter(subsequentStage);

        EligibleDriver driver = createDriver("drv-1", "Test Driver", "ACTIVE", DriverAvailability.AVAILABLE);
        List<EligibleDriver> result = pipelineWithStages.execute(List.of(driver), createCriteria("ord-exhaust"));

        assertThat(result).isEmpty();
        assertThat(stageRan[0]).isTrue();
        assertThat(stageRan[1]).isFalse(); // Second stage skipped because candidate pool was already empty
    }

    @Test
    @DisplayName("findEligibleDrivers with Base, Availability and Workload filters filters out unavailable and overloaded candidates")
    void findEligibleDrivers_withAvailabilityAndWorkloadFilters_filtersOutIneligibleCandidates() {
        DriverEligibilityPipeline multiStagePipeline = new DriverEligibilityPipeline(
                List.of(
                        new BaseEligibilityFilter(),
                        new DriverAvailabilityFilter(),
                        new DriverWorkloadFilter(null, 3, 2, true)
                )
        );
        DriverEligibilityService multiStageService = new DefaultDriverEligibilityService(driverClient, multiStagePipeline);

        AssignmentCriteria criteria = createCriteria("ord-multi-stage");
        EligibleDriver availableDriver = createDriver("drv-avail", "Driver Avail", "ACTIVE", DriverAvailability.AVAILABLE);
        availableDriver.setCurrentWorkload(1);

        EligibleDriver unavailableDriver = createDriver("drv-unavail", "Driver Unavail", "ACTIVE", DriverAvailability.UNAVAILABLE);
        unavailableDriver.setCurrentWorkload(0);

        EligibleDriver inactiveDriver = createDriver("drv-inactive", "Driver Inactive", "INACTIVE", DriverAvailability.AVAILABLE);
        inactiveDriver.setCurrentWorkload(0);

        EligibleDriver overloadedDriver = createDriver("drv-overload", "Driver Overload", "ACTIVE", DriverAvailability.AVAILABLE);
        overloadedDriver.setCurrentWorkload(3); // at max capacity 3 with strict rejection

        when(driverClient.getCandidateDrivers(criteria))
                .thenReturn(List.of(availableDriver, unavailableDriver, inactiveDriver, overloadedDriver));

        List<EligibleDriver> result = multiStageService.findEligibleDrivers(criteria);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getDriverId()).isEqualTo("drv-avail");
    }

    @Test
    @DisplayName("Full 4-stage pipeline: Base -> Availability -> Workload -> Proximity executes seamlessly")
    void findEligibleDrivers_fullFourStagePipeline_calculatesProximityForEligibleCandidates() {
        DriverEligibilityPipeline fourStagePipeline = new DriverEligibilityPipeline(
                List.of(
                        new BaseEligibilityFilter(),
                        new DriverAvailabilityFilter(),
                        new DriverWorkloadFilter(null, 3, 2, false),
                        new DriverProximityFilter(30)
                )
        );
        DriverEligibilityService fourStageService = new DefaultDriverEligibilityService(driverClient, fourStagePipeline);

        AssignmentCriteria criteria = AssignmentCriteria.builder()
                .orderId("ord-4-stages")
                .pickupLocation("Opera House")
                .pickupCoordinates(10.7766, 106.7032)
                .serviceType("EXPRESS")
                .build();

        EligibleDriver d1 = createDriver("drv-1", "D1", "ACTIVE", DriverAvailability.AVAILABLE);
        d1.setCurrentWorkload(0);
        d1.setCurrentLatitude(10.7798);
        d1.setCurrentLongitude(106.6990);

        EligibleDriver dUnavailable = createDriver("drv-unavail", "D Unavail", "ACTIVE", DriverAvailability.UNAVAILABLE);
        dUnavailable.setCurrentLatitude(10.7766);
        dUnavailable.setCurrentLongitude(106.7032);

        when(driverClient.getCandidateDrivers(criteria))
                .thenReturn(List.of(d1, dUnavailable));

        List<EligibleDriver> result = fourStageService.findEligibleDrivers(criteria);

        assertThat(result).hasSize(1);
        EligibleDriver finalCandidate = result.get(0);
        assertThat(finalCandidate.getDriverId()).isEqualTo("drv-1");
        assertThat(finalCandidate.getDistanceKm()).isNotNull();
        assertThat(finalCandidate.getDistanceKm()).isGreaterThan(0.0);
        assertThat(finalCandidate.getMetadata()).containsEntry("distanceUnit", "KM");
    }
}


