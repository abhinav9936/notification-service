package com.notificationservice.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Single-row table (id is always 1) holding the platform-wide default rate limit, used for any
 * tenant that doesn't have its own TenantRateLimit override.
 */
@Entity
@Table(name = "global_rate_limit_config")
@Getter
@Setter
@NoArgsConstructor
public class GlobalRateLimitConfig {

    @Id
    private Long id = 1L;

    @Column(nullable = false)
    private int requestsPerSecond;

    @Column(nullable = false)
    private Instant updatedAt;

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }
}
