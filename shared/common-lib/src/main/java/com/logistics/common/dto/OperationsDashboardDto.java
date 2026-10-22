package com.logistics.common.dto;

import java.io.Serializable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Unified Operations Dashboard DTO providing comprehensive logistical KPIs (PB-048).
 */
public class OperationsDashboardDto implements Serializable {

    private static final long serialVersionUID = 1L;

    private OrderMetricsDto orderMetrics = new OrderMetricsDto();
    private DriverOperationalMetricsDto driverMetrics = new DriverOperationalMetricsDto();
    private long unassignedOrdersCount;
    private long failedOrdersCount;
    private List<AttentionItemDto> attentionItems = new ArrayList<>();
    private List<OrderSummaryDto> recentOrders = new ArrayList<>();
    private Instant generatedAt;
    private boolean cached;

    public OperationsDashboardDto() {
        this.generatedAt = Instant.now();
    }

    public OrderMetricsDto getOrderMetrics() {
        return orderMetrics;
    }

    public void setOrderMetrics(OrderMetricsDto orderMetrics) {
        this.orderMetrics = orderMetrics != null ? orderMetrics : new OrderMetricsDto();
    }

    public DriverOperationalMetricsDto getDriverMetrics() {
        return driverMetrics;
    }

    public void setDriverMetrics(DriverOperationalMetricsDto driverMetrics) {
        this.driverMetrics = driverMetrics != null ? driverMetrics : new DriverOperationalMetricsDto();
    }

    public long getUnassignedOrdersCount() {
        return unassignedOrdersCount;
    }

    public void setUnassignedOrdersCount(long unassignedOrdersCount) {
        this.unassignedOrdersCount = unassignedOrdersCount;
    }

    public long getFailedOrdersCount() {
        return failedOrdersCount;
    }

    public void setFailedOrdersCount(long failedOrdersCount) {
        this.failedOrdersCount = failedOrdersCount;
    }

    public List<AttentionItemDto> getAttentionItems() {
        return attentionItems;
    }

    public void setAttentionItems(List<AttentionItemDto> attentionItems) {
        this.attentionItems = attentionItems != null ? attentionItems : new ArrayList<>();
    }

    public List<OrderSummaryDto> getRecentOrders() {
        return recentOrders;
    }

    public void setRecentOrders(List<OrderSummaryDto> recentOrders) {
        this.recentOrders = recentOrders != null ? recentOrders : new ArrayList<>();
    }

    public Instant getGeneratedAt() {
        return generatedAt;
    }

    public void setGeneratedAt(Instant generatedAt) {
        this.generatedAt = generatedAt;
    }

    public boolean isCached() {
        return cached;
    }

    public void setCached(boolean cached) {
        this.cached = cached;
    }
}
