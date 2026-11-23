package com.logistics.common.security;

import com.logistics.common.exception.LogisticsPlatformException;

public class AccessDeniedException extends LogisticsPlatformException {

    public AccessDeniedException(String message) {
        super("AUTH_ACCESS_DENIED", message);
    }

    public AccessDeniedException(String errorCode, String message) {
        super(errorCode != null ? errorCode : "AUTH_ACCESS_DENIED", message);
    }
}

