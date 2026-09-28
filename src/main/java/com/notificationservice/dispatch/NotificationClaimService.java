package com.notificationservice.dispatch;

import com.notificationservice.entity.NotificationStatus;
import com.notificationservice.repository.NotificationRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Kept as a separate bean (rather than methods on NotificationDispatchService) so its
 * @Transactional methods are called across a real Spring proxy boundary. Calling an
 * @Transactional method on `this` from within the same class silently skips the transaction --
 * Spring's proxy-based AOP only intercepts calls that come from outside the bean.
 */
@Service
@RequiredArgsConstructor
public class NotificationClaimService {

    private final NotificationRequestRepository notificationRequestRepository;

    @Transactional
    public int promoteDueScheduled(Instant now) {
        return notificationRequestRepository.promoteDueScheduled(
                NotificationStatus.SCHEDULED, NotificationStatus.PENDING, now);
    }

    /**
     * Atomically claims up to `limit` rows for one tenant (SELECT ... FOR UPDATE SKIP LOCKED,
     * then immediately flips them to PROCESSING) within a single transaction, so no other poll
     * cycle can claim the same rows.
     */
    @Transactional
    public List<Long> claimBatch(Long tenantId, Instant now, int limit) {
        List<Long> ids = notificationRequestRepository.findClaimableIdsForTenant(tenantId, now, limit);
        if (!ids.isEmpty()) {
            notificationRequestRepository.updateStatusForIds(ids, NotificationStatus.PROCESSING);
        }
        return ids;
    }
}
