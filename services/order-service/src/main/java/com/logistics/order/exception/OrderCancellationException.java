package com.logistics.order.exception;

/**
 * Exception thrown when an order cancellation request violates business rules,
 * such as attempting to cancel an order that is not in a cancellable state.
 */
public class OrderCancellationException extends InvalidStateTransitionException {

    public OrderCancellationException(String message) {
        super(message);
    }

    public OrderCancellationException(String message, Throwable cause) {
        super(message);
        if (cause != null) {
            initCause(cause);
        }
    }
}
