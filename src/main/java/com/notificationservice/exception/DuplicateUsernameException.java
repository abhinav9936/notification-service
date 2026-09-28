package com.notificationservice.exception;

public class DuplicateUsernameException extends ConflictException {
    public DuplicateUsernameException(String username) {
        super("Username already in use: " + username);
    }
}
