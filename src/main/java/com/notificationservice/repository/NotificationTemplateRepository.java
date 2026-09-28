package com.notificationservice.repository;

import com.notificationservice.entity.NotificationTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NotificationTemplateRepository extends JpaRepository<NotificationTemplate, Long> {
    List<NotificationTemplate> findByTenantId(Long tenantId);
    Optional<NotificationTemplate> findByIdAndTenantId(Long id, Long tenantId);
    boolean existsByTenantIdAndName(Long tenantId, String name);
}
