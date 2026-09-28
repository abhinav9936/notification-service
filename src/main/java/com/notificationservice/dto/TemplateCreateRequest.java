package com.notificationservice.dto;

import com.notificationservice.entity.Channel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record TemplateCreateRequest(
        @NotBlank(message = "name is required")
        String name,

        @NotNull(message = "channel is required")
        Channel channel,

        String subject,

        @NotBlank(message = "body is required")
        String body
) {
}
