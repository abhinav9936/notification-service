package com.notificationservice.controller;

import com.notificationservice.dto.TenantCreateRequest;
import com.notificationservice.dto.TenantResponse;
import com.notificationservice.dto.TenantStatusUpdateRequest;
import com.notificationservice.service.TenantService;
import lombok.RequiredArgsConstructor;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tenants")
@RequiredArgsConstructor
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
public class TenantController {

    private final TenantService tenantService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TenantResponse create(@Valid @RequestBody TenantCreateRequest request) {
        return tenantService.createTenant(request);
    }

    @GetMapping
    public List<TenantResponse> list() {
        return tenantService.listTenants();
    }

    @GetMapping("/{id}")
    public TenantResponse get(@PathVariable Long id){
        return tenantService.getTenant(id);
    }

    @PutMapping("/{id}/status")
    public TenantResponse updateStatus(@PathVariable Long id, @Valid @RequestBody TenantStatusUpdateRequest request) {
        return tenantService.updateStatus(id, request.status());
    }
}
