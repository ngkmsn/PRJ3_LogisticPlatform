package com.logistics.notification.client;

import java.util.UUID;

public class NotificationPlatformResult {

    private final boolean success;
    private final UUID platformNotificationId;
    private final String status;
    private final int statusCode;
    private final String errorMessage;
    private final Long retryAfterSeconds;
    private final int attempts;

    private NotificationPlatformResult(boolean success, UUID platformNotificationId, String status,
                                       int statusCode, String errorMessage, Long retryAfterSeconds, int attempts) {
        this.success = success;
        this.platformNotificationId = platformNotificationId;
        this.status = status;
        this.statusCode = statusCode;
        this.errorMessage = errorMessage;
        this.retryAfterSeconds = retryAfterSeconds;
        this.attempts = attempts;
    }

    public static NotificationPlatformResult success(UUID platformNotificationId, String status, int statusCode, int attempts) {
        return new NotificationPlatformResult(true, platformNotificationId, status, statusCode, null, null, attempts);
    }

    public static NotificationPlatformResult failure(int statusCode, String errorMessage, Long retryAfterSeconds, int attempts) {
        return new NotificationPlatformResult(false, null, "FAILED", statusCode, errorMessage, retryAfterSeconds, attempts);
    }

    public boolean isSuccess() {
        return success;
    }

    public UUID getPlatformNotificationId() {
        return platformNotificationId;
    }

    public String getStatus() {
        return status;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public Long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }

    public int getAttempts() {
        return attempts;
    }
}
