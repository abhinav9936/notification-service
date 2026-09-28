package com.notificationservice.dto;

import com.notificationservice.entity.Channel;
import com.notificationservice.entity.NotificationRequest;
import com.notificationservice.entity.NotificationStatus;

import java.time.Instant;

public record NotificationResponse(
        Long id,
        Long tenantId,
        Long templateId,
        Channel channel,
        String recipient,
        NotificationStatus status,
        Instant scheduledAt,
        int attemptCount,
        int maxAttempts,
        Instant nextAttemptAt,
        String lastError,
        Instant createdAt,
        Instant updatedAt
) {
    public static NotificationResponse from(NotificationRequest request) {
        return new NotificationResponse(
                request.getId(),
                request.getTenant().getId(),
                request.getTemplate().getId(),
                request.getChannel(),
                request.getRecipient(),
                request.getStatus(),
                request.getScheduledAt(),
                request.getAttemptCount(),
                request.getMaxAttempts(),
                request.getNextAttemptAt(),
                request.getLastError(),
                request.getCreatedAt(),
                request.getUpdatedAt()
        );
    }
}
