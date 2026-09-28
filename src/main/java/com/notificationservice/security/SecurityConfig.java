package com.notificationservice.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Role gating (PLATFORM_ADMIN vs TENANT_ADMIN) is done per-endpoint with @PreAuthorize on each
 * controller method (enabled by @EnableMethodSecurity below), rather than centralized URL-pattern
 * rules here -- keeps the authorization rule visible right next to the endpoint it protects.
 * Tenant-ownership checks (a tenant admin may only touch their own tenant's data) are a separate,
 * data-level concern handled explicitly in each tenant-scoped controller via TenantAccessGuard,
 * since role alone can't express "this TENANT_ADMIN, but only for tenant #5".
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder(){
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .anyRequest().authenticated()
                )
                .httpBasic(basic -> {});
        return http.build();
    }
}
