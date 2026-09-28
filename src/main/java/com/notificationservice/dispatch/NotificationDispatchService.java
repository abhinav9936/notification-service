package com.notificationservice.dispatch;

import com.notificationservice.entity.DeliveryAttempt;
import com.notificationservice.entity.DeliveryOutcome;
import com.notificationservice.entity.NotificationRequest;
import com.notificationservice.entity.NotificationStatus;
import com.notificationservice.repository.DeliveryAttemptRepository;
import com.notificationservice.repository.NotificationRequestRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * The dispatch loop: promotes due scheduled sends, then for each tenant with claimable work,
 * reserves rate-limit permits, claims a bounded batch (NotificationClaimService), and hands each
 * claimed row to the appropriate channel's bounded worker pool.
 *
 * Per-tenant batching (rather than one global claim ordered by created_at) is what gives fairness:
 * a tenant that just enqueued 10,000 notifications can only ever claim PER_TENANT_BATCH_SIZE of
 * them per poll cycle, so it can't starve other tenants out of the same cycle.
 */
@Slf4j
@Service
public class NotificationDispatchService {

    private static final int PER_TENANT_BATCH_SIZE = 5;
    private static final long BASE_BACKOFF_MILLIS = 2000L;
    private static final long MAX_BACKOFF_MILLIS = 60_000L;

    private final NotificationRequestRepository notificationRequestRepository;
    private final DeliveryAttemptRepository deliveryAttemptRepository;
    private final NotificationClaimService notificationClaimService;
    private final RateLimiterService rateLimiterService;
    private final ChannelSender channelSender;
    private final Executor emailExecutor;
    private final Executor smsExecutor;
    private final Executor pushExecutor;
    private final Executor inAppExecutor;

    public NotificationDispatchService(
            NotificationRequestRepository notificationRequestRepository,
            DeliveryAttemptRepository deliveryAttemptRepository,
            NotificationClaimService notificationClaimService,
            RateLimiterService rateLimiterService,
            ChannelSender channelSender,
            @Qualifier("emailExecutor") Executor emailExecutor,
            @Qualifier("smsExecutor") Executor smsExecutor,
            @Qualifier("pushExecutor") Executor pushExecutor,
            @Qualifier("inAppExecutor") Executor inAppExecutor) {
        this.notificationRequestRepository = notificationRequestRepository;
        this.deliveryAttemptRepository = deliveryAttemptRepository;
        this.notificationClaimService = notificationClaimService;
        this.rateLimiterService = rateLimiterService;
        this.channelSender = channelSender;
        this.emailExecutor = emailExecutor;
        this.smsExecutor = smsExecutor;
        this.pushExecutor = pushExecutor;
        this.inAppExecutor = inAppExecutor;
    }

    @Scheduled(fixedDelay = 2000)
    public void pollAndDispatch() {
        Instant now = Instant.now();
        notificationClaimService.promoteDueScheduled(now);

        List<Long> tenantIds = notificationRequestRepository.findTenantIdsWithClaimableWork(now);
        for (Long tenantId : tenantIds) {
            int permits = rateLimiterService.tryConsumeUpTo(tenantId, PER_TENANT_BATCH_SIZE);
            if (permits <= 0) {
                continue;
            }
            List<Long> claimedIds = notificationClaimService.claimBatch(tenantId, now, permits);
            for (Long id : claimedIds) {
                submitForDispatch(id);
            }
        }
    }

    private void submitForDispatch(Long notificationRequestId) {
        NotificationRequest request = notificationRequestRepository.findById(notificationRequestId).orElse(null);
        if (request == null) {
            return;
        }
        Executor executor = switch (request.getChannel()) {
            case EMAIL -> emailExecutor;
            case SMS -> smsExecutor;
            case PUSH -> pushExecutor;
            case IN_APP -> inAppExecutor;
        };
        executor.execute(() -> processAttempt(notificationRequestId));
    }

    private void processAttempt(Long notificationRequestId) {
        NotificationRequest request = notificationRequestRepository.findById(notificationRequestId).orElse(null);
        if (request == null) {
            return;
        }

        int attemptNumber = request.getAttemptCount() + 1;

        try {
            channelSender.send(request.getRecipient(), request.getRenderedSubject(), request.getRenderedBody());
            saveAttempt(request, attemptNumber, DeliveryOutcome.SUCCESS, null);
            request.setAttemptCount(attemptNumber);
            request.setStatus(NotificationStatus.SENT);
            request.setLastError(null);
        } catch (Exception ex) {
            saveAttempt(request, attemptNumber, DeliveryOutcome.FAILURE, ex.getMessage());
            request.setAttemptCount(attemptNumber);
            request.setLastError(ex.getMessage());
            if (attemptNumber >= request.getMaxAttempts()) {
                request.setStatus(NotificationStatus.DEAD);
                request.setNextAttemptAt(null);
                log.warn("Notification {} exhausted retries after {} attempts, marking DEAD",
                        notificationRequestId, attemptNumber);
            } else {
                long backoffMillis = Math.min(BASE_BACKOFF_MILLIS * (1L << (attemptNumber - 1)), MAX_BACKOFF_MILLIS);
                request.setStatus(NotificationStatus.RETRY_PENDING);
                request.setNextAttemptAt(Instant.now().plusMillis(backoffMillis));
                log.info("Notification {} attempt {} failed, retrying in {}ms",
                        notificationRequestId, attemptNumber, backoffMillis);
            }
        }

        notificationRequestRepository.save(request);
    }

    private void saveAttempt(NotificationRequest request, int attemptNumber, DeliveryOutcome outcome, String errorMessage) {
        DeliveryAttempt attempt = new DeliveryAttempt();
        attempt.setNotificationRequest(request);
        attempt.setAttemptNumber(attemptNumber);
        attempt.setOutcome(outcome);
        attempt.setErrorMessage(errorMessage);
        deliveryAttemptRepository.save(attempt);
    }
}
