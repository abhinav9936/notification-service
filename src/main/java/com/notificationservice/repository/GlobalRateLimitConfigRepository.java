package com.notificationservice.repository;

import com.notificationservice.entity.GlobalRateLimitConfig;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GlobalRateLimitConfigRepository extends JpaRepository<GlobalRateLimitConfig, Long> {
}
