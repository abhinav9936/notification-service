package com.notificationservice.exception;

public class DuplicateTemplateNameException extends ConflictException {
    public DuplicateTemplateNameException(String name) {
        super("Template name already in use for this tenant: " + name);
    }
}
