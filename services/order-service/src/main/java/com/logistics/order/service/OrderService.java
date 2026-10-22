package com.logistics.order.service;

import com.logistics.common.model.OrderStatus;
import com.logistics.common.security.AuthPrincipal;
import com.logistics.common.security.JwtTokenProvider;
import com.logistics.common.security.Role;
import com.logistics.order.dto.CancelOrderRequest;
import com.logistics.order.dto.CreateOrderItemRequest;
import com.logistics.order.dto.CreateOrderRequest;
import com.logistics.order.dto.OrderDto;
import com.logistics.order.entity.Order;
import com.logistics.order.entity.OrderItem;
import com.logistics.order.exception.AccessDeniedException;
import com.logistics.order.exception.BadCredentialsException;
import com.logistics.order.dto.OrderStatusHistoryDto;
import com.logistics.order.dto.UpdateOrderStatusRequest;
import com.logistics.order.entity.OrderStatusHistory;
import com.logistics.order.exception.OrderCancellationException;
import com.logistics.order.exception.OrderNotFoundException;
import com.logistics.order.lifecycle.OrderLifecycleValidator;
import com.logistics.order.repository.OrderRepository;
import com.logistics.order.repository.OrderStatusHistoryRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.logistics.order.dto.OrderCancelledEventPayload;
import com.logistics.order.dto.OrderCreatedEventPayload;
import com.logistics.order.entity.OutboxEvent;
import com.logistics.order.repository.OutboxEventRepository;

