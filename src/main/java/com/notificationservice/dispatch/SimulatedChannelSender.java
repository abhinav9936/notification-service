package com.notificationservice.dispatch;

import com.notificationservice.exception.TransientDeliveryException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Real provider integration (Twilio/SendGrid/APNs/etc.) is explicitly out of scope for this
 * assignment -- this simulates delivery so the retry/backoff/rate-limiting/audit-trail behavior
 * is actually observable and testable end to end.
 *
 * Deliberately deterministic rather than randomly flaky: a recipient containing "fail"
 * (case-insensitive) always fails, everything else always succeeds. That makes failure/retry
 * paths reproducible on demand in tests and demos, instead of relying on random chance.
 */
@Slf4j
@Component
public class SimulatedChannelSender implements ChannelSender {

    @Override
    public void send(String recipient, String subject, String body) {
        if (recipient != null && recipient.toLowerCase().contains("fail")) {
            throw new TransientDeliveryException("Simulated transient failure for recipient: " + recipient);
        }
        log.info("Simulated send -> recipient={}, subject={}", recipient, subject);
    }
}
