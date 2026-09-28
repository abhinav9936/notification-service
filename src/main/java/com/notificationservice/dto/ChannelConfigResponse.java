package com.notificationservice.dto;

import com.notificationservice.entity.Channel;
import com.notificationservice.entity.ChannelConfig;

import java.time.Instant;

public record ChannelConfigResponse(
        Long id,
        Long tenantId,
        Channel channel,
        boolean enabled,
        String providerName,
        Instant createdAt,
        Instant updatedAt
) {
    public static ChannelConfigResponse from(ChannelConfig config) {
        return new ChannelConfigResponse(
                config.getId(),
                config.getTenant().getId(),
                config.getChannel(),
                config.isEnabled(),
                config.getProviderName(),
                config.getCreatedAt(),
                config.getUpdatedAt()
        );
    }
}
