package com.logistics.user.service;

import com.logistics.common.model.DriverAvailability;
import com.logistics.common.security.JwtTokenProvider;
import com.logistics.common.security.ServiceTokenProvider;
import com.logistics.user.dto.DriverProfileDto;
import com.logistics.user.dto.PagedResponse;
import com.logistics.user.entity.DriverProfile;
import com.logistics.user.exception.AccessDeniedException;
import com.logistics.user.exception.BadCredentialsException;
import com.logistics.user.repository.DriverProfileRepository;
import com.logistics.user.repository.UserRepository;
import io.quarkus.panache.common.Page;
import io.quarkus.panache.common.Parameters;
import io.quarkus.hibernate.orm.panache.PanacheQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DriverServiceServiceToServiceAuthTest {

    private static final String SECRET = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
    private static final String ISSUER = "logistics-platform";

    @Mock
    private DriverProfileRepository driverProfileRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PanacheQuery<DriverProfile> panacheQuery;

    private JwtTokenProvider jwtTokenProvider;
    private DriverService driverService;
    private ServiceTokenProvider shipmentServiceProvider;

    @BeforeEach
    void setUp() {
        jwtTokenProvider = new JwtTokenProvider(SECRET, 86400000L, ISSUER);
        driverService = new DriverService(driverProfileRepository, userRepository, jwtTokenProvider);
        shipmentServiceProvider = new ServiceTokenProvider("shipment-service", SECRET, ISSUER, 300L);
    }

    @Test
    @DisplayName("PB-057: Valid service token for user-service with driver:read allows listing drivers")
    void listDrivers_validServiceToken_success() {
        String serviceToken = shipmentServiceProvider.generateToken("user-service", Set.of("driver:read"));

        DriverProfile profile = new DriverProfile(
                "dp-id-1", "user-1", "Nguyen Van A", "0901234567",
                "LIC-12345", "B2", "MOTORBIKE", "29A-11111", "Hanoi", "ACTIVE"
        );
        when(driverProfileRepository.find(anyString(), any(Parameters.class))).thenReturn(panacheQuery);
        when(panacheQuery.count()).thenReturn(1L);
        when(panacheQuery.page(any(Page.class))).thenReturn(panacheQuery);
        when(panacheQuery.list()).thenReturn(List.of(profile));

        PagedResponse<DriverProfileDto> response = driverService.listDrivers("Bearer " + serviceToken, 0, 10, null);

        assertThat(response).isNotNull();
        assertThat(response.getContent()).hasSize(1);
        assertThat(response.getContent().get(0).getFullName()).isEqualTo("Nguyen Van A");
    }

    @Test
    @DisplayName("PB-057: Service token with wrong audience is rejected with 403 Forbidden")
    void listDrivers_wrongAudience_rejected() {
        // Minted for order-service, sent to user-service
        String wrongAudienceToken = shipmentServiceProvider.generateToken("order-service", Set.of("driver:read"));

        assertThatThrownBy(() -> driverService.listDrivers("Bearer " + wrongAudienceToken, 0, 10, null))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Invalid token audience: expected user-service");
    }

    @Test
    @DisplayName("PB-057: Service token with insufficient scope is rejected with 403 Forbidden")
    void listDrivers_insufficientScope_rejected() {
        // Minted with only order:read, lacks driver:read
        String insufficientScopeToken = shipmentServiceProvider.generateToken("user-service", Set.of("order:read"));

        assertThatThrownBy(() -> driverService.listDrivers("Bearer " + insufficientScopeToken, 0, 10, null))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Insufficient service permissions: required scope driver:read");
    }

    @Test
    @DisplayName("PB-057: Missing or invalid token is rejected with 401 BadCredentialsException")
    void listDrivers_missingOrInvalidToken_rejected() {
        assertThatThrownBy(() -> driverService.listDrivers(null, 0, 10, null))
                .isInstanceOf(BadCredentialsException.class);

        assertThatThrownBy(() -> driverService.listDrivers("Bearer invalid.service.token", 0, 10, null))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    @DisplayName("PB-057: Valid service token allows retrieving driver operational metrics")
    void getDriverOperationalMetrics_validServiceToken_success() {
        String serviceToken = shipmentServiceProvider.generateToken("user-service", Set.of("driver:metrics:read"));

        when(driverProfileRepository.count()).thenReturn(50L);
        when(driverProfileRepository.countByAvailability(DriverAvailability.AVAILABLE)).thenReturn(30L);
        when(driverProfileRepository.countByAvailability(DriverAvailability.UNAVAILABLE)).thenReturn(20L);
        when(driverProfileRepository.countByStatus("ACTIVE")).thenReturn(45L);
        when(driverProfileRepository.countByStatus("INACTIVE")).thenReturn(5L);
        when(driverProfileRepository.countByStatus("SUSPENDED")).thenReturn(0L);

        com.logistics.common.dto.DriverOperationalMetricsDto metrics =
                driverService.getDriverOperationalMetrics("Bearer " + serviceToken);

        assertThat(metrics).isNotNull();
        assertThat(metrics.getTotalDrivers()).isEqualTo(50L);
        assertThat(metrics.getAvailableDrivers()).isEqualTo(30L);
    }

    @Test
    @DisplayName("PB-057: Operational metrics rejects wrong audience service token")
    void getDriverOperationalMetrics_wrongAudience_rejected() {
        String wrongAudienceToken = shipmentServiceProvider.generateToken("shipment-service", Set.of("driver:metrics:read"));

        assertThatThrownBy(() -> driverService.getDriverOperationalMetrics("Bearer " + wrongAudienceToken))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Invalid token audience: expected user-service");
    }
}
