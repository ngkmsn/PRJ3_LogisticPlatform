package com.logistics.user.exception;

import com.logistics.common.exception.LogisticsPlatformException;

public class AccountNotActiveException extends LogisticsPlatformException {

    public AccountNotActiveException(String status) {
        super("AUTH_ACCOUNT_NOT_ACTIVE", "User account is " + status.toLowerCase() + " and cannot log in");
    }
}
