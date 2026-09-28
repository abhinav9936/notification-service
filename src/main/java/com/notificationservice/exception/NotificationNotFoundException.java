package com.notificationservice.exception;

public class NotificationNotFoundException extends ResourceNotFoundException {
    public NotificationNotFoundException(Long id) {
        super("Notification not found: " + id);
    }
}
