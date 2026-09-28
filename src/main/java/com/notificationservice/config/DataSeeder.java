package com.notificationservice.config;

import com.notificationservice.entity.AppUser;
import com.notificationservice.entity.AppUserRole;
import com.notificationservice.entity.GlobalRateLimitConfig;
import com.notificationservice.repository.AppUserRepository;
import com.notificationservice.repository.GlobalRateLimitConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private static final int DEFAULT_GLOBAL_REQUESTS_PER_SECOND = 20;

    private final AppUserRepository appUserRepository;
    private final GlobalRateLimitConfigRepository globalRateLimitConfigRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        if (appUserRepository.count() == 0) {
            AppUser admin = new AppUser();
            admin.setUsername("admin");
            admin.setPasswordHash(passwordEncoder.encode("admin123"));
            admin.setRole(AppUserRole.PLATFORM_ADMIN);
            appUserRepository.save(admin);
            log.info("Seeded bootstrap platform admin -> username: admin / password: admin123 (dev only)");
        }

        if (globalRateLimitConfigRepository.count() == 0) {
            GlobalRateLimitConfig config = new GlobalRateLimitConfig();
            config.setId(1L);
            config.setRequestsPerSecond(DEFAULT_GLOBAL_REQUESTS_PER_SECOND);
            globalRateLimitConfigRepository.save(config);
            log.info("Seeded default global rate limit -> {} requests/sec", DEFAULT_GLOBAL_REQUESTS_PER_SECOND);
        }
    }
}
