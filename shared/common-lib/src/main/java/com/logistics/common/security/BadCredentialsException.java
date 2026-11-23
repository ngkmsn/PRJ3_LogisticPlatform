package com.logistics.common.security;

import com.logistics.common.exception.LogisticsPlatformException;

public class BadCredentialsException extends LogisticsPlatformException {

    public BadCredentialsException(String message) {
        super("AUTH_UNAUTHORIZED", message);
    }

    public BadCredentialsException(String errorCode, String message) {
        super(errorCode != null ? errorCode : "AUTH_UNAUTHORIZED", message);
    }
}

