package com.logistics.shipment.service;

import com.logistics.common.security.JwtTokenProvider;
import com.logistics.common.security.Role;
import com.logistics.shipment.dto.UpdateDriverLocationRequest;
import com.logistics.shipment.entity.DriverLocation;
import com.logistics.shipment.exception.AccessDeniedException;
import com.logistics.shipment.exception.BadCredentialsException;
import com.logistics.shipment.exception.TrackingServiceException;
import com.logistics.shipment.repository.DriverLocationRepository;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectSpy;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

@QuarkusTest
class DriverGpsTrackingServiceTest {

    @Inject
    DriverLocationRepository driverLocationRepository;

    @Inject
    TrackingService trackingService;

    @Inject
    JwtTokenProvider jwtTokenProvider;

    @InjectSpy
    DriverLocationRepository spyRepository;

    private String driver1Token;
    private String driver2Token;
    private String customerToken;
    private String adminToken;

    private final String driver1UserId = "drv-gps-user-01";
    private final String driver2UserId = "drv-gps-user-02";

    @BeforeEach
    @Transactional
    void setUp() {
        driverLocationRepository.deleteAll();

        driver1Token = "Bearer " + jwtTokenProvider.generateToken(
                driver1UserId, "driver_gps_1", "driver1@logistics.com", Role.DRIVER.name()
        );
        driver2Token = "Bearer " + jwtTokenProvider.generateToken(
                driver2UserId, "driver_gps_2", "driver2@logistics.com", Role.DRIVER.name()
        );
        customerToken = "Bearer " + jwtTokenProvider.generateToken(
                "cust-gps-user-01", "customer_gps", "customer@test.com", Role.CUSTOMER.name()
        );
        adminToken = "Bearer " + jwtTokenProvider.generateToken(
                "admin-gps-user-01", "admin_gps", "admin@test.com", Role.ADMIN.name()
        );
    }

    @Test
    @TestTransaction
    @DisplayName("Driver sends valid GPS location and receives updated DriverLocation entity")
    void recordDriverLocation_validLocation_success() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        UpdateDriverLocationRequest request = new UpdateDriverLocationRequest(
                21.028511, 105.854444, now, 5.0, 35.0, 180.0, "ORD-123"
        );

        DriverLocation recorded = trackingService.recordDriverLocation(driver1Token, request);

        assertThat(recorded).isNotNull();
        assertThat(recorded.getDriverId()).isEqualTo(driver1UserId);
        assertThat(recorded.getUserId()).isEqualTo(driver1UserId);
        assertThat(recorded.getLatitude()).isEqualTo(21.028511);
        assertThat(recorded.getLongitude()).isEqualTo(105.854444);
        assertThat(recorded.getAccuracy()).isEqualTo(5.0);
        assertThat(recorded.getSpeed()).isEqualTo(35.0);
        assertThat(recorded.getBearing()).isEqualTo(180.0);
        assertThat(recorded.getOrderId()).isEqualTo("ORD-123");
        assertThat(recorded.getTimestamp()).isEqualTo(now);

