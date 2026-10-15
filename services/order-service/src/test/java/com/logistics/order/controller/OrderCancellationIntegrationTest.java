package com.logistics.order.controller;

import com.logistics.common.model.OrderStatus;
import com.logistics.common.security.JwtTokenProvider;
import com.logistics.order.dto.CancelOrderRequest;
import com.logistics.order.dto.CreateOrderItemRequest;
import com.logistics.order.dto.CreateOrderRequest;
import com.logistics.order.entity.Order;
import com.logistics.order.repository.OrderRepository;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

@QuarkusTest
class OrderCancellationIntegrationTest {

    @Inject
    JwtTokenProvider jwtTokenProvider;

    @Inject
    OrderRepository orderRepository;

    private String customerTokenA;
    private String customerIdA;

    private String customerTokenB;
    private String customerIdB;

    private String driverToken;
    private String adminToken;

    @BeforeEach
    void setUp() {
        customerIdA = "aaaa1111-1111-1111-1111-aaaaaaaaaaaa";
        customerTokenA = "Bearer " + jwtTokenProvider.generateToken(
                customerIdA, "customerA", "customerA@logistics.com", "CUSTOMER"
        );

        customerIdB = "bbbb2222-2222-2222-2222-bbbbbbbbbbbb";
        customerTokenB = "Bearer " + jwtTokenProvider.generateToken(
                customerIdB, "customerB", "customerB@logistics.com", "CUSTOMER"
        );

        driverToken = "Bearer " + jwtTokenProvider.generateToken(
                "cccc3333-3333-3333-3333-cccccccccccc", "driver", "driver@logistics.com", "DRIVER"
        );

        adminToken = "Bearer " + jwtTokenProvider.generateToken(
                "dddd4444-4444-4444-4444-dddddddddddd", "admin", "admin@logistics.com", "ADMIN"
        );
    }

    private String createOrderAndGetId(String token, String recipientName, BigDecimal price) {
        CreateOrderRequest request = new CreateOrderRequest(
                recipientName,
                "0912345678",
                "123 Phố Huế, Hai Bà Trưng, Hà Nội",
                "Giao giờ hành chính",
                List.of(
                        new CreateOrderItemRequest("Sản phẩm A", 2, price),
                        new CreateOrderItemRequest("Sản phẩm B", 1, new BigDecimal("50000.00"))
                )
        );

        return given()
                .header("Authorization", token)
                .contentType(ContentType.JSON)
                .body(request)
                .when()
                .post("/orders")
                .then()
                .statusCode(201)
                .extract()
                .path("data.id");
    }

    @Transactional
    void updateOrderStatus(String orderId, OrderStatus status) {
        Order order = orderRepository.findById(orderId);
        if (order != null) {
            order.setStatus(status);
            orderRepository.persist(order);
        }
    }

    @Test
    @DisplayName("Customer can cancel their own PENDING order successfully via POST /orders/{id}/cancel")
    void cancelOrder_ownerSuccess() {
        String orderId = createOrderAndGetId(customerTokenA, "Lê Văn Hùng", new BigDecimal("100000.00"));

        given()
                .header("Authorization", customerTokenA)
                .when()
                .post("/orders/" + orderId + "/cancel")
                .then()
                .statusCode(200)
                .body("success", equalTo(true))
                .body("message", equalTo("Order cancelled successfully"))
                .body("data.id", equalTo(orderId))
                .body("data.customerId", equalTo(customerIdA))
                .body("data.status", equalTo("CANCELLED"))
                .body("data.updatedAt", notNullValue());
    }

    @Test
    @DisplayName("Customer can cancel order with optional reason via request body")
    void cancelOrder_withReason_success() {
        String orderId = createOrderAndGetId(customerTokenA, "Nguyễn Thị Mai", new BigDecimal("120000.00"));
        CancelOrderRequest cancelRequest = new CancelOrderRequest("Đặt nhầm địa chỉ giao hàng");

        given()
                .header("Authorization", customerTokenA)
                .contentType(ContentType.JSON)
                .body(cancelRequest)
                .when()
                .post("/orders/" + orderId + "/cancel")
                .then()
                .statusCode(200)
                .body("success", equalTo(true))
                .body("data.status", equalTo("CANCELLED"))
                .body("data.notes", containsString("[Cancelled: Đặt nhầm địa chỉ giao hàng]"));
    }

