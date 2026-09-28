package com.notificationservice.service;

import com.notificationservice.dto.ChannelConfigResponse;
import com.notificationservice.dto.ChannelConfigUpsertRequest;
import com.notificationservice.entity.Channel;
import com.notificationservice.entity.ChannelConfig;
import com.notificationservice.exception.ChannelNotConfiguredException;
import com.notificationservice.repository.ChannelConfigRepository;
import com.notificationservice.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ChannelConfigService {

    private final ChannelConfigRepository channelConfigRepository;
    private final TenantRepository tenantRepository;

    @Transactional
    public ChannelConfigResponse upsert(Long tenantId, Channel channel, ChannelConfigUpsertRequest request) {
        ChannelConfig config = channelConfigRepository.findByTenantIdAndChannel(tenantId, channel)
                .orElseGet(() -> {
                    ChannelConfig created = new ChannelConfig();
                    created.setTenant(tenantRepository.getReferenceById(tenantId));
                    created.setChannel(channel);
                    return created;
                });
        config.setEnabled(request.enabled());
        config.setProviderName(request.providerName());
        return ChannelConfigResponse.from(channelConfigRepository.save(config));
    }

    @Transactional(readOnly = true)
    public List<ChannelConfigResponse> list(Long tenantId) {
        return channelConfigRepository.findByTenantId(tenantId).stream()
                .map(ChannelConfigResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public ChannelConfigResponse get(Long tenantId, Channel channel) {
        return channelConfigRepository.findByTenantIdAndChannel(tenantId, channel)
                .map(ChannelConfigResponse::from)
                .orElseThrow(() -> new ChannelNotConfiguredException(channel));
    }
}
