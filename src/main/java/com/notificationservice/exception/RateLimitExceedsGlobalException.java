package com.notificationservice.exception;

public class RateLimitExceedsGlobalException extends ConflictException {
    public RateLimitExceedsGlobalException(int requested, int globalDefault) {
        super("Requested rate limit " + requested + " exceeds platform default of " + globalDefault);
    }
}
