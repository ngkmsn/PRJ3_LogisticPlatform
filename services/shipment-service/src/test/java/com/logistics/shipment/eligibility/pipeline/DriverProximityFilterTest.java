package com.logistics.shipment.eligibility.pipeline;

import com.logistics.common.model.DriverAvailability;
import com.logistics.shipment.eligibility.model.AssignmentCriteria;
import com.logistics.shipment.eligibility.model.EligibleDriver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

class DriverProximityFilterTest {

    private DriverProximityFilter filter;
    private AssignmentCriteria criteriaWithCoords;

    // Pickup location: Saigon Opera House (10.7766, 106.7032)
    private static final double PICKUP_LAT = 10.7766;
    private static final double PICKUP_LON = 106.7032;

    @BeforeEach
    void setUp() {
        filter = new DriverProximityFilter(30); // 30 minutes stale threshold
        criteriaWithCoords = AssignmentCriteria.builder()
                .orderId("ord-prox-01")
                .orderNumber("ORD-PROX-001")
                .pickupLocation("Saigon Opera House, District 1, HCMC")
                .pickupCoordinates(PICKUP_LAT, PICKUP_LON)
                .deliveryAddress("District 7, HCMC")
                .serviceType("EXPRESS")
                .totalAmount(BigDecimal.valueOf(100000))
                .build();
    }

    private EligibleDriver createDriverWithLocation(String id, String name, Double lat, Double lon, Instant timestamp) {
        return new EligibleDriver(
                id,
                "user-" + id,
                name,
                "0901234567",
                "MOTORCYCLE",
                "59A-12345",
                DriverAvailability.AVAILABLE,
                "ACTIVE",
                lat,
                lon,
                timestamp
        );
    }

    @Test
    @DisplayName("Empty or null candidate list returns safe empty list without error")
    void filter_emptyOrNullCandidates_returnsEmptyList() {
        assertThat(filter.filter(null, criteriaWithCoords)).isEmpty();
        assertThat(filter.filter(Collections.emptyList(), criteriaWithCoords)).isEmpty();
    }

    @Test
    @DisplayName("Case 1: Driver and pickup at identical coordinates yields distance of 0.0 km")
    void filter_driverAndPickupAtSameCoordinates_distanceIsZero() {
        EligibleDriver sameLocDriver = createDriverWithLocation("d-same", "Same Location Driver",
                PICKUP_LAT, PICKUP_LON, Instant.now());

        List<EligibleDriver> result = filter.filter(List.of(sameLocDriver), criteriaWithCoords);

        assertThat(result).hasSize(1);
        EligibleDriver evaluated = result.get(0);
        assertThat(evaluated.getDistanceKm()).isNotNull();
        assertThat(evaluated.getDistanceKm()).isEqualTo(0.0);
        assertThat(evaluated.getMetadata()).containsEntry("distanceUnit", "KM");
        assertThat(evaluated.getMetadata()).containsEntry("locationValid", true);
    }

    @Test
    @DisplayName("Case 2: Two drivers at different locations have distinct distances in km")
    void filter_twoDriversWithDifferentLocations_computesDistinctDistances() {
        // Driver 1: Notre Dame Cathedral (~0.58 km from Opera House)
        EligibleDriver dNear = createDriverWithLocation("d-near", "Near Driver",
                10.7798, 106.6990, Instant.now());

        // Driver 2: Tan Son Nhat Airport (~6.2 km from Opera House)
        EligibleDriver dFar = createDriverWithLocation("d-far", "Far Driver",
                10.8185, 106.6588, Instant.now());

        List<EligibleDriver> result = filter.filter(List.of(dNear, dFar), criteriaWithCoords);

        assertThat(result).hasSize(2);

        EligibleDriver evalNear = result.stream().filter(d -> d.getDriverId().equals("d-near")).findFirst().orElseThrow();
        EligibleDriver evalFar = result.stream().filter(d -> d.getDriverId().equals("d-far")).findFirst().orElseThrow();

        assertThat(evalNear.getDistanceKm()).isNotNull();
        assertThat(evalFar.getDistanceKm()).isNotNull();

        assertThat(evalNear.getDistanceKm()).isCloseTo(0.58, offset(0.1));
        assertThat(evalFar.getDistanceKm()).isCloseTo(6.73, offset(0.1));
        assertThat(evalNear.getDistanceKm()).isLessThan(evalFar.getDistanceKm());

        // Consistent measurement unit
        assertThat(evalNear.getMetadata()).containsEntry("distanceUnit", "KM");
        assertThat(evalFar.getMetadata()).containsEntry("distanceUnit", "KM");
    }

    @Test
    @DisplayName("Case 3: Invalid coordinates (out-of-bounds or NaN) are marked invalid and distance is null")
    void filter_invalidCoordinates_markedInvalidAndDistanceNull() {
        // Lat > 90
        EligibleDriver dInvalidLat = createDriverWithLocation("d-inv-lat", "Invalid Lat",
                95.0, 106.7032, Instant.now());

        // Lon > 180
        EligibleDriver dInvalidLon = createDriverWithLocation("d-inv-lon", "Invalid Lon",
                10.7766, 185.0, Instant.now());

        List<EligibleDriver> result = filter.filter(List.of(dInvalidLat, dInvalidLon), criteriaWithCoords);

        assertThat(result).hasSize(2);
        for (EligibleDriver driver : result) {
            assertThat(driver.getDistanceKm()).isNull(); // NEVER 0.0
            assertThat(driver.getMetadata()).containsEntry("locationValid", false);
        }
    }

