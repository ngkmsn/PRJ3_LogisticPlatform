package com.logistics.user.exception;

import com.logistics.common.exception.LogisticsPlatformException;

public class UserNotFoundException extends LogisticsPlatformException {

    public UserNotFoundException() {
        super("USER_NOT_FOUND", "User not found");
    }

    public UserNotFoundException(String message) {
        super("USER_NOT_FOUND", message);
    }
}
