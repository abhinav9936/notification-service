package com.notificationservice.repository;

import com.notificationservice.entity.TenantRateLimit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TenantRateLimitRepository extends JpaRepository<TenantRateLimit, Long> {
    Optional<TenantRateLimit> findByTenantId(Long tenantId);
}
