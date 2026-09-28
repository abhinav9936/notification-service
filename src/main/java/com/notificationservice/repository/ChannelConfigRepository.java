package com.notificationservice.repository;

import com.notificationservice.entity.Channel;
import com.notificationservice.entity.ChannelConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ChannelConfigRepository extends JpaRepository<ChannelConfig, Long> {
    List<ChannelConfig> findByTenantId(Long tenantId);
    Optional<ChannelConfig> findByTenantIdAndChannel(Long tenantId, Channel channel);
}
