package org.fourz.rvnkcore.api.model.response;

import java.util.List;

/**
 * Generic paginated response wrapper for REST API.
 * Provides pagination metadata along with data.
 */
public class PagedResponse<T> {
    private List<T> data;
    private int offset;
    private int limit;
    private int total;
    private boolean hasMore;

    // Default constructor for JSON deserialization
    public PagedResponse() {}

    public PagedResponse(List<T> data, int offset, int limit, int total) {
        this.data = data;
        this.offset = offset;
        this.limit = limit;
        this.total = total;
        this.hasMore = (offset + limit) < total;
    }

    // Getters
    public List<T> getData() { return data; }
    public int getOffset() { return offset; }
    public int getLimit() { return limit; }
    public int getTotal() { return total; }
    public boolean isHasMore() { return hasMore; }

    // Setters for JSON deserialization
    public void setData(List<T> data) { this.data = data; }
    public void setOffset(int offset) { this.offset = offset; }
    public void setLimit(int limit) { this.limit = limit; }
    public void setTotal(int total) { this.total = total; }
    public void setHasMore(boolean hasMore) { this.hasMore = hasMore; }
}