@ApplicationScoped
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss").withZone(ZoneOffset.UTC);

    private final OrderRepository orderRepository;
    private final OrderStatusHistoryRepository statusHistoryRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final OrderLifecycleValidator lifecycleValidator;
    private final ObjectMapper objectMapper;

    public OrderService(OrderRepository orderRepository, JwtTokenProvider jwtTokenProvider) {
        this(orderRepository, null, null, jwtTokenProvider, new OrderLifecycleValidator(), new ObjectMapper().findAndRegisterModules());
    }

    public OrderService(OrderRepository orderRepository,
                        JwtTokenProvider jwtTokenProvider,
                        OrderLifecycleValidator lifecycleValidator) {
        this(orderRepository, null, null, jwtTokenProvider, lifecycleValidator, new ObjectMapper().findAndRegisterModules());
    }

    public OrderService(OrderRepository orderRepository,
                        OrderStatusHistoryRepository statusHistoryRepository,
                        JwtTokenProvider jwtTokenProvider,
                        OrderLifecycleValidator lifecycleValidator) {
        this(orderRepository, statusHistoryRepository, null, jwtTokenProvider, lifecycleValidator, new ObjectMapper().findAndRegisterModules());
    }

    public OrderService(OrderRepository orderRepository,
                        OrderStatusHistoryRepository statusHistoryRepository,
                        OutboxEventRepository outboxEventRepository,
                        JwtTokenProvider jwtTokenProvider,
                        OrderLifecycleValidator lifecycleValidator) {
        this(orderRepository, statusHistoryRepository, outboxEventRepository, jwtTokenProvider, lifecycleValidator, new ObjectMapper().findAndRegisterModules());
    }

    @Inject
    public OrderService(OrderRepository orderRepository,
                        OrderStatusHistoryRepository statusHistoryRepository,
                        OutboxEventRepository outboxEventRepository,
                        JwtTokenProvider jwtTokenProvider,
                        OrderLifecycleValidator lifecycleValidator,
                        ObjectMapper objectMapper) {
        this.orderRepository = orderRepository;
        this.statusHistoryRepository = statusHistoryRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.jwtTokenProvider = jwtTokenProvider;
        this.lifecycleValidator = lifecycleValidator != null ? lifecycleValidator : new OrderLifecycleValidator();
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper().findAndRegisterModules();
    }

    public AuthPrincipal resolveAuthenticatedCustomer(String authHeader) {
        return resolveAuthenticatedCustomer(authHeader, "create orders");
    }

    public AuthPrincipal resolveAuthenticatedStaffOrAdmin(String authHeader, String action) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.warn("Authentication failed: missing or invalid Authorization header");
            throw new BadCredentialsException("Missing or invalid Authorization header");
        }

        String token = authHeader.substring(7).trim();
        if (!jwtTokenProvider.validateToken(token)) {
            log.warn("Authentication failed: token is invalid or expired");
            throw new BadCredentialsException("Token is invalid or expired");
        }

        AuthPrincipal principal = AuthPrincipal.fromJwtToken(token, jwtTokenProvider);
        if (principal == null) {
            throw new BadCredentialsException("Token identity could not be verified");
        }

        if (principal.getRole() != Role.ADMIN && principal.getRole() != Role.DISPATCHER) {
            log.warn("Access denied: caller role '{}' is not ADMIN or DISPATCHER for {}", principal.getRole(), action);
            throw new AccessDeniedException("Access denied: Only administrators and dispatchers are permitted to " + action);
        }

        return principal;
    }

    public AuthPrincipal resolveAuthenticatedCustomer(String authHeader, String action) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.warn("Authentication failed: missing or invalid Authorization header");
            throw new BadCredentialsException("Missing or invalid Authorization header");
        }

        String token = authHeader.substring(7).trim();
        if (!jwtTokenProvider.validateToken(token)) {
            log.warn("Authentication failed: token is invalid or expired");
            throw new BadCredentialsException("Token is invalid or expired");
        }

        AuthPrincipal principal = AuthPrincipal.fromJwtToken(token, jwtTokenProvider);
        if (principal == null) {
            throw new BadCredentialsException("Token identity could not be verified");
        }

        if (principal.getRole() != Role.CUSTOMER) {
            log.warn("Access denied: caller role '{}' is not CUSTOMER for {}", principal.getRole(), action);
            throw new AccessDeniedException("Access denied: Only customers are permitted to " + action);
        }

        return principal;
    }

    public com.logistics.order.dto.PagedResponse<OrderDto> listMyOrders(String authHeader, int page, int size, String statusFilter) {
        AuthPrincipal customer = resolveAuthenticatedCustomer(authHeader, "view orders");

        int pageIndex = Math.max(0, page);
        int pageSize = (size <= 0 || size > 100) ? 10 : size;

        StringBuilder query = new StringBuilder("customerId = :customerId");
        io.quarkus.panache.common.Parameters params = io.quarkus.panache.common.Parameters.with("customerId", customer.getUserId());

        if (statusFilter != null && !statusFilter.isBlank()) {
            try {
                OrderStatus status = OrderStatus.valueOf(statusFilter.trim().toUpperCase());
                query.append(" and status = :status");
                params.and("status", status);
            } catch (IllegalArgumentException e) {
                log.warn("Invalid order status filter provided: '{}'", statusFilter);
                throw new IllegalArgumentException("Invalid order status: " + statusFilter);
            }
        }

        var panacheQuery = orderRepository.find(query.toString() + " order by createdAt desc", params);
        long totalElements = panacheQuery.count();

        List<Order> orders = panacheQuery.page(io.quarkus.panache.common.Page.of(pageIndex, pageSize)).list();
        List<OrderDto> dtos = orders.stream()
                .map(OrderDto::from)
                .toList();

        log.info("Customer '{}' retrieved {} orders (page={}, size={}, totalElements={})",
                customer.getUserId(), dtos.size(), pageIndex, pageSize, totalElements);

        return com.logistics.order.dto.PagedResponse.of(dtos, pageIndex, pageSize, totalElements);
    }

    public OrderDto getOrderDetails(String authHeader, String orderIdentifier) {
        AuthPrincipal customer = resolveAuthenticatedCustomer(authHeader, "view order details");

        if (orderIdentifier == null || orderIdentifier.isBlank()) {
            throw new IllegalArgumentException("Order identifier cannot be empty");
        }

        Order order = orderRepository.findByIdOrOrderNumber(orderIdentifier.trim())
                .orElseThrow(() -> {
                    log.warn("Order not found with identifier '{}'", orderIdentifier);
                    return new OrderNotFoundException("Order not found with identifier: " + orderIdentifier);
                });

        // Strict ownership check: Must belong to the authenticated customer
        if (!customer.getUserId().equals(order.getCustomerId())) {
            log.warn("Access denied: Customer '{}' attempted to access order '{}' belonging to customer '{}'",
                    customer.getUserId(), order.getId(), order.getCustomerId());
            throw new AccessDeniedException("Access denied: You do not have permission to view this order");
        }

        log.info("Customer '{}' successfully retrieved order details for orderId='{}', orderNumber='{}'",
                customer.getUserId(), order.getId(), order.getOrderNumber());

        return OrderDto.from(order);
    }

    @Transactional
    public OrderDto createOrder(String authHeader, CreateOrderRequest request) {
        AuthPrincipal customer = resolveAuthenticatedCustomer(authHeader);

        if (request == null) {
            throw new IllegalArgumentException("Order request cannot be null");
        }

        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new IllegalArgumentException("Order must contain at least one item");
        }

        String orderId = UUID.randomUUID().toString();
        String orderNumber = generateOrderNumber();

        Order order = new Order();
        order.setId(orderId);
        order.setOrderNumber(orderNumber);
        order.setCustomerId(customer.getUserId());
        order.setStatus(OrderStatus.PENDING); // Always initialized as PENDING (PB-016 requirement)
        order.setRecipientName(request.getRecipientName().trim());
        order.setRecipientPhone(request.getRecipientPhone().trim());
        order.setDeliveryAddress(request.getDeliveryAddress().trim());
        order.setNotes(request.getNotes() != null ? request.getNotes().trim() : null);
        order.setCreatedAt(Instant.now());
        order.setUpdatedAt(Instant.now());

        BigDecimal totalAmount = BigDecimal.ZERO;

        for (CreateOrderItemRequest itemReq : request.getItems()) {
            if (itemReq.getQuantity() == null || itemReq.getQuantity() <= 0) {
                throw new IllegalArgumentException("Item quantity must be greater than zero");
            }
            if (itemReq.getUnitPrice() == null || itemReq.getUnitPrice().compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Item unit price must be greater than zero");
            }

            BigDecimal itemTotal = itemReq.getUnitPrice().multiply(BigDecimal.valueOf(itemReq.getQuantity()));
            totalAmount = totalAmount.add(itemTotal);

            OrderItem orderItem = new OrderItem(
                    UUID.randomUUID().toString(),
                    order,
                    itemReq.getProductName().trim(),
                    itemReq.getQuantity(),
                    itemReq.getUnitPrice(),
                    itemTotal
            );
            order.addItem(orderItem);
        }

        order.setTotalAmount(totalAmount);

        orderRepository.persist(order);
        recordStatusHistory(order, null, OrderStatus.PENDING, customer.getUserId(), "CUSTOMER", "Đơn hàng được khởi tạo thành công");
        recordOrderCreatedOutboxEvent(order);

        log.info("Order created successfully: orderId='{}', orderNumber='{}', customerId='{}', itemsCount={}, totalAmount={}",
                order.getId(), order.getOrderNumber(), order.getCustomerId(), order.getItems().size(), order.getTotalAmount());

        return OrderDto.from(order);
    }

    @Transactional
    public OrderDto cancelOrder(String authHeader, String orderIdentifier, CancelOrderRequest request) {
        AuthPrincipal customer = resolveAuthenticatedCustomer(authHeader, "cancel orders");

        if (orderIdentifier == null || orderIdentifier.isBlank()) {
            throw new IllegalArgumentException("Order identifier cannot be empty");
        }

        Order order = orderRepository.findByIdOrOrderNumber(orderIdentifier.trim())
                .orElseThrow(() -> {
                    log.warn("Order not found with identifier '{}'", orderIdentifier);
                    return new OrderNotFoundException("Order not found with identifier: " + orderIdentifier);
                });

        // Strict ownership check: Must belong to the authenticated customer
        if (!customer.getUserId().equals(order.getCustomerId())) {
            log.warn("Access denied: Customer '{}' attempted to cancel order '{}' belonging to customer '{}'",
                    customer.getUserId(), order.getId(), order.getCustomerId());
            throw new AccessDeniedException("Access denied: You do not have permission to cancel this order");
        }

        // Business rule check: Can only cancel order if it is in PENDING status
        if (order.getStatus() == OrderStatus.CANCELLED) {
            log.warn("Cancellation rejected: Order '{}' is already cancelled", order.getId());
            throw new OrderCancellationException("Order is already cancelled");
        }

        if (order.getStatus() != OrderStatus.PENDING) {
            log.warn("Cancellation rejected: Order '{}' in status '{}' cannot be cancelled",
                    order.getId(), order.getStatus());
            throw new OrderCancellationException("Order cannot be cancelled in status: " + order.getStatus() +
                    ". Only orders in PENDING status can be cancelled.");
        }

        // Validate lifecycle transition using the state machine
        lifecycleValidator.validateTransition(order.getStatus(), OrderStatus.CANCELLED);

        order.setStatus(OrderStatus.CANCELLED);
        order.setUpdatedAt(Instant.now());

        String cancelReason = (request != null && request.getReason() != null && !request.getReason().isBlank())
                ? request.getReason().trim()
                : "Khách hàng yêu cầu hủy đơn";

        String cancelNote = "[Cancelled: " + cancelReason + "]";
        if (order.getNotes() == null || order.getNotes().isBlank()) {
            order.setNotes(cancelNote);
        } else {
            order.setNotes(order.getNotes() + " " + cancelNote);
        }

        orderRepository.persist(order);
        recordStatusHistory(order, OrderStatus.PENDING, OrderStatus.CANCELLED, customer.getUserId(), "CUSTOMER", cancelReason);
        recordOrderCancelledOutboxEvent(order, cancelReason);

        log.info("Order cancelled successfully: orderId='{}', orderNumber='{}', customerId='{}'",
                order.getId(), order.getOrderNumber(), customer.getUserId());

        return OrderDto.from(order);
    }

    @Transactional
    public OrderDto updateOrderStatus(String authHeader, String orderIdentifier, UpdateOrderStatusRequest request) {
        AuthPrincipal staff = resolveAuthenticatedStaffOrAdmin(authHeader, "update order status");

        if (orderIdentifier == null || orderIdentifier.isBlank()) {
            throw new IllegalArgumentException("Order identifier cannot be empty");
        }

        if (request == null || request.getStatus() == null) {
            throw new IllegalArgumentException("Target order status is required");
        }

        Order order = orderRepository.findByIdOrOrderNumber(orderIdentifier.trim())
                .orElseThrow(() -> {
                    log.warn("Order not found with identifier '{}'", orderIdentifier);
                    return new OrderNotFoundException("Order not found with identifier: " + orderIdentifier);
                });

        OrderStatus currentStatus = order.getStatus();
        OrderStatus targetStatus = request.getStatus();

        // Validate state transition using the dedicated lifecycle state machine
        lifecycleValidator.validateTransition(currentStatus, targetStatus);

        order.setStatus(targetStatus);
        order.setUpdatedAt(Instant.now());

        String updateReason = (request.getReason() != null && !request.getReason().isBlank())
                ? request.getReason().trim()
                : "Cập nhật trạng thái vòng đời";

        String note = String.format("[Status: %s -> %s: %s]",
                currentStatus, targetStatus, updateReason);
        if (order.getNotes() == null || order.getNotes().isBlank()) {
            order.setNotes(note);
        } else {
            order.setNotes(order.getNotes() + " " + note);
        }

        orderRepository.persist(order);
        recordStatusHistory(order, currentStatus, targetStatus, staff.getUserId(), staff.getRole().name(), updateReason);

        log.info("Order status transitioned successfully: orderId='{}', orderNumber='{}', from='{}', to='{}', by='{}' ({})",
                order.getId(), order.getOrderNumber(), currentStatus, targetStatus, staff.getUserId(), staff.getRole());

        return OrderDto.from(order);
    }

    public List<OrderStatusHistoryDto> getOrderHistory(String authHeader, String orderIdentifier) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.warn("Authentication failed: missing or invalid Authorization header");
            throw new BadCredentialsException("Missing or invalid Authorization header");
        }

        String token = authHeader.substring(7).trim();
        if (!jwtTokenProvider.validateToken(token)) {
            log.warn("Authentication failed: token is invalid or expired");
            throw new BadCredentialsException("Token is invalid or expired");
        }

        AuthPrincipal principal = AuthPrincipal.fromJwtToken(token, jwtTokenProvider);
        if (principal == null) {
            throw new BadCredentialsException("Token identity could not be verified");
        }

        if (orderIdentifier == null || orderIdentifier.isBlank()) {
            throw new IllegalArgumentException("Order identifier cannot be empty");
        }

        Order order = orderRepository.findByIdOrOrderNumber(orderIdentifier.trim())
                .orElseThrow(() -> {
                    log.warn("Order not found with identifier '{}'", orderIdentifier);
                    return new OrderNotFoundException("Order not found with identifier: " + orderIdentifier);
                });

        // Strict ownership check for CUSTOMER
        if (principal.getRole() == Role.CUSTOMER && !principal.getUserId().equals(order.getCustomerId())) {
            log.warn("Access denied: Customer '{}' attempted to view history of order '{}' belonging to customer '{}'",
                    principal.getUserId(), order.getId(), order.getCustomerId());
            throw new AccessDeniedException("Access denied: You do not have permission to view history for this order");
        }

        List<OrderStatusHistory> histories;
        if (statusHistoryRepository != null) {
            histories = statusHistoryRepository.findByOrderId(order.getId());
        } else {
            histories = order.getHistory();
        }

        log.info("Retrieved {} history entries for order '{}' by user '{}' ({})",
                histories.size(), order.getId(), principal.getUserId(), principal.getRole());

        return histories.stream()
                .map(OrderStatusHistoryDto::from)
                .toList();
    }

    private OrderStatusHistory recordStatusHistory(Order order, OrderStatus previousStatus, OrderStatus newStatus,
                                                   String changedBy, String changedByRole, String reason) {
        OrderStatusHistory historyEntry = new OrderStatusHistory(
                UUID.randomUUID().toString(),
                order,
                order.getOrderNumber(),
                previousStatus,
                newStatus,
                changedBy,
                changedByRole,
                reason,
                Instant.now()
        );
        order.addHistory(historyEntry);
        if (statusHistoryRepository != null) {
            statusHistoryRepository.persist(historyEntry);
        }
        return historyEntry;
    }

    private void recordOrderCreatedOutboxEvent(Order order) {
        if (outboxEventRepository == null) {
            return;
        }
        try {
            OrderCreatedEventPayload payloadObj = OrderCreatedEventPayload.from(order);
            String payloadJson = objectMapper != null
                    ? objectMapper.writeValueAsString(payloadObj)
                    : "{}";

            OutboxEvent outboxEvent = new OutboxEvent(
                    "ORDER",
                    order.getId(),
                    "ORDER_CREATED",
                    payloadJson
            );

            outboxEventRepository.persist(outboxEvent);
            log.info("Recorded ORDER_CREATED outbox event: eventId='{}', orderId='{}', eventType='{}'",
                    outboxEvent.getId(), order.getId(), outboxEvent.getEventType());
        } catch (Exception e) {
            log.error("Failed to record ORDER_CREATED outbox event for orderId='{}'", order.getId(), e);
            if (e instanceof RuntimeException re) {
                throw re;
            }
            throw new IllegalStateException("Failed to record ORDER_CREATED outbox event: " + e.getMessage(), e);
        }
    }

    private void recordOrderCancelledOutboxEvent(Order order, String cancelReason) {
        if (outboxEventRepository == null) {
            return;
        }
        try {
            OrderCancelledEventPayload payloadObj = OrderCancelledEventPayload.from(order, cancelReason);
            String payloadJson = objectMapper != null
                    ? objectMapper.writeValueAsString(payloadObj)
                    : "{}";

            OutboxEvent outboxEvent = new OutboxEvent(
                    "ORDER",
                    order.getId(),
                    "ORDER_CANCELLED",
                    payloadJson
            );

            outboxEventRepository.persist(outboxEvent);
            log.info("Recorded ORDER_CANCELLED outbox event: eventId='{}', orderId='{}', eventType='{}'",
                    outboxEvent.getId(), order.getId(), outboxEvent.getEventType());
        } catch (Exception e) {
            log.error("Failed to record ORDER_CANCELLED outbox event for orderId='{}'", order.getId(), e);
            if (e instanceof RuntimeException re) {
                throw re;
            }
            throw new IllegalStateException("Failed to record ORDER_CANCELLED outbox event: " + e.getMessage(), e);
        }
    }

    private String generateOrderNumber() {
        String timestamp = DATE_FORMATTER.format(Instant.now());
        String randomSuffix = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        return "ORD-" + timestamp + "-" + randomSuffix;
    }

    @Inject
    com.logistics.order.client.TrackingServiceClient trackingServiceClient;

    public void setTrackingServiceClient(com.logistics.order.client.TrackingServiceClient trackingServiceClient) {
        this.trackingServiceClient = trackingServiceClient;
    }

    public com.logistics.common.dto.OrderTrackingDto getOrderTracking(String authHeader, String orderIdentifier) {
        return getOrderTracking(authHeader, orderIdentifier, false);
    }

    public com.logistics.common.dto.OrderTrackingDto getOrderTracking(String authHeader, String orderIdentifier, boolean allowStale) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.warn("Authentication failed: missing or invalid Authorization header");
            throw new BadCredentialsException("Missing or invalid Authorization header");
        }

        String token = authHeader.substring(7).trim();
        if (!jwtTokenProvider.validateToken(token)) {
            log.warn("Authentication failed: token is invalid or expired");
            throw new BadCredentialsException("Token is invalid or expired");
        }

        AuthPrincipal principal = AuthPrincipal.fromJwtToken(token, jwtTokenProvider);
        if (principal == null) {
            throw new BadCredentialsException("Token identity could not be verified");
        }

        if (orderIdentifier == null || orderIdentifier.isBlank()) {
            throw new IllegalArgumentException("Order identifier cannot be empty");
        }

        String trimmedIdentifier = orderIdentifier.trim();
        Order order = orderRepository.findByIdOrOrderNumber(trimmedIdentifier)
                .orElseThrow(() -> {
                    log.warn("Order not found with identifier '{}'", trimmedIdentifier);
                    return new OrderNotFoundException("Order not found with identifier: " + trimmedIdentifier);
                });

        // Strict ownership check for CUSTOMER
        if (principal.getRole() == Role.CUSTOMER && !principal.getUserId().equals(order.getCustomerId())) {
            log.warn("Access denied: Customer '{}' attempted to view tracking of order '{}' belonging to customer '{}'",
                    principal.getUserId(), order.getId(), order.getCustomerId());
            throw new AccessDeniedException("Access denied: You do not have permission to view tracking for this order");
        }

        // State check: Order must be in trackable state (ASSIGNED or IN_TRANSIT)
        OrderStatus status = order.getStatus();
        if (status != OrderStatus.ASSIGNED && status != OrderStatus.IN_TRANSIT) {
            if (status == OrderStatus.DELIVERED) {
                throw new IllegalStateException("Order is not in a trackable state: Order has already been delivered");
            }
            if (status == OrderStatus.CANCELLED) {
                throw new IllegalStateException("Order is not in a trackable state: Order has been cancelled");
            }
            throw new IllegalStateException("Order is not in a trackable state: Driver has not been assigned yet (current status: " + status + ")");
        }

        if (trackingServiceClient == null) {
            log.error("Tracking Service client is not available or unconfigured");
            throw new com.logistics.order.exception.TrackingServiceUnavailableException("Tracking Service is currently unavailable");
        }

        return trackingServiceClient.getOrderTracking(authHeader, order.getId(), allowStale);
    }

    public com.logistics.common.dto.OrderMetricsDto getOrderMetrics(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new BadCredentialsException("Missing or invalid Authorization header");
        }
        String token = authHeader.substring(7).trim();
        if (!jwtTokenProvider.validateToken(token)) {
            throw new BadCredentialsException("Invalid or expired token");
        }
        AuthPrincipal principal = AuthPrincipal.fromJwtToken(token, jwtTokenProvider);
        if (principal == null || (principal.getRole() != Role.DISPATCHER && principal.getRole() != Role.ADMIN)) {
            throw new AccessDeniedException("Access denied: Operations or Admin role required");
        }

        com.logistics.common.dto.OrderMetricsDto dto = new com.logistics.common.dto.OrderMetricsDto();
        dto.setTotalOrders(orderRepository.count());

        Instant startOfToday = java.time.LocalDate.now(ZoneOffset.UTC).atStartOfDay().toInstant(ZoneOffset.UTC);
        dto.setTodayOrders(orderRepository.countCreatedSince(startOfToday));

        dto.setPendingOrders(orderRepository.countByStatus(OrderStatus.PENDING));
        dto.setSearchingDriverOrders(0L);
        dto.setAssignedOrders(orderRepository.countByStatus(OrderStatus.ASSIGNED));
        dto.setPickedUpOrders(0L);
        dto.setInTransitOrders(orderRepository.countByStatus(OrderStatus.IN_TRANSIT));
        dto.setDeliveredOrders(orderRepository.countByStatus(OrderStatus.DELIVERED));
        dto.setFailedOrders(0L);
        dto.setCancelledOrders(orderRepository.countByStatus(OrderStatus.CANCELLED));

        java.util.Map<String, Long> statusMap = new java.util.HashMap<>();
        for (OrderStatus st : OrderStatus.values()) {
            statusMap.put(st.name(), orderRepository.countByStatus(st));
        }
        dto.setOrdersByStatus(statusMap);

        List<Order> recent = orderRepository.findRecentOrders(10);
        List<com.logistics.common.dto.OrderSummaryDto> summaryList = new java.util.ArrayList<>();
        if (recent != null) {
            for (Order o : recent) {
                summaryList.add(new com.logistics.common.dto.OrderSummaryDto(
                        o.getId(),
                        o.getOrderNumber(),
                        o.getCustomerId(),
                        o.getRecipientName(),
                        o.getDeliveryAddress(),
                        o.getStatus() != null ? o.getStatus().name() : null,
                        o.getTotalAmount(),
                        o.getCreatedAt()
                ));
            }
        }
        dto.setRecentOrders(summaryList);

        return dto;
    }
}

