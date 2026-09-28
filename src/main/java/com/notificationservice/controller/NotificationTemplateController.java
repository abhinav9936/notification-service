package com.notificationservice.controller;

import com.notificationservice.dto.TemplateCreateRequest;
import com.notificationservice.dto.TemplateResponse;
import com.notificationservice.dto.TemplateUpdateRequest;
import com.notificationservice.security.AppUserPrincipal;
import com.notificationservice.security.TenantAccessGuard;
import com.notificationservice.service.NotificationTemplateService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tenants/{tenantId}/templates")
@RequiredArgsConstructor
@PreAuthorize("hasRole('TENANT_ADMIN')")
public class NotificationTemplateController {

    private final NotificationTemplateService templateService;
    private final TenantAccessGuard tenantAccessGuard;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TemplateResponse create(@PathVariable Long tenantId,
                                    @Valid @RequestBody TemplateCreateRequest request,
                                    @AuthenticationPrincipal AppUserPrincipal principal) {
        tenantAccessGuard.requireOwnTenant(principal, tenantId);
        return templateService.create(tenantId, request);
    }

    @GetMapping
    public List<TemplateResponse> list(@PathVariable Long tenantId,
                                        @AuthenticationPrincipal AppUserPrincipal principal) {
        tenantAccessGuard.requireOwnTenant(principal, tenantId);
        return templateService.list(tenantId);
    }

    @GetMapping("/{id}")
    public TemplateResponse get(@PathVariable Long tenantId, @PathVariable Long id,
                                 @AuthenticationPrincipal AppUserPrincipal principal) {
        tenantAccessGuard.requireOwnTenant(principal, tenantId);
        return templateService.get(tenantId, id);
    }

    @PutMapping("/{id}")
    public TemplateResponse update(@PathVariable Long tenantId, @PathVariable Long id,
                                    @Valid @RequestBody TemplateUpdateRequest request,
                                    @AuthenticationPrincipal AppUserPrincipal principal) {
        tenantAccessGuard.requireOwnTenant(principal, tenantId);
        return templateService.update(tenantId, id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long tenantId, @PathVariable Long id,
                        @AuthenticationPrincipal AppUserPrincipal principal) {
        tenantAccessGuard.requireOwnTenant(principal, tenantId);
        templateService.delete(tenantId, id);
    }
}
