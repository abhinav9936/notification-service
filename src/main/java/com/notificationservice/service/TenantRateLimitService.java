package com.notificationservice.service;

import com.notificationservice.dto.RateLimitResponse;
import com.notificationservice.dto.RateLimitUpdateRequest;
import com.notificationservice.entity.GlobalRateLimitConfig;
import com.notificationservice.entity.TenantRateLimit;
import com.notificationservice.exception.RateLimitExceedsGlobalException;
import com.notificationservice.repository.GlobalRateLimitConfigRepository;
import com.notificationservice.repository.TenantRateLimitRepository;
import com.notificationservice.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TenantRateLimitService {

    private static final int FALLBACK_DEFAULT = 20;

    private final TenantRateLimitRepository tenantRateLimitRepository;
    private final GlobalRateLimitConfigRepository globalRateLimitConfigRepository;
    private final TenantRepository tenantRepository;

    @Transactional(readOnly = true)
    public RateLimitResponse get(Long tenantId) {
        return tenantRateLimitRepository.findByTenantId(tenantId)
                .map(limit -> new RateLimitResponse(limit.getRequestsPerSecond(), true, limit.getUpdatedAt()))
                .orElseGet(() -> {
                    GlobalRateLimitConfig global = globalRateLimitConfigRepository.findById(1L).orElse(null);
                    int rate = global != null ? global.getRequestsPerSecond() : FALLBACK_DEFAULT;
                    java.time.Instant updatedAt = global != null ? global.getUpdatedAt() : null;
                    return new RateLimitResponse(rate, false, updatedAt);
                });
    }

    @Transactional
    public RateLimitResponse update(Long tenantId, RateLimitUpdateRequest request) {
        int globalDefault = globalRateLimitConfigRepository.findById(1L)
                .map(GlobalRateLimitConfig::getRequestsPerSecond)
                .orElse(FALLBACK_DEFAULT);

        if (request.requestsPerSecond() > globalDefault) {
            throw new RateLimitExceedsGlobalException(request.requestsPerSecond(), globalDefault);
        }

        TenantRateLimit limit = tenantRateLimitRepository.findByTenantId(tenantId)
                .orElseGet(() -> {
                    TenantRateLimit created = new TenantRateLimit();
                    created.setTenant(tenantRepository.getReferenceById(tenantId));
                    return created;
                });
        limit.setRequestsPerSecond(request.requestsPerSecond());
        TenantRateLimit saved = tenantRateLimitRepository.save(limit);
        return new RateLimitResponse(saved.getRequestsPerSecond(), true, saved.getUpdatedAt());
    }
}
