package com.logistics.user.exception;

import com.logistics.common.exception.LogisticsPlatformException;

public class BadCredentialsException extends LogisticsPlatformException {

    public BadCredentialsException() {
        super("AUTH_INVALID_CREDENTIALS", "Invalid username or password");
    }

    public BadCredentialsException(String message) {
        super("AUTH_INVALID_CREDENTIALS", message);
    }
}
