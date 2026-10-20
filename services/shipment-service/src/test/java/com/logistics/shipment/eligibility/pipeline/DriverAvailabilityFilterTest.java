package com.logistics.shipment.eligibility.pipeline;

import com.logistics.common.model.DriverAvailability;
import com.logistics.shipment.eligibility.model.AssignmentCriteria;
import com.logistics.shipment.eligibility.model.EligibleDriver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DriverAvailabilityFilterTest {

    private DriverAvailabilityFilter filter;
    private AssignmentCriteria criteria;

    @BeforeEach
    void setUp() {
        filter = new DriverAvailabilityFilter();
        criteria = AssignmentCriteria.builder()
                .orderId("ord-avail-test-1")
                .orderNumber("ORD-100")
                .pickupLocation("123 Le Loi, District 1, HCMC")
                .deliveryAddress("456 Nguyen Trai, District 5, HCMC")
                .serviceType("EXPRESS")
                .totalAmount(BigDecimal.valueOf(120000))
                .build();
    }

    private EligibleDriver createDriver(String id, String name, String status,
                                        DriverAvailability availability) {
        return new EligibleDriver(
                id,
                "user-" + id,
                name,
                "0901234567",
                "MOTORCYCLE",
                "59A-12345",
                availability,
                status
        );
    }

    @Test
    @DisplayName("Empty or null candidate list returns safe empty list without error")
    void filter_emptyOrNullList_returnsEmptyList() {
        assertThat(filter.filter(null, criteria)).isEmpty();
        assertThat(filter.filter(Collections.emptyList(), criteria)).isEmpty();
    }

    @Test
    @DisplayName("All eligible and available drivers are retained")
    void filter_allDriversAvailable_keepsAll() {
        EligibleDriver d1 = createDriver("drv-1", "Nguyen Van A", "ACTIVE", DriverAvailability.AVAILABLE);
        EligibleDriver d2 = createDriver("drv-2", "Tran Thi B", "ACTIVE", DriverAvailability.AVAILABLE);
        EligibleDriver d3 = createDriver("drv-3", "Le Van C", "ACTIVE", DriverAvailability.AVAILABLE);

        List<EligibleDriver> result = filter.filter(List.of(d1, d2, d3), criteria);

        assertThat(result).hasSize(3);
        assertThat(result).extracting(EligibleDriver::getDriverId)
                .containsExactly("drv-1", "drv-2", "drv-3");
    }

    @Test
    @DisplayName("Mixed candidate pool: only active and available drivers are kept")
    void filter_mixedAvailability_keepsOnlyEligibleDrivers() {
        EligibleDriver d1 = createDriver("drv-1", "Nguyen Van A", "ACTIVE", DriverAvailability.AVAILABLE); // OK
        EligibleDriver d2 = createDriver("drv-2", "Tran Thi B", "ACTIVE", DriverAvailability.UNAVAILABLE); // UNAVAILABLE
        EligibleDriver d3 = createDriver("drv-3", "Le Van C", "INACTIVE", DriverAvailability.AVAILABLE); // INACTIVE status
        EligibleDriver d4 = createDriver("drv-4", "Pham Van D", "SUSPENDED", DriverAvailability.AVAILABLE); // SUSPENDED status
        EligibleDriver d5 = createDriver("drv-5", "Hoang Van E", "ACTIVE", DriverAvailability.AVAILABLE); // OK

        List<EligibleDriver> result = filter.filter(List.of(d1, d2, d3, d4, d5), criteria);

        assertThat(result).hasSize(2);
        assertThat(result).extracting(EligibleDriver::getDriverId)
                .containsExactly("drv-1", "drv-5");
    }

    @Test
    @DisplayName("All drivers unavailable or ineligible returns empty list safely")
    void filter_allDriversUnavailable_returnsEmptyList() {
        EligibleDriver d1 = createDriver("drv-1", "Driver 1", "ACTIVE", DriverAvailability.UNAVAILABLE);
        EligibleDriver d2 = createDriver("drv-2", "Driver 2", "OFFLINE", DriverAvailability.AVAILABLE);
        EligibleDriver d3 = createDriver("drv-3", "Driver 3", "SUSPENDED", DriverAvailability.AVAILABLE);
        EligibleDriver d4 = createDriver("drv-4", "Driver 4", "ACTIVE", null);
        EligibleDriver d5 = createDriver("drv-5", "Driver 5", "DEACTIVATED", DriverAvailability.AVAILABLE);

        List<EligibleDriver> result = filter.filter(List.of(d1, d2, d3, d4, d5), criteria);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Boundary tests for operational status: case-insensitivity, null, blank, and invalid statuses")
    void filter_boundaryConditions_operationalStatus() {
        EligibleDriver dLower = createDriver("drv-lower", "D Lower", "active", DriverAvailability.AVAILABLE);
        EligibleDriver dUpper = createDriver("drv-upper", "D Upper", "ACTIVE", DriverAvailability.AVAILABLE);
        EligibleDriver dMixed = createDriver("drv-mixed", "D Mixed", "  Active  ", DriverAvailability.AVAILABLE);
        EligibleDriver dNull = createDriver("drv-null", "D Null", null, DriverAvailability.AVAILABLE);
        EligibleDriver dEmpty = createDriver("drv-empty", "D Empty", "   ", DriverAvailability.AVAILABLE);
        EligibleDriver dSuspended = createDriver("drv-susp", "D Susp", "SUSPENDED", DriverAvailability.AVAILABLE);
        EligibleDriver dOffline = createDriver("drv-off", "D Off", "OFFLINE", DriverAvailability.AVAILABLE);

        List<EligibleDriver> result = filter.filter(
                List.of(dLower, dUpper, dMixed, dNull, dEmpty, dSuspended, dOffline), criteria);

        assertThat(result).hasSize(3);
        assertThat(result).extracting(EligibleDriver::getDriverId)
                .containsExactly("drv-lower", "drv-upper", "drv-mixed");
    }

    @Test
    @DisplayName("Boundary tests for DriverAvailability: AVAILABLE vs UNAVAILABLE vs null")
    void filter_boundaryConditions_driverAvailability() {
        EligibleDriver dAvailable = createDriver("d-avail", "Avail", "ACTIVE", DriverAvailability.AVAILABLE);
        EligibleDriver dUnavailable = createDriver("d-unavail", "Unavail", "ACTIVE", DriverAvailability.UNAVAILABLE);
        EligibleDriver dNullAvail = createDriver("d-null", "NullAvail", "ACTIVE", null);

        List<EligibleDriver> result = filter.filter(List.of(dAvailable, dUnavailable, dNullAvail), criteria);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getDriverId()).isEqualTo("d-avail");
    }

    @Test
    @DisplayName("Metadata online and acceptingOrders flags are strictly verified")
    void filter_metadataReadinessFlags() {
        EligibleDriver dOnlineFalse = createDriver("d-off", "Offline", "ACTIVE", DriverAvailability.AVAILABLE);
        dOnlineFalse.addMetadata("online", false);

        EligibleDriver dIsOnlineFalse = createDriver("d-isoff", "IsOffline", "ACTIVE", DriverAvailability.AVAILABLE);
        dIsOnlineFalse.addMetadata("isOnline", "false");

        EligibleDriver dReadyFalse = createDriver("d-notready", "NotReady", "ACTIVE", DriverAvailability.AVAILABLE);
        dReadyFalse.addMetadata("ready", false);

        EligibleDriver dNotAccepting = createDriver("d-notacc", "NotAccepting", "ACTIVE", DriverAvailability.AVAILABLE);
        dNotAccepting.addMetadata("acceptingOrders", false);

        EligibleDriver dFullyReady = createDriver("d-ready", "Ready", "ACTIVE", DriverAvailability.AVAILABLE);
        dFullyReady.addMetadata("online", true);
        dFullyReady.addMetadata("acceptingOrders", true);

        List<EligibleDriver> result = filter.filter(
                List.of(dOnlineFalse, dIsOnlineFalse, dReadyFalse, dNotAccepting, dFullyReady), criteria);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getDriverId()).isEqualTo("d-ready");
    }

    @Test
    @DisplayName("Candidate filtering does not mutate unselected drivers or original list")
    void filter_doesNotMutateOriginalCollection() {
        EligibleDriver d1 = createDriver("drv-1", "D1", "ACTIVE", DriverAvailability.AVAILABLE);
        EligibleDriver d2 = createDriver("drv-2", "D2", "ACTIVE", DriverAvailability.UNAVAILABLE);
        List<EligibleDriver> originalList = new ArrayList<>(List.of(d1, d2));

        List<EligibleDriver> result = filter.filter(originalList, criteria);

        assertThat(result).hasSize(1);
        assertThat(originalList).hasSize(2);
        // Verify d2 attributes remain unchanged
        assertThat(d2.getAvailability()).isEqualTo(DriverAvailability.UNAVAILABLE);
        assertThat(d2.getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("Filter order is 10, running after BaseEligibilityFilter (order 0)")
    void filter_orderAndName() {
        assertThat(filter.getOrder()).isEqualTo(10);
        assertThat(filter.getName()).isEqualTo("DriverAvailabilityFilter");
    }
}
