package com.notificationservice.dto;

import com.notificationservice.entity.Channel;
import com.notificationservice.entity.NotificationTemplate;

import java.time.Instant;

public record TemplateResponse(
        Long id,
        Long tenantId,
        String name,
        Channel channel,
        String subject,
        String body,
        Instant createdAt,
        Instant updatedAt
) {
    public static TemplateResponse from(NotificationTemplate template) {
        return new TemplateResponse(
                template.getId(),
                template.getTenant().getId(),
                template.getName(),
                template.getChannel(),
                template.getSubject(),
                template.getBody(),
                template.getCreatedAt(),
                template.getUpdatedAt()
        );
    }
}
