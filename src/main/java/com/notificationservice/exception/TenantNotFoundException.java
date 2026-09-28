package com.notificationservice.exception;

public class TenantNotFoundException extends ResourceNotFoundException {
    public TenantNotFoundException(Long id) {
        super("Tenant not found: " + id);
    }
}
