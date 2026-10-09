package com.logistics.order.service;

import com.logistics.common.model.OrderStatus;
import com.logistics.common.security.AuthPrincipal;
import com.logistics.common.security.JwtTokenProvider;
import com.logistics.common.security.Role;
import com.logistics.order.dto.CreateOrderItemRequest;
import com.logistics.order.dto.CreateOrderRequest;
import com.logistics.order.dto.OrderDto;
import com.logistics.order.entity.Order;
import com.logistics.order.entity.OrderItem;
import com.logistics.order.exception.AccessDeniedException;
import com.logistics.order.exception.BadCredentialsException;
import com.logistics.order.repository.OrderRepository;
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

@ApplicationScoped
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss").withZone(ZoneOffset.UTC);

    private final OrderRepository orderRepository;
    private final JwtTokenProvider jwtTokenProvider;

    @Inject
    public OrderService(OrderRepository orderRepository, JwtTokenProvider jwtTokenProvider) {
        this.orderRepository = orderRepository;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    public AuthPrincipal resolveAuthenticatedCustomer(String authHeader) {
        return resolveAuthenticatedCustomer(authHeader, "create orders");
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

        log.info("Order created successfully: orderId='{}', orderNumber='{}', customerId='{}', itemsCount={}, totalAmount={}",
                order.getId(), order.getOrderNumber(), order.getCustomerId(), order.getItems().size(), order.getTotalAmount());

        return OrderDto.from(order);
    }

    private String generateOrderNumber() {
        String timestamp = DATE_FORMATTER.format(Instant.now());
        String randomSuffix = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        return "ORD-" + timestamp + "-" + randomSuffix;
    }
}
