package com.logistics.common.security;

public class ServiceTokenValidationResult {

    public enum Status {
        SUCCESS,
        MISSING_TOKEN,
        INVALID_SIGNATURE,
        EXPIRED_TOKEN,
        INVALID_ISSUER,
        AUDIENCE_MISMATCH,
        INSUFFICIENT_SCOPE,
        INVALID_IDENTITY
    }

    private final boolean valid;
    private final Status status;
    private final String message;
    private final ServicePrincipal principal;

    private ServiceTokenValidationResult(boolean valid, Status status, String message, ServicePrincipal principal) {
        this.valid = valid;
        this.status = status;
        this.message = message;
        this.principal = principal;
    }

    public static ServiceTokenValidationResult success(ServicePrincipal principal) {
        return new ServiceTokenValidationResult(true, Status.SUCCESS, "Token is valid", principal);
    }

    public static ServiceTokenValidationResult failure(Status status, String message) {
        return new ServiceTokenValidationResult(false, status, message, null);
    }

    public boolean isValid() {
        return valid;
    }

    public Status getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }

    public ServicePrincipal getPrincipal() {
        return principal;
    }
}

