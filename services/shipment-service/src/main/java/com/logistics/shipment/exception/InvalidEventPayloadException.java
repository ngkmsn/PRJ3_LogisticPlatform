package com.logistics.shipment.exception;

/**
 * Thrown when an incoming Kafka event payload is malformed, missing mandatory fields,
 * or fails domain validation.
 */
public class InvalidEventPayloadException extends RuntimeException {

    public InvalidEventPayloadException(String message) {
        super(message);
    }

    public InvalidEventPayloadException(String message, Throwable cause) {
        super(message, cause);
    }
}
