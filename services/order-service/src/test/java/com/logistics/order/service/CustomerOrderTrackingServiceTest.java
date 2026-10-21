package com.logistics.order.service;

import com.logistics.common.dto.OrderTrackingDto;
import com.logistics.common.model.OrderStatus;
import com.logistics.common.security.JwtTokenProvider;
import com.logistics.common.security.Role;
import com.logistics.order.client.TrackingServiceClient;
import com.logistics.order.entity.Order;
import com.logistics.order.exception.AccessDeniedException;
import com.logistics.order.exception.BadCredentialsException;
import com.logistics.order.exception.OrderNotFoundException;
import com.logistics.order.exception.TrackingServiceUnavailableException;
import com.logistics.order.repository.OrderRepository;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectMock;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@QuarkusTest
class CustomerOrderTrackingServiceTest {

    @Inject
    OrderRepository orderRepository;

    @Inject
    OrderService orderService;

    @Inject
    JwtTokenProvider jwtTokenProvider;

    @InjectMock
    TrackingServiceClient trackingServiceClient;

    private String customerAToken;
    private String customerBToken;
    private final String customerAId = "cust-ord-trk-01";
    private final String customerBId = "cust-ord-trk-02";

    @BeforeEach
    @Transactional
    void setUp() {
        orderRepository.deleteAll();

        customerAToken = "Bearer " + jwtTokenProvider.generateToken(
                customerAId, "customerA", "custA@logistics.com", Role.CUSTOMER.name()
        );
        customerBToken = "Bearer " + jwtTokenProvider.generateToken(
                customerBId, "customerB", "custB@logistics.com", Role.CUSTOMER.name()
        );
    }

    private Order createOrder(String orderId, String customerId, OrderStatus status) {
        Order order = new Order(
                orderId,
                "ORD-" + orderId,
                customerId,
                status,
                "Recipient",
                "0909090909",
                "123 Street",
                new BigDecimal("180000"),
                "Notes"
        );
        orderRepository.persist(order);
        return order;
    }

    @Test
    @Transactional
    @DisplayName("Customer views driver location when order is IN_TRANSIT")
    void getOrderTracking_inTransit_success() {
        String orderId = "ord-srv-101";
        createOrder(orderId, customerAId, OrderStatus.IN_TRANSIT);

        OrderTrackingDto mockDto = new OrderTrackingDto(
                orderId, "ORD-" + orderId, customerAId, "IN_TRANSIT", "IN_TRANSIT", "drv-001",
                21.028511, 105.854444, Instant.now(), true, false
        );

        when(trackingServiceClient.getOrderTracking(anyString(), anyString(), anyBoolean()))
                .thenReturn(mockDto);

        OrderTrackingDto result = orderService.getOrderTracking(customerAToken, orderId);

        assertThat(result).isNotNull();
        assertThat(result.getOrderId()).isEqualTo(orderId);
        assertThat(result.getDriverId()).isEqualTo("drv-001");
        assertThat(result.getLatitude()).isEqualTo(21.028511);
        assertThat(result.isLiveTrackingActive()).isTrue();
    }

    @Test
    @Transactional
    @DisplayName("Customer views driver location when order is ASSIGNED")
    void getOrderTracking_assigned_success() {
        String orderId = "ord-srv-102";
        createOrder(orderId, customerAId, OrderStatus.ASSIGNED);

        OrderTrackingDto mockDto = new OrderTrackingDto(
                orderId, "ORD-" + orderId, customerAId, "ASSIGNED", "ASSIGNED", "drv-002",
                10.776889, 106.700806, Instant.now(), true, false
        );

        when(trackingServiceClient.getOrderTracking(anyString(), anyString(), anyBoolean()))
                .thenReturn(mockDto);

        OrderTrackingDto result = orderService.getOrderTracking(customerAToken, orderId);

        assertThat(result).isNotNull();
        assertThat(result.getOrderId()).isEqualTo(orderId);
        assertThat(result.getDriverId()).isEqualTo("drv-002");
    }

    @Test
    @Transactional
    @DisplayName("Customer cannot view tracking of order owned by another customer (403 Forbidden)")
    void getOrderTracking_notOwnerCustomer_throwsAccessDenied() {
        String orderId = "ord-srv-103";
        createOrder(orderId, customerAId, OrderStatus.IN_TRANSIT);

        assertThatThrownBy(() -> orderService.getOrderTracking(customerBToken, orderId))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("You do not have permission to view tracking");
    }

    @Test
    @Transactional
    @DisplayName("Order in PENDING status (driver not yet assigned) throws IllegalStateException")
    void getOrderTracking_pendingStatus_throwsIllegalState() {
        String orderId = "ord-srv-pending";
        createOrder(orderId, customerAId, OrderStatus.PENDING);

        assertThatThrownBy(() -> orderService.getOrderTracking(customerAToken, orderId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Order is not in a trackable state")
                .hasMessageContaining("Driver has not been assigned yet");
    }

    @Test
    @Transactional
    @DisplayName("Order in DELIVERED status throws IllegalStateException")
    void getOrderTracking_deliveredStatus_throwsIllegalState() {
        String orderId = "ord-srv-delivered";
        createOrder(orderId, customerAId, OrderStatus.DELIVERED);

        assertThatThrownBy(() -> orderService.getOrderTracking(customerAToken, orderId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Order is not in a trackable state")
                .hasMessageContaining("already been delivered");
    }

    @Test
    @Transactional
    @DisplayName("Order in CANCELLED status throws IllegalStateException")
    void getOrderTracking_cancelledStatus_throwsIllegalState() {
        String orderId = "ord-srv-cancelled";
        createOrder(orderId, customerAId, OrderStatus.CANCELLED);

        assertThatThrownBy(() -> orderService.getOrderTracking(customerAToken, orderId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Order is not in a trackable state")
                .hasMessageContaining("Order has been cancelled");
    }

    @Test
    @Transactional
    @DisplayName("When Tracking Service is unavailable, throws TrackingServiceUnavailableException")
    void getOrderTracking_trackingServiceUnavailable_throwsException() {
        String orderId = "ord-srv-down";
        createOrder(orderId, customerAId, OrderStatus.IN_TRANSIT);

        when(trackingServiceClient.getOrderTracking(anyString(), anyString(), anyBoolean()))
                .thenThrow(new TrackingServiceUnavailableException("Tracking Service is currently unavailable"));

        assertThatThrownBy(() -> orderService.getOrderTracking(customerAToken, orderId))
                .isInstanceOf(TrackingServiceUnavailableException.class)
                .hasMessageContaining("Tracking Service is currently unavailable");
    }

    @Test
    @DisplayName("Non-existent order throws OrderNotFoundException")
    void getOrderTracking_orderNotFound_throwsException() {
        assertThatThrownBy(() -> orderService.getOrderTracking(customerAToken, "non-existent-order"))
                .isInstanceOf(OrderNotFoundException.class)
                .hasMessageContaining("Order not found with identifier");
    }

    @Test
    @DisplayName("Unauthenticated call throws BadCredentialsException")
    void getOrderTracking_unauthenticated_throwsException() {
        assertThatThrownBy(() -> orderService.getOrderTracking(null, "ord-101"))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("Missing or invalid Authorization header");
    }
}
