package com.notificationservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.Map;

public record NotificationSendRequest(
        @NotNull(message = "templateId is required")
        Long templateId,

        @NotBlank(message = "recipient is required")
        String recipient,

        Map<String, String> variables,

        Instant scheduledAt,

        @NotBlank(message = "idempotencyKey is required")
        String idempotencyKey
) {
}
