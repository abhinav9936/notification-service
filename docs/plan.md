# Design Plan

## Domain model

| Entity | Purpose |
|---|---|
| `Tenant` | A customer organization. Has status (ACTIVE/SUSPENDED). |
| `AppUser` | Login account. `role` = PLATFORM_ADMIN or TENANT_ADMIN. Tenant admins belong to exactly one tenant; platform admins belong to none. |
| `NotificationTemplate` | Tenant-owned. Channel + subject/body with `{{variable}}` placeholders. |
| `ChannelConfig` | Tenant-owned. Per-channel settings (e.g. which "provider" to simulate) + enabled flag. |
| `TenantRateLimit` | Per-tenant override of the platform-wide default rate limit (requests/sec, burst). |
| `NotificationRequest` | One request to send a notification: tenant, channel, recipient, template + variables (or ad-hoc content), `scheduledAt` (null = immediate), `idempotencyKey`, status. |
| `DeliveryAttempt` | One attempt to deliver a `NotificationRequest`: attempt number, outcome, error, timestamp. The audit trail. |

**Status lifecycle for `NotificationRequest`:**
`SCHEDULED -> PENDING -> PROCESSING -> SENT`
`PROCESSING -> PROCESSING (retry, backoff) -> ... -> SENT | DEAD`

- `idempotencyKey` (unique per tenant) is what prevents duplicate delivery: a caller retrying the
  same logical send reuses the key, and we upsert instead of creating a second request. Within our
  own retry loop, we never create a new `NotificationRequest` for a retry — we add a
  `DeliveryAttempt` row to the existing one and update its status, so "at least once, recorded
  exactly once" holds.

## Concurrency & dispatch

- One bounded `ThreadPoolTaskExecutor` per channel (email/SMS/push/in-app) — this is the "bounded
  worker pool" requirement, and separating by channel means a slow channel can't starve the others.
- A scheduled poller (`@Scheduled(fixedDelay=...)`) queries due work (`status IN (PENDING,
  SCHEDULED-and-due, PROCESSING-and-due-for-retry)`) and hands rows to the right pool.
- Claiming rows safely under concurrency uses Postgres `SELECT ... FOR UPDATE SKIP LOCKED` so
  multiple poll cycles / threads never grab the same row twice.

## Rate limiting & fairness

- In-memory token bucket per tenant (single-instance app, so no need for a distributed limiter —
  matches "distributed systems out of scope"). Platform admin sets the default; tenant admin can
  request a lower override, never higher.
- Fairness: workers pull tenants round-robin rather than FIFO-by-created-time, so one tenant
  blasting thousands of notifications can't starve others out of the same poll cycle.

## Retries & backoff

- Transient failure -> increment `attemptCount`, compute `nextAttemptAt = now + backoff(attempt)`
  (exponential, capped), status stays `PROCESSING`. Exceed `maxAttempts` -> status `DEAD`.
- Every attempt (success or failure) is persisted as a `DeliveryAttempt` row — that's the audit
  trail requirement.

## RBAC

- Spring Security, HTTP Basic auth (JWT/OAuth/SSO explicitly out of scope), passwords BCrypt-hashed.
- `PLATFORM_ADMIN`: manage tenants, set global default rate limit.
- `TENANT_ADMIN`: manage templates/channel configs/rate-limit override/send notifications/view
  reports — scoped to their own tenant only. Every tenant-scoped endpoint must check the
  authenticated user's `tenantId` against the path/resource's tenant, not just their role.

## Build order (each step: implement -> test -> commit)

1. `Tenant` entity/repo/service/controller (platform admin CRUD) — learn JPA + layering.
2. `AppUser` + Spring Security (auth, password hashing, roles, method security) — learn security.
3. `NotificationTemplate` + `ChannelConfig` (tenant admin CRUD) + variable substitution logic.
4. `NotificationRequest` — synchronous immediate send first (no pools yet), prove the flow works.
5. Worker pools + async dispatch + `DeliveryAttempt` audit trail.
6. Per-tenant rate limiting.
7. Scheduled sends + retry/backoff poller.
8. Delivery reports endpoint (tenant admin) + global stats (platform admin).
9. Tighten input validation, error handling (`@ControllerAdvice`), tenant-isolation checks.
10. Fill out unit/integration test coverage gaps, finalize README.md with assumptions.

## Open assumptions (move to README.md at the end)

- Channels are *simulated* (log/store the "send" rather than calling real Twilio/SendGrid/APNs) —
  reasonable for an assignment, since real provider integration isn't the point being evaluated.
- No separate "sender" role: tenant admins trigger sends. A real product might have a
  machine-to-machine API-key-based sender identity, but that's not in the two defined roles.
- Single-instance deployment assumed (matches "distributed systems out of scope"), so in-memory
  rate limiting and a DB-polling dispatcher are acceptable instead of a message broker.