    @Test
    @DisplayName("Case 4: Driver with missing location (null coords) has distanceKm = null and is NOT considered closest")
    void filter_driverWithMissingLocation_distanceIsNull_neverZero() {
        EligibleDriver dNoLoc = createDriverWithLocation("d-noloc", "No Location Driver",
                null, null, null);

        List<EligibleDriver> result = filter.filter(List.of(dNoLoc), criteriaWithCoords);

        assertThat(result).hasSize(1);
        EligibleDriver evaluated = result.get(0);

        // Crucial requirement: MUST NOT assume driver without location is closest
        assertThat(evaluated.getDistanceKm()).isNull();
        assertThat(evaluated.getMetadata()).containsEntry("locationValid", false);
        assertThat(evaluated.getMetadata()).containsEntry("proximityCalculated", false);
    }

    @Test
    @DisplayName("Case 5: Order without valid pickup location retains candidates with distanceKm = null and logs safely")
    void filter_orderWithoutValidPickupLocation_safeHandling() {
        AssignmentCriteria criteriaNoPickup = AssignmentCriteria.builder()
                .orderId("ord-no-pickup")
                .pickupLocation("Non-numeric Address Without Coordinates")
                .build();

        EligibleDriver driver = createDriverWithLocation("d-1", "Valid Driver",
                10.7798, 106.6990, Instant.now());

        List<EligibleDriver> result = filter.filter(List.of(driver), criteriaNoPickup);

        assertThat(result).hasSize(1);
        EligibleDriver evaluated = result.get(0);
        assertThat(evaluated.getDistanceKm()).isNull();
        assertThat(evaluated.getMetadata()).containsEntry("pickupLocationValid", false);
        assertThat(evaluated.getMetadata()).containsEntry("proximityCalculated", false);
    }

    @Test
    @DisplayName("Case 6: Pickup location defined as parsable coordinate string in pickupLocation")
    void filter_pickupLocationParsedFromString() {
        AssignmentCriteria criteriaParsed = AssignmentCriteria.builder()
                .orderId("ord-parsed")
                .pickupLocation("10.7766, 106.7032") // string coordinates
                .build();

        EligibleDriver driver = createDriverWithLocation("d-1", "Driver 1",
                10.7766, 106.7032, Instant.now());

        List<EligibleDriver> result = filter.filter(List.of(driver), criteriaParsed);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getDistanceKm()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("Stale location detection: location updated older than maxAgeMinutes is flagged")
    void filter_staleLocationDetection() {
        Instant freshTime = Instant.now().minus(5, ChronoUnit.MINUTES);
        Instant staleTime = Instant.now().minus(45, ChronoUnit.MINUTES); // 45 mins > 30 mins maxAge

        EligibleDriver freshDriver = createDriverWithLocation("d-fresh", "Fresh Driver",
                10.7798, 106.6990, freshTime);

        EligibleDriver staleDriver = createDriverWithLocation("d-stale", "Stale Driver",
                10.7798, 106.6990, staleTime);

        List<EligibleDriver> result = filter.filter(List.of(freshDriver, staleDriver), criteriaWithCoords);

        assertThat(result).hasSize(2);
        EligibleDriver evalFresh = result.stream().filter(d -> d.getDriverId().equals("d-fresh")).findFirst().orElseThrow();
        EligibleDriver evalStale = result.stream().filter(d -> d.getDriverId().equals("d-stale")).findFirst().orElseThrow();

        assertThat(evalFresh.getMetadata()).containsEntry("locationStale", false);
        assertThat(evalStale.getMetadata()).containsEntry("locationStale", true);
        assertThat((Long) evalStale.getMetadata().get("locationAgeMinutes")).isGreaterThanOrEqualTo(45L);
    }

    @Test
    @DisplayName("Driver coordinates resolved from metadata map if fields are null")
    void filter_driverCoordinatesFromMetadata() {
        EligibleDriver dMeta = createDriverWithLocation("d-meta", "Metadata Driver", null, null, null);
        dMeta.addMetadata("latitude", 10.7798);
        dMeta.addMetadata("longitude", 106.6990);

        List<EligibleDriver> result = filter.filter(List.of(dMeta), criteriaWithCoords);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getDistanceKm()).isNotNull();
        assertThat(result.get(0).getDistanceKm()).isCloseTo(0.58, offset(0.1));
    }

    @Test
    @DisplayName("Filter order is 30, running after DriverWorkloadFilter (order 20)")
    void filter_orderAndName() {
        assertThat(filter.getOrder()).isEqualTo(30);
        assertThat(filter.getName()).isEqualTo("DriverProximityFilter");
    }
}
