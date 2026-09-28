package com.notificationservice.dispatch;

/**
 * Simple token bucket: refills continuously at `refillPerSecond`, capped at `capacity`. Not a
 * Spring bean -- one instance is held per tenant by RateLimiterService.
 */
class TokenBucket {

    private double tokens;
    private double capacity;
    private double refillPerSecond;
    private long lastRefillNanos;

    TokenBucket(double ratePerSecond) {
        this.capacity = ratePerSecond;
        this.refillPerSecond = ratePerSecond;
        this.tokens = ratePerSecond;
        this.lastRefillNanos = System.nanoTime();
    }

    synchronized void updateRate(double ratePerSecond) {
        this.capacity = ratePerSecond;
        this.refillPerSecond = ratePerSecond;
    }

    synchronized int tryConsumeUpTo(int maxWanted) {
        refill();
        int take = (int) Math.min(maxWanted, Math.floor(tokens));
        tokens -= take;
        return take;
    }

    private void refill() {
        long now = System.nanoTime();
        double elapsedSeconds = (now - lastRefillNanos) / 1_000_000_000.0;
        tokens = Math.min(capacity, tokens + elapsedSeconds * refillPerSecond);
        lastRefillNanos = now;
    }
}
