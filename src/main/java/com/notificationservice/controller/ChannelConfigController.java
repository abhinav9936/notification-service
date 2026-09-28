package com.notificationservice.controller;

import com.notificationservice.dto.ChannelConfigResponse;
import com.notificationservice.dto.ChannelConfigUpsertRequest;
import com.notificationservice.entity.Channel;
import com.notificationservice.security.AppUserPrincipal;
import com.notificationservice.security.TenantAccessGuard;
import com.notificationservice.service.ChannelConfigService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tenants/{tenantId}/channels")
@RequiredArgsConstructor
@PreAuthorize("hasRole('TENANT_ADMIN')")
public class ChannelConfigController {

    private final ChannelConfigService channelConfigService;
    private final TenantAccessGuard tenantAccessGuard;

    @PutMapping("/{channel}")
    public ChannelConfigResponse upsert(@PathVariable Long tenantId, @PathVariable Channel channel,
                                         @Valid @RequestBody ChannelConfigUpsertRequest request,
                                         @AuthenticationPrincipal AppUserPrincipal principal) {
        tenantAccessGuard.requireOwnTenant(principal, tenantId);
        return channelConfigService.upsert(tenantId, channel, request);
    }

    @GetMapping
    public List<ChannelConfigResponse> list(@PathVariable Long tenantId,
                                             @AuthenticationPrincipal AppUserPrincipal principal) {
        tenantAccessGuard.requireOwnTenant(principal, tenantId);
        return channelConfigService.list(tenantId);
    }

    @GetMapping("/{channel}")
    public ChannelConfigResponse get(@PathVariable Long tenantId, @PathVariable Channel channel,
                                      @AuthenticationPrincipal AppUserPrincipal principal) {
        tenantAccessGuard.requireOwnTenant(principal, tenantId);
        return channelConfigService.get(tenantId, channel);
    }
}
