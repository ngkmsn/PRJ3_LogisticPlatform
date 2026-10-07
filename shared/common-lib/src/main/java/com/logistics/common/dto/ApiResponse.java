package com.logistics.common.dto;

/**
 * Generic API response wrapper used by all services.
 * <p>
 * Provides a consistent response envelope across the platform so consumers
 * always receive the same top-level structure regardless of which service
 * they are calling.
 *
 * @param <T> the type of the payload data
 */
public class ApiResponse<T> {

    private boolean success;
    private String message;
    private T data;

    // --- constructors ---

    public ApiResponse() {}

    public ApiResponse(boolean success, String message, T data) {
        this.success = success;
        this.message = message;
        this.data    = data;
    }

    // --- factory helpers ---

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, "OK", data);
    }

    public static <T> ApiResponse<T> ok(String message, T data) {
        return new ApiResponse<>(true, message, data);
    }

    public static <T> ApiResponse<T> error(String message) {
        return new ApiResponse<>(false, message, null);
    }

    // --- getters / setters ---

    public boolean isSuccess()             { return success; }
    public void    setSuccess(boolean s)   { this.success = s; }

    public String  getMessage()            { return message; }
    public void    setMessage(String m)    { this.message = m; }

    public T       getData()               { return data; }
    public void    setData(T data)         { this.data = data; }
}
