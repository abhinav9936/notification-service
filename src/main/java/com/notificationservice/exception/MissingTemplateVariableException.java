package com.notificationservice.exception;

public class MissingTemplateVariableException extends IllegalArgumentException {
    public MissingTemplateVariableException(String variableName) {
        super("Missing value for template variable: " + variableName);
    }
}
