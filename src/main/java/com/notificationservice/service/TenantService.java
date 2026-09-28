package com.notificationservice.service;

import com.notificationservice.dto.TenantCreateRequest;
import com.notificationservice.dto.TenantResponse;
import com.notificationservice.entity.Tenant;
import com.notificationservice.entity.TenantStatus;
import com.notificationservice.exception.DuplicateTenantNameException;
import com.notificationservice.exception.TenantNotFoundException;
import com.notificationservice.repository.TenantRepository;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TenantService {

    private final TenantRepository tenantRepository;

    @Transactional
    public TenantResponse createTenant(TenantCreateRequest request) {
        if (tenantRepository.existsByName(request.name())) {
            throw new DuplicateTenantNameException(request.name());
        }
        Tenant tenant = new Tenant();
        tenant.setName(request.name());
        Tenant saved = tenantRepository.save(tenant);
        return TenantResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public TenantResponse getTenant(Long id){
        Tenant tenant = tenantRepository.findById(id)
                .orElseThrow(() -> new TenantNotFoundException(id));
        return TenantResponse.from(tenant);
    }

    @Transactional(readOnly = true)
    public List<TenantResponse> listTenants(){
        return tenantRepository.findAll().stream()
                .map(TenantResponse::from)
                .toList();
    }

    @Transactional
    public TenantResponse updateStatus(Long id, TenantStatus status){
        Tenant tenant = tenantRepository.findById(id)
                .orElseThrow(() -> new TenantNotFoundException(id));
        tenant.setStatus(status);
        return TenantResponse.from(tenant);
    }
}
