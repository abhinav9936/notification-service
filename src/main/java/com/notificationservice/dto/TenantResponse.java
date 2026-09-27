package com.notificationservice.dto;

import com.notificationservice.entity.Tenant;
import com.notificationservice.entity.TenantStatus;

import java.time.Instant;

public record TenantResponse (
        Long id,
        String name,
        TenantStatus status,
        Instant createdAt,
        Instant updatedAt
){
    public static TenantResponse from(Tenant tenant){
        return new TenantResponse(
                tenant.getId(),
                tenant.getName(),
                tenant.getStatus(),
                tenant.getCreatedAt(),
                tenant.getUpdatedAt()
        );
    }
}
