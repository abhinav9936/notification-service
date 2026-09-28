package com.notificationservice.dto;

import java.time.Instant;

public record RateLimitResponse(
        int requestsPerSecond,
        boolean isOverride,
        Instant updatedAt
) {
}
