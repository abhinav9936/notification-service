package com.notificationservice.service;

import com.notificationservice.dto.TenantCreateRequest;
import com.notificationservice.dto.TenantResponse;
import com.notificationservice.entity.Tenant;
import com.notificationservice.entity.TenantStatus;
import com.notificationservice.exception.DuplicateTenantNameException;
import com.notificationservice.exception.TenantNotFoundException;
import com.notificationservice.repository.TenantRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TenantServiceTest {

    @Mock
    private TenantRepository tenantRepository;

    @InjectMocks
    private TenantService tenantService;

    @Test
    void createTenant_savesAndReturnsResponse_whenNameIsUnique() {
        when(tenantRepository.existsByName("Acme")).thenReturn(false);
        when(tenantRepository.save(any(Tenant.class))).thenAnswer(invocation -> {
            Tenant tenant = invocation.getArgument(0);
            tenant.setId(1L);
            return tenant;
        });

        TenantResponse response = tenantService.createTenant(new TenantCreateRequest("Acme"));

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.name()).isEqualTo("Acme");
        assertThat(response.status()).isEqualTo(TenantStatus.ACTIVE);
        verify(tenantRepository).save(any(Tenant.class));
    }

    @Test
    void createTenant_throwsConflict_whenNameAlreadyExists() {
        when(tenantRepository.existsByName("Acme")).thenReturn(true);

        assertThatThrownBy(() -> tenantService.createTenant(new TenantCreateRequest("Acme")))
                .isInstanceOf(DuplicateTenantNameException.class);

        verify(tenantRepository, never()).save(any());
    }

    @Test
    void getTenant_throwsNotFound_whenIdDoesNotExist() {
        when(tenantRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> tenantService.getTenant(999L))
                .isInstanceOf(TenantNotFoundException.class)
                .hasMessageContaining("999");
    }

    @Test
    void updateStatus_flipsStatusOnManagedEntity() {
        Tenant tenant = new Tenant();
        tenant.setId(5L);
        tenant.setName("Acme");
        tenant.setStatus(TenantStatus.ACTIVE);
        when(tenantRepository.findById(5L)).thenReturn(Optional.of(tenant));

        TenantResponse response = tenantService.updateStatus(5L, TenantStatus.SUSPENDED);

        assertThat(response.status()).isEqualTo(TenantStatus.SUSPENDED);
        assertThat(tenant.getStatus()).isEqualTo(TenantStatus.SUSPENDED);
    }
}
