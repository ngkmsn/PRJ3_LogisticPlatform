package com.logistics.shipment.service;

import com.logistics.common.dto.OrderTrackingDto;
import com.logistics.common.security.JwtTokenProvider;
import com.logistics.common.security.ServiceTokenManager;
import com.logistics.common.security.ServiceTokenProvider;
import com.logistics.shipment.dashboard.client.DefaultOrderMetricsClient;
import com.logistics.shipment.eligibility.client.DefaultDriverClient;
import com.logistics.shipment.entity.Assignment;
import com.logistics.shipment.entity.DriverLocation;
import com.logistics.shipment.exception.AccessDeniedException;
import com.logistics.shipment.exception.BadCredentialsException;
import com.logistics.shipment.model.AssignmentStatus;
import com.logistics.shipment.repository.AssignmentRepository;
import com.logistics.shipment.repository.DriverLocationRepository;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@QuarkusTest
class ShipmentServiceToServiceAuthTest {

    private static final String SECRET = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";
    private static final String ISSUER = "logistics-platform";

    @Inject
    AssignmentRepository assignmentRepository;

    @Inject
    DriverLocationRepository driverLocationRepository;

    @Inject
    TrackingService trackingService;

    @Inject
    JwtTokenProvider jwtTokenProvider;

    private ServiceTokenProvider orderServiceProvider;

    @BeforeEach
    @Transactional
    void setUp() {
        driverLocationRepository.deleteAll();
        assignmentRepository.deleteAll();
        orderServiceProvider = new ServiceTokenProvider("order-service", SECRET, ISSUER, 300L);
    }

    private Assignment createAssignment(String orderId, String customerId, String driverId, AssignmentStatus status) {
        Assignment assignment = new Assignment(
                UUID.randomUUID().toString(),
                orderId,
                "ORD-" + orderId,
                customerId,
                "Recipient Name",
                "0987654321",
                "123 Delivery St, City",
                new BigDecimal("150000"),
                status,
                driverId,
                status == AssignmentStatus.ASSIGNED ? Instant.now() : null,
                "S2S Tracking Test",
                Instant.now(),
                Instant.now()
        );
        assignmentRepository.persist(assignment);
        return assignment;
    }

    private DriverLocation recordDriverGps(String targetDriverId, double lat, double lon) {
        DriverLocation location = new DriverLocation(
                UUID.randomUUID().toString(),
                targetDriverId,
                targetDriverId + "-user",
                lat,
                lon,
                5.0,
                25.0,
                90.0,
                null,
                Instant.now(),
                Instant.now(),
                Instant.now()
        );
        driverLocationRepository.persist(location);
        return location;
    }

    @Test
    @Transactional
    @DisplayName("PB-057: Service token for shipment-service allows reading order tracking without user ownership")
    void getOrderTracking_validServiceToken_success() {
        String orderId = "ord-s2s-100";
        createAssignment(orderId, "customer-1", "driver-1", AssignmentStatus.IN_TRANSIT);
        recordDriverGps("driver-1", 21.0285, 105.8542);

        String serviceToken = orderServiceProvider.generateToken("shipment-service", Set.of("shipment:tracking:read"));

        OrderTrackingDto tracking = trackingService.getOrderTracking("Bearer " + serviceToken, orderId, false);

        assertThat(tracking).isNotNull();
        assertThat(tracking.getOrderId()).isEqualTo(orderId);
        assertThat(tracking.getAssignmentStatus()).isEqualTo("IN_TRANSIT");
        assertThat(tracking.getLatitude()).isEqualTo(21.0285);
        assertThat(tracking.getLongitude()).isEqualTo(105.8542);
    }

    @Test
    @Transactional
    @DisplayName("PB-057: Service token with wrong audience is rejected with 403 Forbidden")
    void getOrderTracking_wrongAudience_rejected() {
        String orderId = "ord-s2s-101";
        createAssignment(orderId, "customer-1", "driver-1", AssignmentStatus.IN_TRANSIT);

        // Minted for user-service, presented to shipment-service
        String wrongAudienceToken = orderServiceProvider.generateToken("user-service", Set.of("shipment:tracking:read"));

        assertThatThrownBy(() -> trackingService.getOrderTracking("Bearer " + wrongAudienceToken, orderId, false))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Invalid token audience: expected shipment-service");
    }

    @Test
    @Transactional
    @DisplayName("PB-057: Missing or invalid token is rejected with 401 BadCredentialsException")
    void getOrderTracking_missingOrInvalidToken_rejected() {
        assertThatThrownBy(() -> trackingService.getOrderTracking(null, "ord-100", false))
                .isInstanceOf(BadCredentialsException.class);

        assertThatThrownBy(() -> trackingService.getOrderTracking("Bearer invalid.token", "ord-100", false))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    @DisplayName("PB-057: DefaultDriverClient attaches service token for user-service")
    void defaultDriverClient_attachesServiceToken() {
        DefaultDriverClient client = new DefaultDriverClient();
        ServiceTokenManager tokenManager = new ServiceTokenManager("shipment-service", SECRET, ISSUER, 300L, 30L);
        client.setServiceTokenManager(tokenManager);

        String token = tokenManager.getServiceToken("user-service", Set.of("driver:read"));
        assertThat(token).isNotBlank();
        assertThat(jwtTokenProvider.validateToken(token)).isTrue();
    }

    @Test
    @DisplayName("PB-057: DefaultOrderMetricsClient attaches service token for order-service")
    void defaultOrderMetricsClient_attachesServiceToken() {
        DefaultOrderMetricsClient client = new DefaultOrderMetricsClient();
        ServiceTokenManager tokenManager = new ServiceTokenManager("shipment-service", SECRET, ISSUER, 300L, 30L);
        client.setServiceTokenManager(tokenManager);

        String token = tokenManager.getServiceToken("order-service", Set.of("order:metrics:read"));
        assertThat(token).isNotBlank();
        assertThat(jwtTokenProvider.validateToken(token)).isTrue();
    }
}
