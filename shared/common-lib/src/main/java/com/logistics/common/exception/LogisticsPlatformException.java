package com.logistics.common.exception;

/**
 * Base runtime exception for the logistics platform.
 * <p>
 * All service-specific exceptions should extend this class so that
 * cross-cutting concerns (e.g. a future global exception handler) can
 * catch platform exceptions uniformly.
 */
public class LogisticsPlatformException extends RuntimeException {

    private final String errorCode;

    public LogisticsPlatformException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public LogisticsPlatformException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
