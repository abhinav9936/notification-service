package com.notificationservice.controller;

import com.notificationservice.dto.RateLimitResponse;
import com.notificationservice.dto.RateLimitUpdateRequest;
import com.notificationservice.security.AppUserPrincipal;
import com.notificationservice.security.TenantAccessGuard;
import com.notificationservice.service.TenantRateLimitService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/tenants/{tenantId}/rate-limit")
@RequiredArgsConstructor
@PreAuthorize("hasRole('TENANT_ADMIN')")
public class TenantRateLimitController {

    private final TenantRateLimitService tenantRateLimitService;
    private final TenantAccessGuard tenantAccessGuard;

    @GetMapping
    public RateLimitResponse get(@PathVariable Long tenantId, @AuthenticationPrincipal AppUserPrincipal principal) {
        tenantAccessGuard.requireOwnTenant(principal, tenantId);
        return tenantRateLimitService.get(tenantId);
    }

    @PutMapping
    public RateLimitResponse update(@PathVariable Long tenantId, @Valid @RequestBody RateLimitUpdateRequest request,
                                     @AuthenticationPrincipal AppUserPrincipal principal) {
        tenantAccessGuard.requireOwnTenant(principal, tenantId);
        return tenantRateLimitService.update(tenantId, request);
    }
}
