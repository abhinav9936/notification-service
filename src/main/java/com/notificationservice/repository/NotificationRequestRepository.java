package com.notificationservice.repository;

import com.notificationservice.entity.NotificationRequest;
import com.notificationservice.entity.NotificationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface NotificationRequestRepository extends JpaRepository<NotificationRequest, Long> {

    Optional<NotificationRequest> findByTenantIdAndIdempotencyKey(Long tenantId, String idempotencyKey);

    Optional<NotificationRequest> findByIdAndTenantId(Long id, Long tenantId);

    List<NotificationRequest> findByTenantIdOrderByCreatedAtDesc(Long tenantId);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE NotificationRequest n SET n.status = :pending " +
            "WHERE n.status = :scheduled AND n.scheduledAt <= :now")
    int promoteDueScheduled(@Param("scheduled") NotificationStatus scheduled,
                             @Param("pending") NotificationStatus pending,
                             @Param("now") Instant now);

    /**
     * Which tenants currently have work ready to claim (immediate sends, or retries whose backoff
     * has elapsed). Used to iterate tenants round-robin for fair, per-tenant-bounded claiming.
     */
    @Query(value = "SELECT DISTINCT tenant_id FROM notification_requests WHERE " +
            "(status = 'PENDING') OR (status = 'RETRY_PENDING' AND next_attempt_at <= :now)",
            nativeQuery = true)
    List<Long> findTenantIdsWithClaimableWork(@Param("now") Instant now);

    /**
     * Atomically claims up to `limit` claimable rows for one tenant. FOR UPDATE SKIP LOCKED means
     * concurrent poll cycles (or, later, multiple app instances against the same DB) never grab
     * the same row twice -- a row already locked by another transaction is simply skipped rather
     * than blocked on.
     */
    @Query(value = "SELECT id FROM notification_requests WHERE tenant_id = :tenantId AND " +
            "((status = 'PENDING') OR (status = 'RETRY_PENDING' AND next_attempt_at <= :now)) " +
            "ORDER BY created_at LIMIT :limit FOR UPDATE SKIP LOCKED",
            nativeQuery = true)
    List<Long> findClaimableIdsForTenant(@Param("tenantId") Long tenantId,
                                          @Param("now") Instant now,
                                          @Param("limit") int limit);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE NotificationRequest n SET n.status = :status WHERE n.id IN :ids")
    int updateStatusForIds(@Param("ids") List<Long> ids, @Param("status") NotificationStatus status);
}
