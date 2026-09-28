package com.notificationservice.service;

import com.notificationservice.dto.AppUserResponse;
import com.notificationservice.dto.CreateTenantAdminRequest;
import com.notificationservice.entity.AppUser;
import com.notificationservice.entity.AppUserRole;
import com.notificationservice.entity.Tenant;
import com.notificationservice.exception.DuplicateUsernameException;
import com.notificationservice.exception.TenantNotFoundException;
import com.notificationservice.repository.AppUserRepository;
import com.notificationservice.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AppUserService {

    private final AppUserRepository appUserRepository;
    private final TenantRepository tenantRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public AppUserResponse createTenantAdmin(Long tenantId, CreateTenantAdminRequest request) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new TenantNotFoundException(tenantId));

        if (appUserRepository.existsByUsername(request.username())) {
            throw new DuplicateUsernameException(request.username());
        }

        AppUser user = new AppUser();
        user.setUsername(request.username());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole(AppUserRole.TENANT_ADMIN);
        user.setTenant(tenant);

        return AppUserResponse.from(appUserRepository.save(user));
    }
}
