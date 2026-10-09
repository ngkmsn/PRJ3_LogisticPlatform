package com.logistics.user.exception;

import com.logistics.common.exception.LogisticsPlatformException;

public class UserAlreadyExistsException extends LogisticsPlatformException {

    public UserAlreadyExistsException(String message) {
        super("USER_ALREADY_EXISTS", message);
    }
}
