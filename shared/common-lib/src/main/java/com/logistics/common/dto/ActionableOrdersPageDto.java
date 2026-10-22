package com.logistics.common.dto;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/**
 * Paginated response container for actionable orders with summary counters (PB-049).
 */
public class ActionableOrdersPageDto implements Serializable {

    private static final long serialVersionUID = 1L;

    private List<ActionableOrderDto> content;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;

    // Summary counters for Operations UI badges
    private long totalActionable;
    private long unassignedCount;
    private long failedCount;

    public ActionableOrdersPageDto() {
        this.content = Collections.emptyList();
    }

    public ActionableOrdersPageDto(List<ActionableOrderDto> content, int page, int size,
                                  long totalElements, int totalPages,
                                  long totalActionable, long unassignedCount, long failedCount) {
        this.content = content != null ? content : Collections.emptyList();
        this.page = page;
        this.size = size;
        this.totalElements = totalElements;
        this.totalPages = totalPages;
        this.totalActionable = totalActionable;
        this.unassignedCount = unassignedCount;
        this.failedCount = failedCount;
    }

    public static ActionableOrdersPageDto of(List<ActionableOrderDto> content, int page, int size,
                                            long totalElements, long totalActionable,
                                            long unassignedCount, long failedCount) {
        int totalPages = size > 0 ? (int) Math.ceil((double) totalElements / size) : 0;
        return new ActionableOrdersPageDto(content, page, size, totalElements, totalPages,
                totalActionable, unassignedCount, failedCount);
    }

    public List<ActionableOrderDto> getContent() {
        return content;
    }

    public void setContent(List<ActionableOrderDto> content) {
        this.content = content != null ? content : Collections.emptyList();
    }

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }

    public long getTotalElements() {
        return totalElements;
    }

    public void setTotalElements(long totalElements) {
        this.totalElements = totalElements;
    }

    public int getTotalPages() {
        return totalPages;
    }

    public void setTotalPages(int totalPages) {
        this.totalPages = totalPages;
    }

    public long getTotalActionable() {
        return totalActionable;
    }

    public void setTotalActionable(long totalActionable) {
        this.totalActionable = totalActionable;
    }

    public long getUnassignedCount() {
        return unassignedCount;
    }

    public void setUnassignedCount(long unassignedCount) {
        this.unassignedCount = unassignedCount;
    }

    public long getFailedCount() {
        return failedCount;
    }

    public void setFailedCount(long failedCount) {
        this.failedCount = failedCount;
    }
}
