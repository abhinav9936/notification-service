package com.notificationservice.dispatch;

import com.notificationservice.repository.GlobalRateLimitConfigRepository;
import com.notificationservice.repository.TenantRateLimitRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * In-memory per-tenant token bucket. In-memory (rather than a distributed limiter) is a deliberate
 * choice matching the assignment's "distributed systems out of scope" -- this is a single-instance
 * application, so there's only ever one process's worth of buckets to keep consistent.
 */
@Service
@RequiredArgsConstructor
public class RateLimiterService {

    private static final int DEFAULT_FALLBACK_RATE = 20;

    private final TenantRateLimitRepository tenantRateLimitRepository;
    private final GlobalRateLimitConfigRepository globalRateLimitConfigRepository;
    private final ConcurrentMap<Long, TokenBucket> buckets = new ConcurrentHashMap<>();

    /**
     * Reserves up to maxWanted permits for this tenant right now, returning how many were
     * actually available (0 if the tenant is currently rate-limited).
     */
    public int tryConsumeUpTo(Long tenantId, int maxWanted) {
        double rate = resolveRatePerSecond(tenantId);
        TokenBucket bucket = buckets.computeIfAbsent(tenantId, id -> new TokenBucket(rate));
        bucket.updateRate(rate);
        return bucket.tryConsumeUpTo(maxWanted);
    }

    private double resolveRatePerSecond(Long tenantId) {
        return tenantRateLimitRepository.findByTenantId(tenantId)
                .map(limit -> (double) limit.getRequestsPerSecond())
                .orElseGet(() -> (double) globalRateLimitConfigRepository.findById(1L)
                        .map(com.notificationservice.entity.GlobalRateLimitConfig::getRequestsPerSecond)
                        .orElse(DEFAULT_FALLBACK_RATE));
    }
}
