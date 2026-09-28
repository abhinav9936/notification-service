package com.notificationservice.dto;

import jakarta.validation.constraints.NotBlank;

public record TemplateUpdateRequest(
        String subject,

        @NotBlank(message = "body is required")
        String body
) {
}
