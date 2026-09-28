package com.notificationservice.dto;

import com.notificationservice.entity.TenantStatus;
import jakarta.validation.constraints.NotNull;

public record TenantStatusUpdateRequest (
        @NotNull(message = "Status is required")
        TenantStatus status
){
}
