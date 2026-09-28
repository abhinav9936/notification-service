package com.notificationservice.exception;

public class TransientDeliveryException extends RuntimeException {
    public TransientDeliveryException(String message) {
        super(message);
    }
}
