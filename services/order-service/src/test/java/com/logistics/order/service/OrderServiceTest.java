package com.logistics.order.service;

import com.logistics.common.model.OrderStatus;
import com.logistics.common.security.JwtTokenProvider;
import com.logistics.order.dto.CreateOrderItemRequest;
import com.logistics.order.dto.CreateOrderRequest;
import com.logistics.order.dto.OrderDto;
import com.logistics.order.entity.Order;
import com.logistics.order.exception.AccessDeniedException;
import com.logistics.order.exception.BadCredentialsException;
import com.logistics.order.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    private JwtTokenProvider jwtTokenProvider;
    private OrderService orderService;

    private String customerToken;
    private String driverToken;
    private String adminToken;

    @BeforeEach
    void setUp() {
        jwtTokenProvider = new JwtTokenProvider(
                "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                86400000L,
                "logistics-platform"
        );
        orderService = new OrderService(orderRepository, jwtTokenProvider);

        customerToken = "Bearer " + jwtTokenProvider.generateToken(
                "cust-uuid-1", "customer1", "customer1@logistics.com", "CUSTOMER"
        );
        driverToken = "Bearer " + jwtTokenProvider.generateToken(
                "driver-uuid-1", "driver1", "driver1@logistics.com", "DRIVER"
        );
        adminToken = "Bearer " + jwtTokenProvider.generateToken(
                "admin-uuid-1", "admin1", "admin1@logistics.com", "ADMIN"
        );
    }

    @Test
    @DisplayName("Customer can create a valid order, status is always initialized to PENDING")
    void createOrder_customerSuccess_statusIsPending() {
        CreateOrderRequest request = new CreateOrderRequest(
                "Nguyễn Văn Nhận",
                "0988776655",
                "123 Hoàng Hoa Thám, Ba Đình, Hà Nội",
                "Giao giờ hành chính",
                List.of(
                        new CreateOrderItemRequest("Kiện hàng A", 2, new BigDecimal("150000.00")),
                        new CreateOrderItemRequest("Kiện hàng B", 1, new BigDecimal("200000.00"))
                )
        );

        OrderDto created = orderService.createOrder(customerToken, request);

        assertThat(created).isNotNull();
        assertThat(created.getId()).isNotBlank();
        assertThat(created.getOrderNumber()).startsWith("ORD-");
        assertThat(created.getCustomerId()).isEqualTo("cust-uuid-1");
        assertThat(created.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(created.getRecipientName()).isEqualTo("Nguyễn Văn Nhận");
        assertThat(created.getRecipientPhone()).isEqualTo("0988776655");
        assertThat(created.getDeliveryAddress()).isEqualTo("123 Hoàng Hoa Thám, Ba Đình, Hà Nội");
        assertThat(created.getNotes()).isEqualTo("Giao giờ hành chính");
        assertThat(created.getItems()).hasSize(2);
        // Total = (2 * 150,000) + (1 * 200,000) = 500,000
        assertThat(created.getTotalAmount()).isEqualByComparingTo("500000.00");

        verify(orderRepository).persist(any(Order.class));
    }

    @Test
    @DisplayName("Non-customer role (DRIVER) is rejected with AccessDeniedException")
    void createOrder_driverRole_forbidden() {
        CreateOrderRequest request = new CreateOrderRequest(
                "Người Nhận", "0900000000", "Địa chỉ", null,
                List.of(new CreateOrderItemRequest("Hàng", 1, new BigDecimal("10000.00")))
        );

        assertThatThrownBy(() -> orderService.createOrder(driverToken, request))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Only customers are permitted to create orders");
    }

    @Test
    @DisplayName("Non-customer role (ADMIN) is rejected with AccessDeniedException")
    void createOrder_adminRole_forbidden() {
        CreateOrderRequest request = new CreateOrderRequest(
                "Người Nhận", "0900000000", "Địa chỉ", null,
                List.of(new CreateOrderItemRequest("Hàng", 1, new BigDecimal("10000.00")))
        );

        assertThatThrownBy(() -> orderService.createOrder(adminToken, request))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Only customers are permitted to create orders");
    }

    @Test
    @DisplayName("Unauthenticated request (missing header) is rejected with BadCredentialsException")
    void createOrder_unauthenticated_rejected() {
        CreateOrderRequest request = new CreateOrderRequest(
                "Người Nhận", "0900000000", "Địa chỉ", null,
                List.of(new CreateOrderItemRequest("Hàng", 1, new BigDecimal("10000.00")))
        );

        assertThatThrownBy(() -> orderService.createOrder(null, request))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("Missing or invalid");
    }

    @Test
    @DisplayName("Order with empty items list throws IllegalArgumentException")
    void createOrder_emptyItems_throwsException() {
        CreateOrderRequest request = new CreateOrderRequest(
                "Người Nhận", "0900000000", "Địa chỉ", null, Collections.emptyList()
        );

        assertThatThrownBy(() -> orderService.createOrder(customerToken, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Order must contain at least one item");
    }

    @Test
    @DisplayName("Item with zero or negative quantity throws IllegalArgumentException")
    void createOrder_invalidItemQuantity_throwsException() {
        CreateOrderRequest request = new CreateOrderRequest(
                "Người Nhận", "0900000000", "Địa chỉ", null,
                List.of(new CreateOrderItemRequest("Hàng", 0, new BigDecimal("10000.00")))
        );

        assertThatThrownBy(() -> orderService.createOrder(customerToken, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("quantity must be greater than zero");
    }

    @Test
    @DisplayName("Item with zero or negative unit price throws IllegalArgumentException")
    void createOrder_invalidItemUnitPrice_throwsException() {
        CreateOrderRequest request = new CreateOrderRequest(
                "Người Nhận", "0900000000", "Địa chỉ", null,
                List.of(new CreateOrderItemRequest("Hàng", 1, BigDecimal.ZERO))
        );

        assertThatThrownBy(() -> orderService.createOrder(customerToken, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unit price must be greater than zero");
    }

    @Test
    @DisplayName("Customer can list their orders with pagination and data isolation")
    void listMyOrders_customerSuccess_returnsPagedOrders() {
        @SuppressWarnings("unchecked")
        io.quarkus.hibernate.orm.panache.PanacheQuery<Order> panacheQuery = org.mockito.Mockito.mock(io.quarkus.hibernate.orm.panache.PanacheQuery.class);
        org.mockito.Mockito.when(orderRepository.find(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(io.quarkus.panache.common.Parameters.class)))
                .thenReturn(panacheQuery);
        org.mockito.Mockito.when(panacheQuery.count()).thenReturn(1L);
        org.mockito.Mockito.when(panacheQuery.page(org.mockito.ArgumentMatchers.any(io.quarkus.panache.common.Page.class)))
                .thenReturn(panacheQuery);

        Order order = new Order("ord-1", "ORD-123", "cust-uuid-1", OrderStatus.PENDING, "Người nhận", "0901234567", "Hà Nội", new BigDecimal("100000.00"), "Ghi chú");
        org.mockito.Mockito.when(panacheQuery.list()).thenReturn(List.of(order));

        var response = orderService.listMyOrders(customerToken, 0, 10, null);

        assertThat(response).isNotNull();
        assertThat(response.getContent()).hasSize(1);
        assertThat(response.getContent().get(0).getId()).isEqualTo("ord-1");
        assertThat(response.getContent().get(0).getOrderNumber()).isEqualTo("ORD-123");
        assertThat(response.getTotalElements()).isEqualTo(1L);
        assertThat(response.getTotalPages()).isEqualTo(1);
        assertThat(response.getPage()).isEqualTo(0);
        assertThat(response.getSize()).isEqualTo(10);
    }

    @Test
    @DisplayName("Customer can filter orders by valid OrderStatus")
    void listMyOrders_withStatusFilter_success() {
        @SuppressWarnings("unchecked")
        io.quarkus.hibernate.orm.panache.PanacheQuery<Order> panacheQuery = org.mockito.Mockito.mock(io.quarkus.hibernate.orm.panache.PanacheQuery.class);
        org.mockito.Mockito.when(orderRepository.find(org.mockito.ArgumentMatchers.contains("status = :status"), org.mockito.ArgumentMatchers.any(io.quarkus.panache.common.Parameters.class)))
                .thenReturn(panacheQuery);
        org.mockito.Mockito.when(panacheQuery.count()).thenReturn(0L);
        org.mockito.Mockito.when(panacheQuery.page(org.mockito.ArgumentMatchers.any(io.quarkus.panache.common.Page.class)))
                .thenReturn(panacheQuery);
        org.mockito.Mockito.when(panacheQuery.list()).thenReturn(Collections.emptyList());

        var response = orderService.listMyOrders(customerToken, 0, 10, "PENDING");

        assertThat(response).isNotNull();
        assertThat(response.getContent()).isEmpty();
        assertThat(response.getTotalElements()).isEqualTo(0L);
    }

    @Test
    @DisplayName("Invalid status filter throws IllegalArgumentException")
    void listMyOrders_invalidStatus_throwsException() {
        assertThatThrownBy(() -> orderService.listMyOrders(customerToken, 0, 10, "UNKNOWN_STATUS"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid order status: UNKNOWN_STATUS");
    }

    @Test
    @DisplayName("Non-customer role (DRIVER) is rejected with AccessDeniedException when listing orders")
    void listMyOrders_driverRole_forbidden() {
        assertThatThrownBy(() -> orderService.listMyOrders(driverToken, 0, 10, null))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Only customers are permitted to view orders");
    }

    @Test
    @DisplayName("Unauthenticated request (missing header) is rejected with BadCredentialsException when listing orders")
    void listMyOrders_unauthenticated_rejected() {
        assertThatThrownBy(() -> orderService.listMyOrders(null, 0, 10, null))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("Missing or invalid");
    }
}
