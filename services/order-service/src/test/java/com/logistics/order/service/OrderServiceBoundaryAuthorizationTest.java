package com.logistics.order.service;

import com.logistics.common.model.OrderStatus;
import com.logistics.common.security.JwtTokenProvider;
import com.logistics.common.security.ServiceTokenProvider;
import com.logistics.order.dto.CancelOrderRequest;
import com.logistics.order.dto.CreateOrderItemRequest;
import com.logistics.order.dto.CreateOrderRequest;
import com.logistics.order.dto.OrderDto;
import com.logistics.order.dto.UpdateOrderStatusRequest;
import com.logistics.order.entity.Order;
import com.logistics.order.entity.OrderItem;
import com.logistics.order.exception.AccessDeniedException;
import com.logistics.order.exception.BadCredentialsException;
import com.logistics.order.repository.OrderRepository;
import com.logistics.order.repository.OrderStatusHistoryRepository;
import io.quarkus.panache.common.Parameters;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectMock;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@QuarkusTest
class OrderServiceBoundaryAuthorizationTest {

    private static final String SECRET = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";

    @Inject
    OrderService orderService;

    @Inject
    JwtTokenProvider jwtTokenProvider;

    @InjectMock
    OrderRepository orderRepository;

    @InjectMock
    OrderStatusHistoryRepository statusHistoryRepository;

    private ServiceTokenProvider serviceTokenProvider;

    private String customerAToken;
    private String customerBToken;
    private String driverToken;
    private String dispatcherToken;
    private String adminToken;

    private Order orderCustomerA;

    @BeforeEach
    void setUp() {
        serviceTokenProvider = new ServiceTokenProvider("shipment-service", SECRET, "shipment-service", 300L);

        customerAToken = "Bearer " + jwtTokenProvider.generateToken("cust-A", "alice", "alice@test.com", "CUSTOMER");
        customerBToken = "Bearer " + jwtTokenProvider.generateToken("cust-B", "bob", "bob@test.com", "CUSTOMER");
        driverToken = "Bearer " + jwtTokenProvider.generateToken("drv-1", "charlie", "drv@test.com", "DRIVER");
        dispatcherToken = "Bearer " + jwtTokenProvider.generateToken("disp-1", "dave", "disp@test.com", "DISPATCHER");
        adminToken = "Bearer " + jwtTokenProvider.generateToken("adm-1", "admin", "admin@test.com", "ADMIN");

        orderCustomerA = new Order();
        orderCustomerA.setId("ord-100");
        orderCustomerA.setOrderNumber("ORD-NUM-100");
        orderCustomerA.setCustomerId("cust-A");
        orderCustomerA.setStatus(OrderStatus.PENDING);
        orderCustomerA.setRecipientName("Alice Recipient");
        orderCustomerA.setRecipientPhone("0901234567");
        orderCustomerA.setDeliveryAddress("123 District 1, HCMC");
        orderCustomerA.setTotalAmount(BigDecimal.valueOf(100000));
        orderCustomerA.setCreatedAt(Instant.now());
        orderCustomerA.setUpdatedAt(Instant.now());

        when(orderRepository.findByIdOrOrderNumber("ord-100")).thenReturn(Optional.of(orderCustomerA));
        when(orderRepository.findByIdOrOrderNumber("ORD-NUM-100")).thenReturn(Optional.of(orderCustomerA));
    }

    @Test
    @DisplayName("PB-058 [401]: Missing or invalid token fails authentication at boundary")
    void unauthenticated_rejected() {
        assertThatThrownBy(() -> orderService.getOrderDetails(null, "ord-100"))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("Missing or invalid Authorization header");

        assertThatThrownBy(() -> orderService.getOrderDetails("Bearer invalid-token", "ord-100"))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("Token is invalid or expired");
    }

    @Test
    @DisplayName("PB-058 [Ownership]: Customer A can read their own order details")
    void customerOwner_readOrderDetails_success() {
        OrderDto dto = orderService.getOrderDetails(customerAToken, "ord-100");
        assertThat(dto).isNotNull();
        assertThat(dto.getId()).isEqualTo("ord-100");
        assertThat(dto.getCustomerId()).isEqualTo("cust-A");
    }

    @Test
    @DisplayName("PB-058 [IDOR Prevention]: Customer B cannot read Customer A's order")
    void customerB_readOrderCustomerA_rejectedWith403() {
        assertThatThrownBy(() -> orderService.getOrderDetails(customerBToken, "ord-100"))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Access denied: You do not have permission to view this order");
    }

