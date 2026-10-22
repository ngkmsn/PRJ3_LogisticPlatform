package com.logistics.shipment.exception;

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
    public static class AssignmentNotFoundMapper implements ExceptionMapper<AssignmentNotFoundException> {
        @Override
        public Response toResponse(AssignmentNotFoundException exception) {
            log.warn("Assignment not found: {}", exception.getMessage());
            return Response.status(Response.Status.NOT_FOUND)
                    .type(MediaType.APPLICATION_JSON)
                    .entity(ApiResponse.error(exception.getMessage()))
                    .build();
        }
    }

    @Provider
    public static class DuplicateAssignmentMapper implements ExceptionMapper<DuplicateAssignmentException> {
        @Override
        public Response toResponse(DuplicateAssignmentException exception) {
            log.warn("Duplicate assignment conflict: {}", exception.getMessage());
            return Response.status(Response.Status.CONFLICT)
                    .type(MediaType.APPLICATION_JSON)
                    .entity(ApiResponse.error(exception.getMessage()))
                    .build();
        }
    }

    @Provider
    public static class ValidationExceptionMapper implements ExceptionMapper<ConstraintViolationException> {
        @Override
        public Response toResponse(ConstraintViolationException exception) {
            String message = exception.getConstraintViolations().stream()
                    .map(ConstraintViolation::getMessage)
                    .collect(Collectors.joining(", "));
            log.warn("Validation failed: {}", message);
            return Response.status(Response.Status.BAD_REQUEST)
                    .type(MediaType.APPLICATION_JSON)
                    .entity(ApiResponse.error(message))
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

    @Provider
    public static class IllegalStateExceptionMapper implements ExceptionMapper<IllegalStateException> {
        @Override
        public Response toResponse(IllegalStateException exception) {
            log.warn("Illegal state: {}", exception.getMessage());
            return Response.status(Response.Status.BAD_REQUEST)
                    .type(MediaType.APPLICATION_JSON)
                    .entity(ApiResponse.error(exception.getMessage()))
                    .build();
        }
    }

    @Provider
    public static class TrackingServiceExceptionMapper implements ExceptionMapper<TrackingServiceException> {
        @Override
        public Response toResponse(TrackingServiceException exception) {
            log.error("Tracking service failure: {}", exception.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .type(MediaType.APPLICATION_JSON)
                    .entity(ApiResponse.error("Tracking service error: " + exception.getMessage()))
                    .build();
        }
    }

    @Provider
    public static class DriverDataProviderUnavailableMapper implements ExceptionMapper<DriverDataProviderUnavailableException> {
        @Override
        public Response toResponse(DriverDataProviderUnavailableException exception) {
            log.error("Driver data provider unavailable: {}", exception.getMessage());
            return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                    .type(MediaType.APPLICATION_JSON)
                    .entity(ApiResponse.error("Driver data provider is currently unavailable: " + exception.getMessage()))
                    .build();
        }
    }

    @Provider
    public static class DriverNotFoundMapper implements ExceptionMapper<DriverNotFoundException> {
        @Override
        public Response toResponse(DriverNotFoundException exception) {
            log.warn("Driver not found: {}", exception.getMessage());
            return Response.status(Response.Status.NOT_FOUND)
                    .type(MediaType.APPLICATION_JSON)
                    .entity(ApiResponse.error(exception.getMessage()))
                    .build();
        }
    }

    @Provider
    public static class MapInvalidRequestMapper implements ExceptionMapper<com.logistics.shipment.map.exception.MapInvalidRequestException> {
        @Override
        public Response toResponse(com.logistics.shipment.map.exception.MapInvalidRequestException exception) {
            log.warn("Map invalid request: {}", exception.getMessage());
            return Response.status(Response.Status.BAD_REQUEST)
                    .type(MediaType.APPLICATION_JSON)
                    .entity(ApiResponse.error("Invalid map request: " + exception.getMessage()))
                    .build();
        }
    }

    @Provider
    public static class MapConfigurationMapper implements ExceptionMapper<com.logistics.shipment.map.exception.MapConfigurationException> {
        @Override
        public Response toResponse(com.logistics.shipment.map.exception.MapConfigurationException exception) {
            log.error("Map configuration error: {}", exception.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .type(MediaType.APPLICATION_JSON)
                    .entity(ApiResponse.error("Map service configuration error: " + exception.getMessage()))
                    .build();
        }
    }

    @Provider
    public static class MapServiceTimeoutMapper implements ExceptionMapper<com.logistics.shipment.map.exception.MapServiceTimeoutException> {
        @Override
        public Response toResponse(com.logistics.shipment.map.exception.MapServiceTimeoutException exception) {
            log.error("Map service timeout: {}", exception.getMessage());
            return Response.status(504) // Gateway Timeout
                    .type(MediaType.APPLICATION_JSON)
                    .entity(ApiResponse.error("Map service timed out: " + exception.getMessage()))
                    .build();
        }
    }

    @Provider
    public static class MapServiceUnavailableMapper implements ExceptionMapper<com.logistics.shipment.map.exception.MapServiceUnavailableException> {
        @Override
        public Response toResponse(com.logistics.shipment.map.exception.MapServiceUnavailableException exception) {
            log.error("Map service unavailable: {}", exception.getMessage());
            return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                    .type(MediaType.APPLICATION_JSON)
                    .entity(ApiResponse.error("Map service unavailable: " + exception.getMessage()))
                    .build();
        }
    }

    @Provider
    public static class MapRateLimitMapper implements ExceptionMapper<com.logistics.shipment.map.exception.MapRateLimitException> {
        @Override
        public Response toResponse(com.logistics.shipment.map.exception.MapRateLimitException exception) {
            log.warn("Map rate limit exceeded: {}", exception.getMessage());
            return Response.status(429) // Too Many Requests
                    .type(MediaType.APPLICATION_JSON)
                    .entity(ApiResponse.error("Map request rate limit exceeded: " + exception.getMessage()))
                    .build();
        }
    }
}
