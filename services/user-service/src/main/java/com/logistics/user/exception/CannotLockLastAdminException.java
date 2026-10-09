package com.logistics.user.exception;

import com.logistics.common.exception.LogisticsPlatformException;

public class CannotLockLastAdminException extends LogisticsPlatformException {

    public CannotLockLastAdminException() {
        super("CANNOT_LOCK_LAST_ADMIN", "Cannot lock, deactivate or demote the last remaining active Administrator in the system");
    }

    public CannotLockLastAdminException(String message) {
        super("CANNOT_LOCK_LAST_ADMIN", message);
    }
}
