package com.logistics.user.exception;

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
    public static class AccountNotActiveMapper implements ExceptionMapper<AccountNotActiveException> {
        @Override
        public Response toResponse(AccountNotActiveException exception) {
            log.warn("Inactive account login attempt: {}", exception.getMessage());
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
    public static class UserNotFoundMapper implements ExceptionMapper<UserNotFoundException> {
        @Override
        public Response toResponse(UserNotFoundException exception) {
            log.warn("User not found: {}", exception.getMessage());
            return Response.status(Response.Status.NOT_FOUND)
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
    public static class UserAlreadyExistsMapper implements ExceptionMapper<UserAlreadyExistsException> {
        @Override
        public Response toResponse(UserAlreadyExistsException exception) {
            log.warn("User conflict: {}", exception.getMessage());
            return Response.status(Response.Status.CONFLICT)
                    .type(MediaType.APPLICATION_JSON)
                    .entity(ApiResponse.error(exception.getMessage()))
                    .build();
        }
    }

    @Provider
    public static class CannotLockLastAdminMapper implements ExceptionMapper<CannotLockLastAdminException> {
        @Override
        public Response toResponse(CannotLockLastAdminException exception) {
            log.warn("Invalid admin operation: {}", exception.getMessage());
            return Response.status(Response.Status.BAD_REQUEST)
                    .type(MediaType.APPLICATION_JSON)
                    .entity(ApiResponse.error(exception.getMessage()))
                    .build();
        }
    }
}
