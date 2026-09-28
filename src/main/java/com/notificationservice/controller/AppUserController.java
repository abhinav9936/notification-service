package com.notificationservice.controller;

import com.notificationservice.dto.AppUserResponse;
import com.notificationservice.dto.CreateTenantAdminRequest;
import com.notificationservice.service.AppUserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/tenants/{tenantId}/admins")
@RequiredArgsConstructor
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
public class AppUserController {

    private final AppUserService appUserService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AppUserResponse createTenantAdmin(@PathVariable Long tenantId,
                                              @Valid @RequestBody CreateTenantAdminRequest request) {
        return appUserService.createTenantAdmin(tenantId, request);
    }
}