    @Test
    @DisplayName("PB-058 [RBAC]: Driver role cannot access order details directly")
    void driver_readOrderDetails_rejectedWith403() {
        assertThatThrownBy(() -> orderService.getOrderDetails(driverToken, "ord-100"))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Only customers and operations staff are permitted to view order details");
    }

    @Test
    @DisplayName("PB-058 [Ownership]: Customer A can cancel their own PENDING order")
    void customerOwner_cancelOrder_success() {
        CancelOrderRequest req = new CancelOrderRequest();
        req.setReason("Changed mind");

        OrderDto dto = orderService.cancelOrder(customerAToken, "ord-100", req);
        assertThat(dto).isNotNull();
        assertThat(dto.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    @DisplayName("PB-058 [IDOR Prevention]: Customer B cannot cancel Customer A's order")
    void customerB_cancelOrderCustomerA_rejectedWith403() {
        CancelOrderRequest req = new CancelOrderRequest();
        req.setReason("Malicious cancellation attempt");

        assertThatThrownBy(() -> orderService.cancelOrder(customerBToken, "ord-100", req))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Access denied: You do not have permission to cancel this order");
    }

    @Test
    @DisplayName("PB-058 [RBAC]: Customers and Drivers cannot update order lifecycle status")
    void nonStaff_updateOrderStatus_rejectedWith403() {
        UpdateOrderStatusRequest req = new UpdateOrderStatusRequest();
        req.setStatus(OrderStatus.ASSIGNED);
        req.setReason("Manual transition");

        // Customer cannot update status
        assertThatThrownBy(() -> orderService.updateOrderStatus(customerAToken, "ord-100", req))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Only administrators and dispatchers are permitted to update order status");

        // Driver cannot update status directly via order-service
        assertThatThrownBy(() -> orderService.updateOrderStatus(driverToken, "ord-100", req))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Only administrators and dispatchers are permitted to update order status");
    }

    @Test
    @DisplayName("PB-058 [RBAC]: Dispatcher and Admin can update order lifecycle status")
    void staff_updateOrderStatus_success() {
        UpdateOrderStatusRequest req = new UpdateOrderStatusRequest();
        req.setStatus(OrderStatus.CONFIRMED);
        req.setReason("Confirmed order");

        OrderDto dto = orderService.updateOrderStatus(dispatcherToken, "ord-100", req);
        assertThat(dto).isNotNull();
        assertThat(dto.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    }

    @Test
    @DisplayName("PB-058 [Ownership]: Customer A can view history of their order, stranger cannot")
    void orderHistory_ownershipEnforcement() {
        when(statusHistoryRepository.findByOrderId("ord-100")).thenReturn(List.of());

        // Owner customer A succeeds
        assertThatCode(() -> orderService.getOrderHistory(customerAToken, "ord-100"))
                .doesNotThrowAnyException();

        // Dispatcher / Admin succeeds
        assertThatCode(() -> orderService.getOrderHistory(dispatcherToken, "ord-100"))
                .doesNotThrowAnyException();
        assertThatCode(() -> orderService.getOrderHistory(adminToken, "ord-100"))
                .doesNotThrowAnyException();

        // Stranger customer B is rejected
        assertThatThrownBy(() -> orderService.getOrderHistory(customerBToken, "ord-100"))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("You do not have permission to view history for this order");

        // Driver is rejected (DRIVER role does not own the order and is not operations staff)
        assertThatThrownBy(() -> orderService.getOrderHistory(driverToken, "ord-100"))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("is not permitted to view order history");
    }

    @Test
    @DisplayName("PB-058 [S2S Authorization]: Service caller with valid scope succeeds; wrong audience or missing scope fails")
    void serviceToService_boundaryEnforcement() {
        // Valid S2S token with audience 'order-service' and scope 'order:read'
        String validS2SToken = "Bearer " + serviceTokenProvider.generateToken("order-service", Set.of("order:read"));
        OrderDto details = orderService.getOrderDetails(validS2SToken, "ord-100");
        assertThat(details).isNotNull();
        assertThat(details.getId()).isEqualTo("ord-100");

        // S2S token with mismatched audience
        String wrongAudToken = "Bearer " + serviceTokenProvider.generateToken("user-service", Set.of("order:read"));
        assertThatThrownBy(() -> orderService.getOrderDetails(wrongAudToken, "ord-100"))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Invalid token audience: expected order-service");

        // S2S token missing required scope
        String missingScopeToken = "Bearer " + serviceTokenProvider.generateToken("order-service", Set.of("other:scope"));
        assertThatThrownBy(() -> orderService.getOrderDetails(missingScopeToken, "ord-100"))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Insufficient service permissions: required scope order:read");
    }
}
