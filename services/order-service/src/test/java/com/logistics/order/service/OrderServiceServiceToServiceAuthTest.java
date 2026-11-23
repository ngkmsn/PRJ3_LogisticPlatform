package com.logistics.order.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.logistics.common.dto.OrderMetricsDto;
import com.logistics.common.security.JwtTokenProvider;
import com.logistics.common.security.ServiceTokenManager;
import com.logistics.common.security.ServiceTokenProvider;
import com.logistics.order.client.DefaultTrackingServiceClient;
import com.logistics.order.exception.AccessDeniedException;
import com.logistics.order.exception.BadCredentialsException;
import com.logistics.order.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Collections;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceServiceToServiceAuthTest {

    private static final String SECRET = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
    private static final String ISSUER = "logistics-platform";

    @Mock
    private OrderRepository orderRepository;

    private JwtTokenProvider jwtTokenProvider;
    private OrderService orderService;
    private ServiceTokenProvider shipmentServiceProvider;

    @BeforeEach
    void setUp() {
        jwtTokenProvider = new JwtTokenProvider(SECRET, 86400000L, ISSUER);
        orderService = new OrderService(orderRepository, jwtTokenProvider);
        shipmentServiceProvider = new ServiceTokenProvider("shipment-service", SECRET, ISSUER, 300L);
    }

    @Test
    @DisplayName("PB-057: Service token for order-service with order:metrics:read allows accessing order metrics")
    void getOrderMetrics_validServiceToken_success() {
        String serviceToken = shipmentServiceProvider.generateToken("order-service", Set.of("order:metrics:read"));

        when(orderRepository.count()).thenReturn(100L);
        when(orderRepository.countCreatedSince(any())).thenReturn(25L);
        when(orderRepository.findRecentOrders(10)).thenReturn(Collections.emptyList());

        OrderMetricsDto metrics = orderService.getOrderMetrics("Bearer " + serviceToken);

        assertThat(metrics).isNotNull();
        assertThat(metrics.getTotalOrders()).isEqualTo(100L);
        assertThat(metrics.getTodayOrders()).isEqualTo(25L);
    }

    @Test
    @DisplayName("PB-057: Service token with wrong audience is rejected with 403 Forbidden")
    void getOrderMetrics_wrongAudience_rejected() {
        // Minted for user-service, presented to order-service
        String wrongAudienceToken = shipmentServiceProvider.generateToken("user-service", Set.of("order:metrics:read"));

        assertThatThrownBy(() -> orderService.getOrderMetrics("Bearer " + wrongAudienceToken))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Invalid token audience: expected order-service");
    }

    @Test
    @DisplayName("PB-057: Service token lacking required scope is rejected with 403 Forbidden")
    void getOrderMetrics_insufficientScope_rejected() {
        // Minted with only user:read, lacks order:metrics:read
        String insufficientScopeToken = shipmentServiceProvider.generateToken("order-service", Set.of("user:read"));

        assertThatThrownBy(() -> orderService.getOrderMetrics("Bearer " + insufficientScopeToken))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Insufficient service permissions: required scope order:metrics:read");
    }

    @Test
    @DisplayName("PB-057: Missing or invalid token is rejected with 401 BadCredentialsException")
    void getOrderMetrics_missingOrInvalidToken_rejected() {
        assertThatThrownBy(() -> orderService.getOrderMetrics(null))
                .isInstanceOf(BadCredentialsException.class);

        assertThatThrownBy(() -> orderService.getOrderMetrics("Bearer invalid.token.value"))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    @DisplayName("PB-057: DefaultTrackingServiceClient uses ServiceTokenManager to attach service token")
    void trackingServiceClient_attachesServiceToken() throws Exception {
        HttpClient mockHttpClient = mock(HttpClient.class);
        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.body()).thenReturn("{\"success\":true,\"message\":\"OK\",\"data\":{\"orderId\":\"ORD-100\"}}");

        when(mockHttpClient.send(any(HttpRequest.class), any())).thenAnswer(invocation -> {
            HttpRequest req = invocation.getArgument(0);
            String authHeader = req.headers().firstValue("Authorization").orElse(null);
            assertThat(authHeader).isNotNull();
            assertThat(authHeader).startsWith("Bearer ");
            // Verify attached token is valid for shipment-service
            String token = authHeader.substring(7);
            assertThat(jwtTokenProvider.validateToken(token)).isTrue();
            return mockResponse;
        });

        DefaultTrackingServiceClient client = new DefaultTrackingServiceClient(
                "http://localhost:8083",
                new ObjectMapper(),
                mockHttpClient
        );
        ServiceTokenManager tokenManager = new ServiceTokenManager("order-service", SECRET, ISSUER, 300L, 30L);
        client.setServiceTokenManager(tokenManager);

        var result = client.getOrderTracking(null, "ORD-100", false);
        assertThat(result).isNotNull();
        assertThat(result.getOrderId()).isEqualTo("ORD-100");
    }
}
