package com.logistics.user.exception;

public class LicenseAlreadyRegisteredException extends RuntimeException {

    public LicenseAlreadyRegisteredException(String message) {
        super(message);
    }
}
