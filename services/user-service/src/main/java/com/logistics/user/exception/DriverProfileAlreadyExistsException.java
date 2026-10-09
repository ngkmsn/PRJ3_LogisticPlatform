package com.logistics.user.exception;

public class DriverProfileAlreadyExistsException extends RuntimeException {

    public DriverProfileAlreadyExistsException(String message) {
        super(message);
    }
}