    @Test
    @DisplayName("Customer can cancel order by orderNumber identifier")
    void cancelOrder_byOrderNumber_success() {
        CreateOrderRequest request = new CreateOrderRequest(
                "Trần Văn Bình",
                "0987654321",
                "789 Kim Mã, Ba Đình, Hà Nội",
                null,
                List.of(new CreateOrderItemRequest("Sản phẩm C", 1, new BigDecimal("80000.00")))
        );

        String orderNumber = given()
                .header("Authorization", customerTokenA)
                .contentType(ContentType.JSON)
                .body(request)
                .when()
                .post("/orders")
                .then()
                .statusCode(201)
                .extract()
                .path("data.orderNumber");

        given()
                .header("Authorization", customerTokenA)
                .when()
                .post("/orders/" + orderNumber + "/cancel")
                .then()
                .statusCode(200)
                .body("success", equalTo(true))
                .body("data.orderNumber", equalTo(orderNumber))
                .body("data.status", equalTo("CANCELLED"));
    }

    @Test
    @DisplayName("Customer can cancel order via PUT method and /api/orders prefix")
    void cancelOrder_putAndApiPrefix_success() {
        String orderId = createOrderAndGetId(customerTokenA, "Hoàng Văn Nam", new BigDecimal("90000.00"));

        given()
                .header("Authorization", customerTokenA)
                .when()
                .put("/api/orders/" + orderId + "/cancel")
                .then()
                .statusCode(200)
                .body("success", equalTo(true))
                .body("data.status", equalTo("CANCELLED"));
    }

    @Test
    @DisplayName("Customer attempting to cancel another customer's order is rejected with 403 Forbidden")
    void cancelOrder_notOwner_forbidden() {
        String orderAId = createOrderAndGetId(customerTokenA, "Chủ đơn A", new BigDecimal("50000.00"));

        given()
                .header("Authorization", customerTokenB)
                .when()
                .post("/orders/" + orderAId + "/cancel")
                .then()
                .statusCode(403)
                .body("success", equalTo(false))
                .body("message", containsString("You do not have permission to cancel this order"));
    }

    @Test
    @DisplayName("Non-customer role (DRIVER) is rejected with 403 Forbidden when cancelling order")
    void cancelOrder_nonCustomer_forbidden() {
        String orderAId = createOrderAndGetId(customerTokenA, "Chủ đơn A", new BigDecimal("50000.00"));

        given()
                .header("Authorization", driverToken)
                .when()
                .post("/orders/" + orderAId + "/cancel")
                .then()
                .statusCode(403)
                .body("success", equalTo(false))
                .body("message", containsString("Only customers are permitted to cancel orders"));
    }

    @Test
    @DisplayName("Unauthenticated request is rejected with 401 Unauthorized")
    void cancelOrder_unauthenticated_rejected() {
        given()
                .when()
                .post("/orders/any-id/cancel")
                .then()
                .statusCode(401)
                .body("success", equalTo(false))
                .body("message", containsString("Missing or invalid Authorization header"));
    }

    @Test
    @DisplayName("Cancelling non-existent order returns 404 Not Found")
    void cancelOrder_notFound_404() {
        given()
                .header("Authorization", customerTokenA)
                .when()
                .post("/orders/non-existent-order-id/cancel")
                .then()
                .statusCode(404)
                .body("success", equalTo(false))
                .body("message", containsString("Order not found with identifier"));
    }

    @Test
    @DisplayName("Cancelling already CANCELLED order returns 400 Bad Request")
    void cancelOrder_alreadyCancelled_badRequest() {
        String orderId = createOrderAndGetId(customerTokenA, "Vũ Đình Toàn", new BigDecimal("200000.00"));

        // Cancel first time -> 200 OK
        given()
                .header("Authorization", customerTokenA)
                .when()
                .post("/orders/" + orderId + "/cancel")
                .then()
                .statusCode(200);

        // Cancel second time -> 400 Bad Request
        given()
                .header("Authorization", customerTokenA)
                .when()
                .post("/orders/" + orderId + "/cancel")
                .then()
                .statusCode(400)
                .body("success", equalTo(false))
                .body("message", containsString("Order is already cancelled"));
    }

    @Test
    @DisplayName("Cancelling order in IN_TRANSIT or DELIVERED status returns 400 Bad Request")
    void cancelOrder_inTransitOrDelivered_badRequest() {
        String orderId = createOrderAndGetId(customerTokenA, "Đặng Thu Thảo", new BigDecimal("300000.00"));

        // Simulate order moved to IN_TRANSIT
        updateOrderStatus(orderId, OrderStatus.IN_TRANSIT);

        given()
                .header("Authorization", customerTokenA)
                .when()
                .post("/orders/" + orderId + "/cancel")
                .then()
                .statusCode(400)
                .body("success", equalTo(false))
                .body("message", containsString("Order cannot be cancelled in status: IN_TRANSIT"));
    }
}
