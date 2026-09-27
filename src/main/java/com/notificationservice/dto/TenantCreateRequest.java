package com.notificationservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TenantCreateRequest(
        @NotBlank(message = "name is required")
        @Size(max = 255, message = "name must be within 255 characters")
        String name
) {
}
