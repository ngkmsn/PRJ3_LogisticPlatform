package com.logistics.user.exception;

import com.logistics.common.exception.LogisticsPlatformException;

public class AccessDeniedException extends LogisticsPlatformException {

    public AccessDeniedException() {
        super("AUTH_ACCESS_DENIED", "Access denied: Administrator privileges required");
    }

    public AccessDeniedException(String message) {
        super("AUTH_ACCESS_DENIED", message);
    }
}
