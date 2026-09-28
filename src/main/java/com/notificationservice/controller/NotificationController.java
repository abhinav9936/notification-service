package com.notificationservice.controller;

import com.notificationservice.dto.NotificationDetailResponse;
import com.notificationservice.dto.NotificationResponse;
import com.notificationservice.dto.NotificationSendRequest;
import com.notificationservice.security.AppUserPrincipal;
import com.notificationservice.security.TenantAccessGuard;
import com.notificationservice.service.NotificationRequestService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tenants/{tenantId}/notifications")
@RequiredArgsConstructor
@PreAuthorize("hasRole('TENANT_ADMIN')")
public class NotificationController {

    private final NotificationRequestService notificationRequestService;
    private final TenantAccessGuard tenantAccessGuard;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public NotificationResponse send(@PathVariable Long tenantId,
                                      @Valid @RequestBody NotificationSendRequest request,
                                      @AuthenticationPrincipal AppUserPrincipal principal) {
        tenantAccessGuard.requireOwnTenant(principal, tenantId);
        return notificationRequestService.send(tenantId, request);
    }

    @GetMapping
    public List<NotificationResponse> list(@PathVariable Long tenantId,
                                            @AuthenticationPrincipal AppUserPrincipal principal) {
        tenantAccessGuard.requireOwnTenant(principal, tenantId);
        return notificationRequestService.list(tenantId);
    }

    @GetMapping("/{id}")
    public NotificationDetailResponse get(@PathVariable Long tenantId, @PathVariable Long id,
                                           @AuthenticationPrincipal AppUserPrincipal principal) {
        tenantAccessGuard.requireOwnTenant(principal, tenantId);
        return notificationRequestService.getDetail(tenantId, id);
    }
}
