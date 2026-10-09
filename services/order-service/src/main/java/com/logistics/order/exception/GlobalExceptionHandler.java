package com.logistics.order.exception;

import com.logistics.common.dto.ApiResponse;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.stream.Collectors;

public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @Provider
    public static class BadCredentialsMapper implements ExceptionMapper<BadCredentialsException> {
        @Override
        public Response toResponse(BadCredentialsException exception) {
            log.warn("Authentication failed: {}", exception.getMessage());
            return Response.status(Response.Status.UNAUTHORIZED)
                    .type(MediaType.APPLICATION_JSON)
                    .entity(ApiResponse.error(exception.getMessage()))
                    .build();
        }
    }

    @Provider
    public static class AccessDeniedMapper implements ExceptionMapper<AccessDeniedException> {
        @Override
        public Response toResponse(AccessDeniedException exception) {
            log.warn("Access denied: {}", exception.getMessage());
            return Response.status(Response.Status.FORBIDDEN)
                    .type(MediaType.APPLICATION_JSON)
                    .entity(ApiResponse.error(exception.getMessage()))
                    .build();
        }
    }

    @Provider
    public static class ValidationExceptionMapper implements ExceptionMapper<ConstraintViolationException> {
        @Override
        public Response toResponse(ConstraintViolationException exception) {
            String errorMessage = exception.getConstraintViolations().stream()
                    .map(ConstraintViolation::getMessage)
                    .collect(Collectors.joining("; "));
            if (errorMessage.isBlank()) {
                errorMessage = "Validation error";
            }
            log.warn("Validation error: {}", errorMessage);
            return Response.status(Response.Status.BAD_REQUEST)
                    .type(MediaType.APPLICATION_JSON)
                    .entity(ApiResponse.error(errorMessage))
                    .build();
        }
    }

    @Provider
    public static class IllegalArgumentExceptionMapper implements ExceptionMapper<IllegalArgumentException> {
        @Override
        public Response toResponse(IllegalArgumentException exception) {
            log.warn("Illegal argument: {}", exception.getMessage());
            return Response.status(Response.Status.BAD_REQUEST)
                    .type(MediaType.APPLICATION_JSON)
                    .entity(ApiResponse.error(exception.getMessage()))
                    .build();
        }
    }
}
