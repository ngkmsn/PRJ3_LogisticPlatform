package com.logistics.order.service;

import com.logistics.common.model.OrderStatus;
import com.logistics.common.security.JwtTokenProvider;
import com.logistics.order.dto.CancelOrderRequest;
import com.logistics.order.dto.CreateOrderItemRequest;
import com.logistics.order.dto.CreateOrderRequest;
import com.logistics.order.dto.OrderDto;
import com.logistics.order.dto.UpdateOrderStatusRequest;
import com.logistics.order.entity.Order;
import com.logistics.order.exception.AccessDeniedException;
import com.logistics.order.exception.BadCredentialsException;
import com.logistics.order.exception.InvalidStateTransitionException;
import com.logistics.order.exception.OrderCancellationException;
import com.logistics.order.exception.OrderNotFoundException;
import com.logistics.order.dto.OrderStatusHistoryDto;
import com.logistics.order.entity.OrderStatusHistory;
import com.logistics.order.entity.OutboxEvent;
import com.logistics.order.entity.OutboxStatus;
import com.logistics.order.lifecycle.OrderLifecycleValidator;
import com.logistics.order.repository.OrderRepository;
import com.logistics.order.repository.OrderStatusHistoryRepository;
import com.logistics.order.repository.OutboxEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderStatusHistoryRepository statusHistoryRepository;

    @Mock
    private OutboxEventRepository outboxEventRepository;

    private JwtTokenProvider jwtTokenProvider;
    private OrderLifecycleValidator lifecycleValidator;
    private OrderService orderService;

    private String customerToken;
    private String driverToken;
    private String adminToken;
    private String dispatcherToken;

    @BeforeEach
    void setUp() {
        jwtTokenProvider = new JwtTokenProvider(
                "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                86400000L,
                "logistics-platform"
        );
        lifecycleValidator = new OrderLifecycleValidator();
        orderService = new OrderService(orderRepository, statusHistoryRepository, outboxEventRepository, jwtTokenProvider, lifecycleValidator);

        customerToken = "Bearer " + jwtTokenProvider.generateToken(
                "cust-uuid-1", "customer1", "customer1@logistics.com", "CUSTOMER"
        );
        driverToken = "Bearer " + jwtTokenProvider.generateToken(
                "driver-uuid-1", "driver1", "driver1@logistics.com", "DRIVER"
        );
        adminToken = "Bearer " + jwtTokenProvider.generateToken(
                "admin-uuid-1", "admin1", "admin1@logistics.com", "ADMIN"
        );
        dispatcherToken = "Bearer " + jwtTokenProvider.generateToken(
                "dispatcher-uuid-1", "dispatcher1", "dispatcher1@logistics.com", "DISPATCHER"
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

    @Test
    @DisplayName("Customer can get order details when they are the owner")
    void getOrderDetails_ownerSuccess_returnsOrderDto() {
        Order order = new Order("ord-1", "ORD-123", "cust-uuid-1", OrderStatus.PENDING, "Người nhận", "0901234567", "Hà Nội", new BigDecimal("150000.00"), "Ghi chú");
        org.mockito.Mockito.when(orderRepository.findByIdOrOrderNumber("ord-1")).thenReturn(Optional.of(order));

        OrderDto result = orderService.getOrderDetails(customerToken, "ord-1");

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo("ord-1");
        assertThat(result.getOrderNumber()).isEqualTo("ORD-123");
        assertThat(result.getCustomerId()).isEqualTo("cust-uuid-1");
        assertThat(result.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(result.getRecipientName()).isEqualTo("Người nhận");
        assertThat(result.getTotalAmount()).isEqualByComparingTo("150000.00");
    }

    @Test
    @DisplayName("Customer can get order details using orderNumber identifier")
    void getOrderDetails_byOrderNumber_success() {
        Order order = new Order("ord-1", "ORD-123", "cust-uuid-1", OrderStatus.PENDING, "Người nhận", "0901234567", "Hà Nội", new BigDecimal("150000.00"), "Ghi chú");
        org.mockito.Mockito.when(orderRepository.findByIdOrOrderNumber("ORD-123")).thenReturn(Optional.of(order));

        OrderDto result = orderService.getOrderDetails(customerToken, "ORD-123");

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo("ord-1");
        assertThat(result.getOrderNumber()).isEqualTo("ORD-123");
    }

    @Test
    @DisplayName("Customer trying to view another customer's order is rejected with AccessDeniedException (403)")
    void getOrderDetails_notOwner_forbidden() {
        Order anotherCustomersOrder = new Order("ord-2", "ORD-456", "other-customer-uuid", OrderStatus.PENDING, "Người nhận", "0901234567", "Hà Nội", new BigDecimal("200000.00"), null);
        org.mockito.Mockito.when(orderRepository.findByIdOrOrderNumber("ord-2")).thenReturn(Optional.of(anotherCustomersOrder));

        assertThatThrownBy(() -> orderService.getOrderDetails(customerToken, "ord-2"))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("You do not have permission to view this order");
    }

    @Test
    @DisplayName("Non-existent order throws OrderNotFoundException (404)")
    void getOrderDetails_notFound_throwsOrderNotFoundException() {
        org.mockito.Mockito.when(orderRepository.findByIdOrOrderNumber("non-existent")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.getOrderDetails(customerToken, "non-existent"))
                .isInstanceOf(OrderNotFoundException.class)
                .hasMessageContaining("Order not found with identifier: non-existent");
    }

    @Test
    @DisplayName("Empty order identifier throws IllegalArgumentException (400)")
    void getOrderDetails_emptyIdentifier_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> orderService.getOrderDetails(customerToken, "   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Order identifier cannot be empty");
    }

    @Test
    @DisplayName("Non-customer role (DRIVER) is rejected with AccessDeniedException when getting order details")
    void getOrderDetails_nonCustomer_forbidden() {
        assertThatThrownBy(() -> orderService.getOrderDetails(driverToken, "ord-1"))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Only customers are permitted to view order details");
    }

    @Test
    @DisplayName("Unauthenticated request is rejected with BadCredentialsException when getting order details")
    void getOrderDetails_unauthenticated_rejected() {
        assertThatThrownBy(() -> orderService.getOrderDetails(null, "ord-1"))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("Missing or invalid");
    }

    // =========================================================================
    // PB-019: Customer cancel order tests
    // =========================================================================

    @Test
    @DisplayName("Customer can cancel their own PENDING order successfully, status becomes CANCELLED")
    void cancelOrder_pendingOrder_success() {
        Order order = new Order("ord-1", "ORD-123", "cust-uuid-1", OrderStatus.PENDING,
                "Người nhận", "0901234567", "Hà Nội", new BigDecimal("150000.00"), "Giao sáng");
        org.mockito.Mockito.when(orderRepository.findByIdOrOrderNumber("ord-1")).thenReturn(Optional.of(order));

        OrderDto cancelled = orderService.cancelOrder(customerToken, "ord-1", null);

        assertThat(cancelled).isNotNull();
        assertThat(cancelled.getId()).isEqualTo("ord-1");
        assertThat(cancelled.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        verify(orderRepository).persist(order);
    }

    @Test
    @DisplayName("Customer can cancel order with reason and reason is appended to notes")
    void cancelOrder_withReason_appendsNotes() {
        Order order = new Order("ord-1", "ORD-123", "cust-uuid-1", OrderStatus.PENDING,
                "Người nhận", "0901234567", "Hà Nội", new BigDecimal("150000.00"), "Giao sáng");
        org.mockito.Mockito.when(orderRepository.findByIdOrOrderNumber("ord-1")).thenReturn(Optional.of(order));

        CancelOrderRequest request = new CancelOrderRequest("Đặt nhầm địa chỉ giao hàng");
        OrderDto cancelled = orderService.cancelOrder(customerToken, "ord-1", request);

        assertThat(cancelled.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(cancelled.getNotes()).contains("Giao sáng [Cancelled: Đặt nhầm địa chỉ giao hàng]");
        verify(orderRepository).persist(order);
    }

    @Test
    @DisplayName("Customer cannot cancel an order that belongs to another customer (403 AccessDeniedException)")
    void cancelOrder_notOwner_forbidden() {
        Order anotherCustomersOrder = new Order("ord-2", "ORD-456", "other-customer-uuid", OrderStatus.PENDING,
                "Người nhận", "0901234567", "Hà Nội", new BigDecimal("200000.00"), null);
        org.mockito.Mockito.when(orderRepository.findByIdOrOrderNumber("ord-2")).thenReturn(Optional.of(anotherCustomersOrder));

        assertThatThrownBy(() -> orderService.cancelOrder(customerToken, "ord-2", null))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("You do not have permission to cancel this order");
    }

    @Test
    @DisplayName("Non-customer role (DRIVER) is rejected with AccessDeniedException when cancelling order")
    void cancelOrder_driverRole_forbidden() {
        assertThatThrownBy(() -> orderService.cancelOrder(driverToken, "ord-1", null))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Only customers are permitted to cancel orders");
    }

    @Test
    @DisplayName("Unauthenticated request is rejected with BadCredentialsException when cancelling order")
    void cancelOrder_unauthenticated_rejected() {
        assertThatThrownBy(() -> orderService.cancelOrder(null, "ord-1", null))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("Missing or invalid Authorization header");
    }

    @Test
    @DisplayName("Non-existent order throws OrderNotFoundException (404) when cancelling")
    void cancelOrder_notFound_throwsOrderNotFoundException() {
        org.mockito.Mockito.when(orderRepository.findByIdOrOrderNumber("non-existent")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.cancelOrder(customerToken, "non-existent", null))
                .isInstanceOf(OrderNotFoundException.class)
                .hasMessageContaining("Order not found with identifier: non-existent");
    }

    @Test
    @DisplayName("Attempting to cancel already CANCELLED order throws OrderCancellationException")
    void cancelOrder_alreadyCancelled_throwsException() {
        Order order = new Order("ord-1", "ORD-123", "cust-uuid-1", OrderStatus.CANCELLED,
                "Người nhận", "0901234567", "Hà Nội", new BigDecimal("150000.00"), null);
        org.mockito.Mockito.when(orderRepository.findByIdOrOrderNumber("ord-1")).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.cancelOrder(customerToken, "ord-1", null))
                .isInstanceOf(OrderCancellationException.class)
                .hasMessageContaining("Order is already cancelled");
    }

    @Test
    @DisplayName("Attempting to cancel IN_TRANSIT order throws OrderCancellationException")
    void cancelOrder_inTransit_throwsException() {
        Order order = new Order("ord-1", "ORD-123", "cust-uuid-1", OrderStatus.IN_TRANSIT,
                "Người nhận", "0901234567", "Hà Nội", new BigDecimal("150000.00"), null);
        org.mockito.Mockito.when(orderRepository.findByIdOrOrderNumber("ord-1")).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.cancelOrder(customerToken, "ord-1", null))
                .isInstanceOf(OrderCancellationException.class)
                .hasMessageContaining("Order cannot be cancelled in status: IN_TRANSIT");
    }

    @Test
    @DisplayName("Attempting to cancel DELIVERED order throws OrderCancellationException")
    void cancelOrder_delivered_throwsException() {
        Order order = new Order("ord-1", "ORD-123", "cust-uuid-1", OrderStatus.DELIVERED,
                "Người nhận", "0901234567", "Hà Nội", new BigDecimal("150000.00"), null);
        org.mockito.Mockito.when(orderRepository.findByIdOrOrderNumber("ord-1")).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.cancelOrder(customerToken, "ord-1", null))
                .isInstanceOf(OrderCancellationException.class)
                .hasMessageContaining("Order cannot be cancelled in status: DELIVERED");
    }

    @Test
    @DisplayName("Attempting to cancel CONFIRMED order throws OrderCancellationException")
    void cancelOrder_confirmed_throwsException() {
        Order order = new Order("ord-1", "ORD-123", "cust-uuid-1", OrderStatus.CONFIRMED,
                "Người nhận", "0901234567", "Hà Nội", new BigDecimal("150000.00"), null);
        org.mockito.Mockito.when(orderRepository.findByIdOrOrderNumber("ord-1")).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.cancelOrder(customerToken, "ord-1", null))
                .isInstanceOf(OrderCancellationException.class)
                .hasMessageContaining("Order cannot be cancelled in status: CONFIRMED");
    }

    // =========================================================================
    // PB-020: Order lifecycle state transition tests
    // =========================================================================

    @Test
    @DisplayName("Admin can transition order from PENDING to CONFIRMED")
    void updateOrderStatus_adminSuccess_pendingToConfirmed() {
        Order order = new Order("ord-1", "ORD-123", "cust-1", OrderStatus.PENDING,
                "Người nhận", "0901234567", "Hà Nội", new BigDecimal("100000.00"), null);
        org.mockito.Mockito.when(orderRepository.findByIdOrOrderNumber("ord-1")).thenReturn(Optional.of(order));

        UpdateOrderStatusRequest req = new UpdateOrderStatusRequest(OrderStatus.CONFIRMED, "Đã kiểm tra kho");
        OrderDto result = orderService.updateOrderStatus(adminToken, "ord-1", req);

        assertThat(result.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(order.getNotes()).contains("[Status: PENDING -> CONFIRMED: Đã kiểm tra kho]");
        verify(orderRepository).persist(order);
    }

    @Test
    @DisplayName("Dispatcher can transition order from CONFIRMED to ASSIGNED")
    void updateOrderStatus_dispatcherSuccess_confirmedToAssigned() {
        Order order = new Order("ord-1", "ORD-123", "cust-1", OrderStatus.CONFIRMED,
                "Người nhận", "0901234567", "Hà Nội", new BigDecimal("100000.00"), null);
        org.mockito.Mockito.when(orderRepository.findByIdOrOrderNumber("ord-1")).thenReturn(Optional.of(order));

        UpdateOrderStatusRequest req = new UpdateOrderStatusRequest(OrderStatus.ASSIGNED, "Gán tài xế Nguyễn Văn A");
        OrderDto result = orderService.updateOrderStatus(dispatcherToken, "ord-1", req);

        assertThat(result.getStatus()).isEqualTo(OrderStatus.ASSIGNED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.ASSIGNED);
        verify(orderRepository).persist(order);
    }

    @Test
    @DisplayName("Dispatcher can transition order from ASSIGNED to IN_TRANSIT")
    void updateOrderStatus_assignedToInTransit() {
        Order order = new Order("ord-1", "ORD-123", "cust-1", OrderStatus.ASSIGNED,
                "Người nhận", "0901234567", "Hà Nội", new BigDecimal("100000.00"), null);
        org.mockito.Mockito.when(orderRepository.findByIdOrOrderNumber("ord-1")).thenReturn(Optional.of(order));

        UpdateOrderStatusRequest req = new UpdateOrderStatusRequest(OrderStatus.IN_TRANSIT);
        OrderDto result = orderService.updateOrderStatus(dispatcherToken, "ord-1", req);

        assertThat(result.getStatus()).isEqualTo(OrderStatus.IN_TRANSIT);
    }

    @Test
    @DisplayName("Admin can transition order from IN_TRANSIT to DELIVERED")
    void updateOrderStatus_inTransitToDelivered() {
        Order order = new Order("ord-1", "ORD-123", "cust-1", OrderStatus.IN_TRANSIT,
                "Người nhận", "0901234567", "Hà Nội", new BigDecimal("100000.00"), null);
        org.mockito.Mockito.when(orderRepository.findByIdOrOrderNumber("ord-1")).thenReturn(Optional.of(order));

        UpdateOrderStatusRequest req = new UpdateOrderStatusRequest(OrderStatus.DELIVERED);
        OrderDto result = orderService.updateOrderStatus(adminToken, "ord-1", req);

        assertThat(result.getStatus()).isEqualTo(OrderStatus.DELIVERED);
    }

    @Test
    @DisplayName("Dispatcher can transition order from ASSIGNED back to CONFIRMED (re-dispatch)")
    void updateOrderStatus_assignedToConfirmed_unassign() {
        Order order = new Order("ord-1", "ORD-123", "cust-1", OrderStatus.ASSIGNED,
                "Người nhận", "0901234567", "Hà Nội", new BigDecimal("100000.00"), null);
        org.mockito.Mockito.when(orderRepository.findByIdOrOrderNumber("ord-1")).thenReturn(Optional.of(order));

        UpdateOrderStatusRequest req = new UpdateOrderStatusRequest(OrderStatus.CONFIRMED, "Tài xế bận, gán lại");
        OrderDto result = orderService.updateOrderStatus(dispatcherToken, "ord-1", req);

        assertThat(result.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    }

    @Test
    @DisplayName("Invalid transition from DELIVERED to CANCELLED throws InvalidStateTransitionException")
    void updateOrderStatus_deliveredToCancelled_blocked() {
        Order order = new Order("ord-1", "ORD-123", "cust-1", OrderStatus.DELIVERED,
                "Người nhận", "0901234567", "Hà Nội", new BigDecimal("100000.00"), null);
        org.mockito.Mockito.when(orderRepository.findByIdOrOrderNumber("ord-1")).thenReturn(Optional.of(order));

        UpdateOrderStatusRequest req = new UpdateOrderStatusRequest(OrderStatus.CANCELLED);
        assertThatThrownBy(() -> orderService.updateOrderStatus(adminToken, "ord-1", req))
                .isInstanceOf(InvalidStateTransitionException.class)
                .hasMessageContaining("terminal status 'DELIVERED'");
    }

    @Test
    @DisplayName("Invalid transition to same status throws InvalidStateTransitionException")
    void updateOrderStatus_sameStatus_blocked() {
        Order order = new Order("ord-1", "ORD-123", "cust-1", OrderStatus.PENDING,
                "Người nhận", "0901234567", "Hà Nội", new BigDecimal("100000.00"), null);
        org.mockito.Mockito.when(orderRepository.findByIdOrOrderNumber("ord-1")).thenReturn(Optional.of(order));

        UpdateOrderStatusRequest req = new UpdateOrderStatusRequest(OrderStatus.PENDING);
        assertThatThrownBy(() -> orderService.updateOrderStatus(adminToken, "ord-1", req))
                .isInstanceOf(InvalidStateTransitionException.class)
                .hasMessageContaining("already in status PENDING");
    }

    @Test
    @DisplayName("Customer role is forbidden from calling updateOrderStatus (403)")
    void updateOrderStatus_customerRole_forbidden() {
        UpdateOrderStatusRequest req = new UpdateOrderStatusRequest(OrderStatus.CONFIRMED);
        assertThatThrownBy(() -> orderService.updateOrderStatus(customerToken, "ord-1", req))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Only administrators and dispatchers are permitted to update order status");
    }

    @Test
    @DisplayName("Driver role is forbidden from calling updateOrderStatus (403)")
    void updateOrderStatus_driverRole_forbidden() {
        UpdateOrderStatusRequest req = new UpdateOrderStatusRequest(OrderStatus.CONFIRMED);
        assertThatThrownBy(() -> orderService.updateOrderStatus(driverToken, "ord-1", req))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Only administrators and dispatchers are permitted to update order status");
    }

    @Test
    @DisplayName("Unauthenticated request is rejected when updating order status (401)")
    void updateOrderStatus_unauthenticated_rejected() {
        UpdateOrderStatusRequest req = new UpdateOrderStatusRequest(OrderStatus.CONFIRMED);
        assertThatThrownBy(() -> orderService.updateOrderStatus(null, "ord-1", req))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("Missing or invalid Authorization header");
    }

    @Test
    @DisplayName("Non-existent order throws OrderNotFoundException (404) when updating status")
    void updateOrderStatus_notFound_throwsException() {
        org.mockito.Mockito.when(orderRepository.findByIdOrOrderNumber("non-existent")).thenReturn(Optional.empty());

        UpdateOrderStatusRequest req = new UpdateOrderStatusRequest(OrderStatus.CONFIRMED);
        assertThatThrownBy(() -> orderService.updateOrderStatus(adminToken, "non-existent", req))
                .isInstanceOf(OrderNotFoundException.class)
                .hasMessageContaining("Order not found with identifier: non-existent");
    }

    // =========================================================================
    // PB-021: Order status history tests
    // =========================================================================

    @Test
    @DisplayName("Customer can retrieve history of their own order")
    void getOrderHistory_ownerCustomer_success() {
        Order order = new Order("ord-1", "ORD-100", "cust-uuid-1", OrderStatus.PENDING,
                "Người nhận", "0901234567", "Hà Nội", new BigDecimal("100000.00"), null);
        org.mockito.Mockito.when(orderRepository.findByIdOrOrderNumber("ord-1")).thenReturn(Optional.of(order));

        OrderStatusHistory h1 = new OrderStatusHistory(order, null, OrderStatus.PENDING, "cust-uuid-1", "CUSTOMER", "Order created");
        org.mockito.Mockito.when(statusHistoryRepository.findByOrderId("ord-1")).thenReturn(List.of(h1));

        List<OrderStatusHistoryDto> history = orderService.getOrderHistory(customerToken, "ord-1");

        assertThat(history).hasSize(1);
        assertThat(history.get(0).getNewStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(history.get(0).getPreviousStatus()).isNull();
        assertThat(history.get(0).getChangedBy()).isEqualTo("cust-uuid-1");
    }

    @Test
    @DisplayName("Admin or Dispatcher can retrieve history of any order")
    void getOrderHistory_adminOrDispatcher_success() {
        Order order = new Order("ord-2", "ORD-200", "other-cust", OrderStatus.CONFIRMED,
                "Người nhận", "0901234567", "Hà Nội", new BigDecimal("100000.00"), null);
        org.mockito.Mockito.when(orderRepository.findByIdOrOrderNumber("ord-2")).thenReturn(Optional.of(order));

        OrderStatusHistory h1 = new OrderStatusHistory(order, null, OrderStatus.PENDING, "other-cust", "CUSTOMER", "Created");
        OrderStatusHistory h2 = new OrderStatusHistory(order, OrderStatus.PENDING, OrderStatus.CONFIRMED, "admin-uuid-1", "ADMIN", "Approved");
        org.mockito.Mockito.when(statusHistoryRepository.findByOrderId("ord-2")).thenReturn(List.of(h1, h2));

        List<OrderStatusHistoryDto> history = orderService.getOrderHistory(adminToken, "ord-2");

        assertThat(history).hasSize(2);
        assertThat(history.get(1).getPreviousStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(history.get(1).getNewStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(history.get(1).getChangedByRole()).isEqualTo("ADMIN");
    }

    @Test
    @DisplayName("Customer cannot retrieve history of another customer's order (403 AccessDeniedException)")
    void getOrderHistory_otherCustomer_forbidden() {
        Order order = new Order("ord-3", "ORD-300", "other-customer-id", OrderStatus.PENDING,
                "Người nhận", "0901234567", "Hà Nội", new BigDecimal("100000.00"), null);
        org.mockito.Mockito.when(orderRepository.findByIdOrOrderNumber("ord-3")).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.getOrderHistory(customerToken, "ord-3"))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("You do not have permission to view history for this order");
    }

    @Test
    @DisplayName("Non-existent order throws OrderNotFoundException (404) when retrieving history")
    void getOrderHistory_notFound_throwsException() {
        org.mockito.Mockito.when(orderRepository.findByIdOrOrderNumber("non-existent")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.getOrderHistory(adminToken, "non-existent"))
                .isInstanceOf(OrderNotFoundException.class)
                .hasMessageContaining("Order not found with identifier: non-existent");
    }

    @Test
    @DisplayName("Unauthenticated request is rejected when retrieving order history (401)")
    void getOrderHistory_unauthenticated_rejected() {
        assertThatThrownBy(() -> orderService.getOrderHistory(null, "ord-1"))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("Missing or invalid Authorization header");
    }

    // =========================================================================
    // PB-022: Outbox event for ORDER_CREATED tests
    // =========================================================================

    @Test
    @DisplayName("PB-022: Creating order successfully persists ORDER_CREATED outbox event with full payload")
    void createOrder_success_persistsOutboxEventWithPayload() {
        CreateOrderRequest request = new CreateOrderRequest(
                "Lê Thị B",
                "0911223344",
                "456 Kim Mã, Ba Đình, Hà Nội",
                "Hàng dễ vỡ xin nhẹ tay",
                List.of(new CreateOrderItemRequest("Bình hoa gốm", 1, new BigDecimal("350000.00")))
        );

        OrderDto created = orderService.createOrder(customerToken, request);

        assertThat(created).isNotNull();

        ArgumentCaptor<OutboxEvent> outboxCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).persist(outboxCaptor.capture());

        OutboxEvent captured = outboxCaptor.getValue();
        assertThat(captured).isNotNull();
        assertThat(captured.getId()).isNotBlank();
        assertThat(captured.getAggregateType()).isEqualTo("ORDER");
        assertThat(captured.getAggregateId()).isEqualTo(created.getId());
        assertThat(captured.getEventType()).isEqualTo("ORDER_CREATED");
        assertThat(captured.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(captured.getRetryCount()).isZero();
        assertThat(captured.getOccurredAt()).isNotNull();

        assertThat(captured.getPayload()).contains(created.getId());
        assertThat(captured.getPayload()).contains(created.getOrderNumber());
        assertThat(captured.getPayload()).contains("cust-uuid-1");
        assertThat(captured.getPayload()).contains("Lê Thị B");
        assertThat(captured.getPayload()).contains("350000.00");
    }

    @Test
    @DisplayName("PB-022: If outbox persistence fails, an exception is thrown causing transaction rollback")
    void createOrder_outboxPersistenceFails_throwsExceptionAndRollsBack() {
        CreateOrderRequest request = new CreateOrderRequest(
                "Trần Văn C",
                "0933445566",
                "789 Giải Phóng, Hà Nội",
                null,
                List.of(new CreateOrderItemRequest("Sách hướng dẫn", 1, new BigDecimal("50000.00")))
        );

        doThrow(new RuntimeException("Database error saving outbox event"))
                .when(outboxEventRepository).persist(any(OutboxEvent.class));

        assertThatThrownBy(() -> orderService.createOrder(customerToken, request))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Database error saving outbox event");
    }

    @Test
    @DisplayName("PB-048: Dispatcher can successfully retrieve order metrics")
    void getOrderMetrics_dispatcher_returnsMetrics() {
        when(orderRepository.count()).thenReturn(100L);
        when(orderRepository.countCreatedSince(any())).thenReturn(25L);
        when(orderRepository.countByStatus(OrderStatus.PENDING)).thenReturn(10L);
        when(orderRepository.countByStatus(OrderStatus.CONFIRMED)).thenReturn(5L);
        when(orderRepository.countByStatus(OrderStatus.ASSIGNED)).thenReturn(15L);
        when(orderRepository.countByStatus(OrderStatus.IN_TRANSIT)).thenReturn(20L);
        when(orderRepository.countByStatus(OrderStatus.DELIVERED)).thenReturn(40L);
        when(orderRepository.countByStatus(OrderStatus.CANCELLED)).thenReturn(10L);

        Order sampleOrder = new Order();
        sampleOrder.setId("ord-sample-01");
        sampleOrder.setOrderNumber("ORD-20261022-001");
        sampleOrder.setCustomerId("cust-01");
        sampleOrder.setRecipientName("Nguyen Van A");
        sampleOrder.setDeliveryAddress("123 Le Loi, Hanoi");
        sampleOrder.setStatus(OrderStatus.PENDING);
        sampleOrder.setTotalAmount(new BigDecimal("100000.00"));
        sampleOrder.setCreatedAt(Instant.now());

        when(orderRepository.findRecentOrders(10)).thenReturn(List.of(sampleOrder));

        com.logistics.common.dto.OrderMetricsDto metrics = orderService.getOrderMetrics(dispatcherToken);

        assertThat(metrics).isNotNull();
        assertThat(metrics.getTotalOrders()).isEqualTo(100L);
        assertThat(metrics.getTodayOrders()).isEqualTo(25L);
        assertThat(metrics.getPendingOrders()).isEqualTo(10L);
        assertThat(metrics.getDeliveredOrders()).isEqualTo(40L);
        assertThat(metrics.getOrdersByStatus()).containsEntry("PENDING", 10L);
        assertThat(metrics.getRecentOrders()).hasSize(1);
        assertThat(metrics.getRecentOrders().get(0).getOrderNumber()).isEqualTo("ORD-20261022-001");
    }

    @Test
    @DisplayName("PB-048: Customer role is denied access to order metrics (403 Forbidden)")
    void getOrderMetrics_customerRole_throwsAccessDenied() {
        assertThatThrownBy(() -> orderService.getOrderMetrics(customerToken))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Operations or Admin role required");
    }

    @Test
    @DisplayName("PB-048: Missing or invalid token throws BadCredentialsException (401 Unauthorized)")
    void getOrderMetrics_invalidToken_throwsBadCredentials() {
        assertThatThrownBy(() -> orderService.getOrderMetrics("Bearer invalid.token.xyz"))
                .isInstanceOf(BadCredentialsException.class);
    }
}

