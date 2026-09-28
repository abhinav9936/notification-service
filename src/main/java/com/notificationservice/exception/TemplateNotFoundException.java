package com.notificationservice.exception;

public class TemplateNotFoundException extends ResourceNotFoundException {
    public TemplateNotFoundException(Long id) {
        super("Template not found: " + id);
    }
}
