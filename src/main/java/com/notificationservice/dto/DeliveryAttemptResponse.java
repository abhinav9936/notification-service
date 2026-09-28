package com.notificationservice.dto;

import com.notificationservice.entity.DeliveryAttempt;
import com.notificationservice.entity.DeliveryOutcome;

import java.time.Instant;

public record DeliveryAttemptResponse(
        Long id,
        int attemptNumber,
        DeliveryOutcome outcome,
        String errorMessage,
        Instant attemptedAt
) {
    public static DeliveryAttemptResponse from(DeliveryAttempt attempt) {
        return new DeliveryAttemptResponse(
                attempt.getId(),
                attempt.getAttemptNumber(),
                attempt.getOutcome(),
                attempt.getErrorMessage(),
                attempt.getAttemptedAt()
        );
    }
}
