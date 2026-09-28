package com.notificationservice.repository;

import com.notificationservice.entity.DeliveryAttempt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DeliveryAttemptRepository extends JpaRepository<DeliveryAttempt, Long> {
    List<DeliveryAttempt> findByNotificationRequestIdOrderByAttemptNumberAsc(Long notificationRequestId);
}
