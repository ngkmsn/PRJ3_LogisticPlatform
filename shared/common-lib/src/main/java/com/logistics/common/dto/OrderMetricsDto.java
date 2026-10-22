package com.logistics.common.dto;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Aggregated operational metrics for Orders in the Logistics Platform (PB-048).
 */
public class OrderMetricsDto implements Serializable {

    private static final long serialVersionUID = 1L;

    private long totalOrders;
    private long todayOrders;
    private long pendingOrders;
    private long searchingDriverOrders;
    private long assignedOrders;
    private long pickedUpOrders;
    private long inTransitOrders;
    private long deliveredOrders;
    private long failedOrders;
    private long cancelledOrders;
    private Map<String, Long> ordersByStatus = new HashMap<>();
    private List<OrderSummaryDto> recentOrders = new ArrayList<>();

    public OrderMetricsDto() {
    }

    public long getTotalOrders() {
        return totalOrders;
    }

    public void setTotalOrders(long totalOrders) {
        this.totalOrders = totalOrders;
    }

    public long getTodayOrders() {
        return todayOrders;
    }

    public void setTodayOrders(long todayOrders) {
        this.todayOrders = todayOrders;
    }

    public long getPendingOrders() {
        return pendingOrders;
    }

    public void setPendingOrders(long pendingOrders) {
        this.pendingOrders = pendingOrders;
    }

    public long getSearchingDriverOrders() {
        return searchingDriverOrders;
    }

    public void setSearchingDriverOrders(long searchingDriverOrders) {
        this.searchingDriverOrders = searchingDriverOrders;
    }

    public long getAssignedOrders() {
        return assignedOrders;
    }

    public void setAssignedOrders(long assignedOrders) {
        this.assignedOrders = assignedOrders;
    }

    public long getPickedUpOrders() {
        return pickedUpOrders;
    }

    public void setPickedUpOrders(long pickedUpOrders) {
        this.pickedUpOrders = pickedUpOrders;
    }

    public long getInTransitOrders() {
        return inTransitOrders;
    }

    public void setInTransitOrders(long inTransitOrders) {
        this.inTransitOrders = inTransitOrders;
    }

    public long getDeliveredOrders() {
        return deliveredOrders;
    }

    public void setDeliveredOrders(long deliveredOrders) {
        this.deliveredOrders = deliveredOrders;
    }

    public long getFailedOrders() {
        return failedOrders;
    }

    public void setFailedOrders(long failedOrders) {
        this.failedOrders = failedOrders;
    }

    public long getCancelledOrders() {
        return cancelledOrders;
    }

    public void setCancelledOrders(long cancelledOrders) {
        this.cancelledOrders = cancelledOrders;
    }

    public Map<String, Long> getOrdersByStatus() {
        return ordersByStatus;
    }

    public void setOrdersByStatus(Map<String, Long> ordersByStatus) {
        this.ordersByStatus = ordersByStatus != null ? ordersByStatus : new HashMap<>();
    }

    public List<OrderSummaryDto> getRecentOrders() {
        return recentOrders;
    }

    public void setRecentOrders(List<OrderSummaryDto> recentOrders) {
        this.recentOrders = recentOrders != null ? recentOrders : new ArrayList<>();
    }
}
