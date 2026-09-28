package com.notificationservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ChannelConfigUpsertRequest(
        @NotNull(message = "enabled is required")
        Boolean enabled,

        @NotBlank(message = "providerName is required")
        String providerName
) {
}
