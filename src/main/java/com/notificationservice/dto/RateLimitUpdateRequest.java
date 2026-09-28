package com.notificationservice.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record RateLimitUpdateRequest(
        @NotNull(message = "requestsPerSecond is required")
        @Min(value = 1, message = "requestsPerSecond must be at least 1")
        Integer requestsPerSecond
) {
}