        // Verify stored in repository
        Optional<DriverLocation> inDb = driverLocationRepository.findByDriverId(driver1UserId);
        assertThat(inDb).isPresent();
        assertThat(inDb.get().getLatitude()).isEqualTo(21.028511);
        assertThat(inDb.get().getLongitude()).isEqualTo(105.854444);
    }

    @Test
    @TestTransaction
    @DisplayName("Successive valid GPS updates for same driver update existing row without duplicating")
    void recordDriverLocation_upsert_updatesExistingRecord() {
        Instant t1 = Instant.now().minus(2, ChronoUnit.MINUTES).truncatedTo(ChronoUnit.MILLIS);
        UpdateDriverLocationRequest req1 = new UpdateDriverLocationRequest(21.010000, 105.800000, t1);
        trackingService.recordDriverLocation(driver1Token, req1);

        assertThat(driverLocationRepository.count("driverId", driver1UserId)).isEqualTo(1);

        Instant t2 = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        UpdateDriverLocationRequest req2 = new UpdateDriverLocationRequest(21.020000, 105.820000, t2);
        DriverLocation updated = trackingService.recordDriverLocation(driver1Token, req2);

        // Ensure row count is still exactly 1 for this driver
        assertThat(driverLocationRepository.count("driverId", driver1UserId)).isEqualTo(1);
        assertThat(updated.getLatitude()).isEqualTo(21.020000);
        assertThat(updated.getLongitude()).isEqualTo(105.820000);
        assertThat(updated.getTimestamp()).isEqualTo(t2);
    }

    @Test
    @TestTransaction
    @DisplayName("Rejects GPS update when incoming timestamp is older than current recorded location")
    void recordDriverLocation_staleTimestamp_throwsIllegalArgument() {
        Instant t2 = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        UpdateDriverLocationRequest reqNew = new UpdateDriverLocationRequest(21.030000, 105.830000, t2);
        trackingService.recordDriverLocation(driver1Token, reqNew);

        // Attempt to send location with timestamp t1 older than t2
        Instant t1 = t2.minus(5, ChronoUnit.MINUTES);
        UpdateDriverLocationRequest reqStale = new UpdateDriverLocationRequest(21.010000, 105.810000, t1);

        assertThatThrownBy(() -> trackingService.recordDriverLocation(driver1Token, reqStale))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Stale GPS location")
                .hasMessageContaining("is older than current location timestamp");

        // Verify database location still retains t2 coordinates
        DriverLocation current = driverLocationRepository.findByDriverId(driver1UserId).orElseThrow();
        assertThat(current.getLatitude()).isEqualTo(21.030000);
        assertThat(current.getLongitude()).isEqualTo(105.830000);
        assertThat(current.getTimestamp()).isEqualTo(t2);
    }

    @Test
    @DisplayName("Rejects GPS update when timestamp is more than 5 minutes in the future")
    void recordDriverLocation_futureTimestamp_throwsIllegalArgument() {
        Instant future = Instant.now().plus(10, ChronoUnit.MINUTES);
        UpdateDriverLocationRequest request = new UpdateDriverLocationRequest(21.000000, 105.800000, future);

        assertThatThrownBy(() -> trackingService.recordDriverLocation(driver1Token, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Location timestamp cannot be in the future");
    }

    @Test
    @DisplayName("Rejects GPS update when latitude is out of range or null")
    void recordDriverLocation_invalidLatitude_throwsIllegalArgument() {
        // Latitude > 90.0
        UpdateDriverLocationRequest req1 = new UpdateDriverLocationRequest(91.0, 105.800000);
        assertThatThrownBy(() -> trackingService.recordDriverLocation(driver1Token, req1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Latitude must be between -90.0 and 90.0 degrees");

        // Latitude < -90.0
        UpdateDriverLocationRequest req2 = new UpdateDriverLocationRequest(-95.5, 105.800000);
        assertThatThrownBy(() -> trackingService.recordDriverLocation(driver1Token, req2))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Latitude must be between -90.0 and 90.0 degrees");

        // Latitude is null
        UpdateDriverLocationRequest req3 = new UpdateDriverLocationRequest(null, 105.800000);
        assertThatThrownBy(() -> trackingService.recordDriverLocation(driver1Token, req3))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Latitude is required");
    }

    @Test
    @DisplayName("Rejects GPS update when longitude is out of range or null")
    void recordDriverLocation_invalidLongitude_throwsIllegalArgument() {
        // Longitude > 180.0
        UpdateDriverLocationRequest req1 = new UpdateDriverLocationRequest(21.000000, 185.0);
        assertThatThrownBy(() -> trackingService.recordDriverLocation(driver1Token, req1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Longitude must be between -180.0 and 180.0 degrees");

        // Longitude < -180.0
        UpdateDriverLocationRequest req2 = new UpdateDriverLocationRequest(21.000000, -181.5);
        assertThatThrownBy(() -> trackingService.recordDriverLocation(driver1Token, req2))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Longitude must be between -180.0 and 180.0 degrees");

        // Longitude is null
        UpdateDriverLocationRequest req3 = new UpdateDriverLocationRequest(21.000000, null);
        assertThatThrownBy(() -> trackingService.recordDriverLocation(driver1Token, req3))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Longitude is required");
    }

    @Test
    @DisplayName("Rejects GPS update when accuracy, speed, or bearing are negative or invalid")
    void recordDriverLocation_invalidMetrics_throwsIllegalArgument() {
        // Negative accuracy
        UpdateDriverLocationRequest req1 = new UpdateDriverLocationRequest(21.0, 105.0, Instant.now(), -1.0, null, null, null);
        assertThatThrownBy(() -> trackingService.recordDriverLocation(driver1Token, req1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Accuracy must be greater than or equal to 0");

        // Negative speed
        UpdateDriverLocationRequest req2 = new UpdateDriverLocationRequest(21.0, 105.0, Instant.now(), null, -5.0, null, null);
        assertThatThrownBy(() -> trackingService.recordDriverLocation(driver1Token, req2))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Speed must be greater than or equal to 0");

        // Bearing > 360
        UpdateDriverLocationRequest req3 = new UpdateDriverLocationRequest(21.0, 105.0, Instant.now(), null, null, 365.0, null);
        assertThatThrownBy(() -> trackingService.recordDriverLocation(driver1Token, req3))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Bearing must be between 0.0 and 360.0 degrees");
    }

    @Test
    @DisplayName("Rejects GPS update when caller has CUSTOMER role")
    void recordDriverLocation_customerRole_throwsAccessDenied() {
        UpdateDriverLocationRequest request = new UpdateDriverLocationRequest(21.028511, 105.854444);

        assertThatThrownBy(() -> trackingService.recordDriverLocation(customerToken, request))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Only drivers are permitted to send GPS location");
    }

    @Test
    @DisplayName("Rejects GPS update when Authorization header is missing or token is invalid")
    void recordDriverLocation_missingOrInvalidToken_throwsBadCredentials() {
        UpdateDriverLocationRequest request = new UpdateDriverLocationRequest(21.028511, 105.854444);

        assertThatThrownBy(() -> trackingService.recordDriverLocation(null, request))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("Missing or invalid Authorization header");

        assertThatThrownBy(() -> trackingService.recordDriverLocation("Bearer invalid.jwt.token", request))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("Token is invalid or expired");
    }

    @Test
    @TestTransaction
    @DisplayName("getCurrentDriverLocation returns latest location for authenticated driver")
    void getCurrentDriverLocation_success() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        trackingService.recordDriverLocation(driver2Token, new UpdateDriverLocationRequest(10.776889, 106.700806, now));

        Optional<DriverLocation> current = trackingService.getCurrentDriverLocation(driver2Token);
        assertThat(current).isPresent();
        assertThat(current.get().getDriverId()).isEqualTo(driver2UserId);
        assertThat(current.get().getLatitude()).isEqualTo(10.776889);
        assertThat(current.get().getLongitude()).isEqualTo(106.700806);
    }

    @Test
    @TestTransaction
    @DisplayName("getDriverLocation returns specific driver's location for authorized caller")
    void getDriverLocation_success() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        trackingService.recordDriverLocation(driver1Token, new UpdateDriverLocationRequest(21.028511, 105.854444, now));

        Optional<DriverLocation> loc = trackingService.getDriverLocation(adminToken, driver1UserId);
        assertThat(loc).isPresent();
        assertThat(loc.get().getDriverId()).isEqualTo(driver1UserId);
    }

    @Test
    @DisplayName("Database errors during persistence are safely wrapped in TrackingServiceException")
    void recordDriverLocation_databaseError_wrapsInTrackingServiceException() {
        UpdateDriverLocationRequest request = new UpdateDriverLocationRequest(21.0, 105.0);

        doThrow(new RuntimeException("Simulated database failure"))
                .when(spyRepository).persist(any(DriverLocation.class));

        assertThatThrownBy(() -> trackingService.recordDriverLocation(driver1Token, request))
                .isInstanceOf(TrackingServiceException.class)
                .hasMessageContaining("Database error saving driver GPS location");
    }
}
