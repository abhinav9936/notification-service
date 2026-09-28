package com.notificationservice.dto;

import com.notificationservice.entity.Channel;
import com.notificationservice.entity.NotificationRequest;
import com.notificationservice.entity.NotificationStatus;

import java.time.Instant;
import java.util.List;

public record NotificationDetailResponse(
        Long id,
        Long tenantId,
        Long templateId,
        Channel channel,
        String recipient,
        String renderedSubject,
        String renderedBody,
        NotificationStatus status,
        Instant scheduledAt,
        int attemptCount,
        int maxAttempts,
        Instant nextAttemptAt,
        String lastError,
        Instant createdAt,
        Instant updatedAt,
        List<DeliveryAttemptResponse> attempts
) {
    public static NotificationDetailResponse from(NotificationRequest request, List<DeliveryAttemptResponse> attempts) {
        return new NotificationDetailResponse(
                request.getId(),
                request.getTenant().getId(),
                request.getTemplate().getId(),
                request.getChannel(),
                request.getRecipient(),
                request.getRenderedSubject(),
                request.getRenderedBody(),
                request.getStatus(),
                request.getScheduledAt(),
                request.getAttemptCount(),
                request.getMaxAttempts(),
                request.getNextAttemptAt(),
                request.getLastError(),
                request.getCreatedAt(),
                request.getUpdatedAt(),
                attempts
        );
    }
}
