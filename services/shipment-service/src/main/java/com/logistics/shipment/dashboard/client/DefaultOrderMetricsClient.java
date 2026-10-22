package com.logistics.shipment.dashboard.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.logistics.common.dto.OrderMetricsDto;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;

@ApplicationScoped
@DefaultBean
public class DefaultOrderMetricsClient implements OrderMetricsClient {

    private static final Logger log = LoggerFactory.getLogger(DefaultOrderMetricsClient.class);

    @ConfigProperty(name = "logistics.order.service.url", defaultValue = "http://localhost:8082")
    String orderServiceUrl;

    @Inject
    ObjectMapper objectMapper;

    private HttpClient httpClient;

    public DefaultOrderMetricsClient() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();
    }

    public DefaultOrderMetricsClient(String orderServiceUrl, ObjectMapper objectMapper, HttpClient httpClient) {
        this.orderServiceUrl = orderServiceUrl;
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
    }

    @Override
    public Optional<OrderMetricsDto> getOrderMetrics(String authHeader) {
        String base = orderServiceUrl != null ? orderServiceUrl.trim() : "http://localhost:8082";
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        String endpoint = base + "/api/orders/metrics";
        log.info("DefaultOrderMetricsClient: fetching order metrics from '{}'", endpoint);

        try {
            HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .timeout(Duration.ofSeconds(4))
                    .header("Accept", "application/json")
                    .GET();

            if (authHeader != null && !authHeader.isBlank()) {
                reqBuilder.header("Authorization", authHeader);
            }

            HttpResponse<String> response = httpClient.send(reqBuilder.build(), HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();

            if (status == 200) {
                if (objectMapper == null) {
                    objectMapper = new ObjectMapper().findAndRegisterModules();
                }
                JsonNode root = objectMapper.readTree(response.body());
                JsonNode dataNode = root.path("data");
                if (!dataNode.isMissingNode() && !dataNode.isNull()) {
                    OrderMetricsDto dto = objectMapper.treeToValue(dataNode, OrderMetricsDto.class);
                    return Optional.ofNullable(dto);
                }
            } else {
                log.warn("DefaultOrderMetricsClient: order-service responded with HTTP {}", status);
            }
        } catch (Exception e) {
            log.error("DefaultOrderMetricsClient: failed to fetch order metrics from order-service: {}", e.getMessage());
        }
        return Optional.empty();
    }
}
