package com.notificationservice.service;

import com.notificationservice.dto.RateLimitResponse;
import com.notificationservice.dto.RateLimitUpdateRequest;
import com.notificationservice.entity.GlobalRateLimitConfig;
import com.notificationservice.repository.GlobalRateLimitConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GlobalRateLimitService {

    private static final int FALLBACK_DEFAULT = 20;

    private final GlobalRateLimitConfigRepository globalRateLimitConfigRepository;

    @Transactional(readOnly = true)
    public RateLimitResponse get() {
        GlobalRateLimitConfig config = loadOrDefault();
        return new RateLimitResponse(config.getRequestsPerSecond(), false, config.getUpdatedAt());
    }

    @Transactional
    public RateLimitResponse update(RateLimitUpdateRequest request) {
        GlobalRateLimitConfig config = loadOrDefault();
        config.setRequestsPerSecond(request.requestsPerSecond());
        GlobalRateLimitConfig saved = globalRateLimitConfigRepository.save(config);
        return new RateLimitResponse(saved.getRequestsPerSecond(), false, saved.getUpdatedAt());
    }

    private GlobalRateLimitConfig loadOrDefault() {
        return globalRateLimitConfigRepository.findById(1L).orElseGet(() -> {
            GlobalRateLimitConfig config = new GlobalRateLimitConfig();
            config.setId(1L);
            config.setRequestsPerSecond(FALLBACK_DEFAULT);
            return globalRateLimitConfigRepository.save(config);
        });
    }
}
