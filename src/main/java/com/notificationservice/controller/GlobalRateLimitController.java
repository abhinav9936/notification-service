package com.notificationservice.controller;

import com.notificationservice.dto.RateLimitResponse;
import com.notificationservice.dto.RateLimitUpdateRequest;
import com.notificationservice.service.GlobalRateLimitService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/rate-limit")
@RequiredArgsConstructor
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
public class GlobalRateLimitController {

    private final GlobalRateLimitService globalRateLimitService;

    @GetMapping
    public RateLimitResponse get() {
        return globalRateLimitService.get();
    }

    @PutMapping
    public RateLimitResponse update(@Valid @RequestBody RateLimitUpdateRequest request) {
        return globalRateLimitService.update(request);
    }
}
