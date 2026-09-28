# Multi-tenant Notification Service

A Spring Boot service that lets multiple tenants define notification templates, configure delivery
channels, and send immediate or scheduled notifications (email/SMS/push/in-app), with per-tenant
rate limiting, retries with exponential backoff on transient failures, and a persisted audit trail
of every delivery attempt.

Full original requirements: [`docs/assignment.md`](docs/assignment.md). Design rationale and build
order: [`docs/plan.md`](docs/plan.md).

## Tech stack

| Choice | Reasoning |
|---|---|
| **Java 21 + Maven** | Standard, widely documented, easiest to work with as a first Spring Boot project. |
| **Spring Boot 4.1.1** | Current stable version from Spring Initializr at time of writing. |
| **PostgreSQL** | A real relational DB rather than H2 — gives real `SELECT ... FOR UPDATE SKIP LOCKED` semantics for safe concurrent work-claiming, which the dispatch engine relies on. |
| **Spring Data JPA / Hibernate** | Standard persistence layer; used with explicit DTOs (never expose entities over the wire). |
| **Spring Security (HTTP Basic, stateless)** | OAuth/SSO/MFA are explicitly out of scope; Basic auth over HTTPS-in-production is a reasonable, simple mechanism for the two defined roles. Role checks via `@PreAuthorize`, tenant-ownership checks via a small `TenantAccessGuard` (role alone can't express "this tenant admin, but only for tenant #5"). |
| **In-memory token-bucket rate limiting** | "Distributed systems out of scope" implies a single instance, so there's no need for a distributed limiter (e.g. Redis) — an in-process bucket per tenant is correct and simpler. |
| **Per-channel `ThreadPoolTaskExecutor` + a `@Scheduled` poller** | Satisfies "bounded worker pools" and "dispatch concurrently across channels" without needing a message broker, which would be disproportionate for a single-instance assignment. |

## Running it locally

Prerequisites: Java 21, PostgreSQL running locally.

```bash
# one-time setup
brew install postgresql@16          # if not already installed
brew services start postgresql@16
createdb notification_service
createdb notification_service_test  # only needed to run the test suite

# run
./mvnw spring-boot:run
```

The app starts on `http://localhost:8080`. On first run, `DataSeeder` creates:
- a bootstrap platform admin: **username `admin`, password `admin123`** (dev-only, logged at startup)
- a default global rate limit of **20 requests/sec**

Database connection settings are in [`src/main/resources/application.properties`](src/main/resources/application.properties).

### Running the tests

```bash
./mvnw test
```

Uses a separate `notification_service_test` database (`src/test/resources/application-test.properties`,
schema created fresh via `ddl-auto=create-drop` each run) so tests never touch dev data.

## Architecture

### Domain model

- **Tenant** — a customer organization (`ACTIVE`/`SUSPENDED`).
- **AppUser** — a login account; `PLATFORM_ADMIN` (no tenant) or `TENANT_ADMIN` (belongs to exactly one tenant).
- **NotificationTemplate** — tenant-owned; a channel + subject/body with `{{variable}}` placeholders.
- **ChannelConfig** — tenant-owned; per-channel enabled flag + a simulated provider name.
- **GlobalRateLimitConfig** / **TenantRateLimit** — platform default and optional per-tenant override.
- **NotificationRequest** — one logical send: tenant, channel, recipient, rendered content,
  idempotency key, status, retry bookkeeping (`attemptCount`, `maxAttempts`, `nextAttemptAt`).
- **DeliveryAttempt** — one attempt at delivering a `NotificationRequest` (outcome, error, timestamp)
  — this is the audit trail.

### Status lifecycle

```
SCHEDULED --(scheduledAt due)--> PENDING --(claimed)--> PROCESSING
PROCESSING --(success)--> SENT
PROCESSING --(transient failure, attempts remain)--> RETRY_PENDING --(backoff elapses)--> PROCESSING
PROCESSING --(transient failure, attempts exhausted)--> DEAD
```

### Concurrency & fairness

A single `@Scheduled` poller (every 2s, `NotificationDispatchService`) drives dispatch:

1. Promotes any `SCHEDULED` row whose `scheduledAt` has passed to `PENDING`.
2. Finds which tenants currently have claimable work (`PENDING`, or `RETRY_PENDING` whose backoff
   has elapsed).
3. For **each tenant** (not globally), reserves up to 5 rate-limit permits and claims that many rows
   via `SELECT ... FOR UPDATE SKIP LOCKED` (`NotificationClaimService`), immediately flipping them
   to `PROCESSING` in the same transaction.
4. Hands each claimed row to the worker pool for its channel (`AsyncExecutorConfig`: one bounded
   `ThreadPoolTaskExecutor` per channel).

**Why per-tenant, not one global batch:** claiming a fixed batch *per tenant* rather than one global
`ORDER BY created_at LIMIT N` is what gives per-tenant fairness — a tenant that just enqueued 10,000
notifications can only ever claim 5 per poll cycle, so it can't starve other tenants out of the same
cycle. **Why `SKIP LOCKED`:** it lets concurrent claim attempts skip rows another transaction already
has locked instead of blocking on them, so no two workers can ever process the same row twice.

### Retries & backoff

On failure, `attemptCount` increments and a `DeliveryAttempt(FAILURE)` is recorded. If attempts remain,
status becomes `RETRY_PENDING` with `nextAttemptAt = now + min(2s * 2^(attempt-1), 60s)` (exponential,
capped). Once `maxAttempts` (default 5) is exhausted, status becomes `DEAD`. Every attempt — success
or failure — is persisted, satisfying the audit-trail requirement.

### Avoiding duplicate deliveries

Two distinct mechanisms:
- **Client-side retries** (the caller resubmits the same logical send): `idempotencyKey` is unique
  per tenant. Resubmitting the same key returns the existing `NotificationRequest` instead of
  creating a new one.
- **Internal retries** (our own backoff loop): a retry never creates a new row — it's the same
  `NotificationRequest`, re-claimed and re-attempted, with a new `DeliveryAttempt` appended. Combined
  with the `SKIP LOCKED` claiming above, a row is never picked up by two workers at once.

### Channel sending

Real provider integration (Twilio/SendGrid/APNs/etc.) is out of scope for this assignment.
`SimulatedChannelSender` stands in for it: a recipient address containing `"fail"` (case-insensitive)
always fails, everything else always succeeds. This is deliberately deterministic rather than
randomly flaky, so retry/backoff/dead-letter behavior is reproducible on demand for testing and demos.

### RBAC

- `PLATFORM_ADMIN`: manage tenants, create tenant-admin accounts, set the platform-wide default rate limit.
- `TENANT_ADMIN`: manage their own tenant's templates/channel configs/rate-limit override, send
  notifications, view delivery reports.
- Every tenant-scoped endpoint checks **both** the caller's role (`@PreAuthorize`) **and** that the
  authenticated user's own `tenantId` matches the tenant in the URL (`TenantAccessGuard`) — role
  alone can't stop one tenant admin from reading another tenant's data.

## API reference

All endpoints require HTTP Basic auth except none (everything is authenticated). Request/response
bodies are JSON.

**Platform admin** (`ROLE_PLATFORM_ADMIN`)
| Method | Path | Purpose |
|---|---|---|
| POST | `/api/tenants` | Create a tenant |
| GET | `/api/tenants` | List all tenants |
| GET | `/api/tenants/{id}` | Get a tenant |
| PUT | `/api/tenants/{id}/status` | Activate/suspend a tenant |
| POST | `/api/tenants/{tenantId}/admins` | Create a tenant-admin account for a tenant |
| GET / PUT | `/api/admin/rate-limit` | View/update the platform-wide default rate limit |

**Tenant admin** (`ROLE_TENANT_ADMIN`, own tenant only)
| Method | Path | Purpose |
|---|---|---|
| POST / GET | `/api/tenants/{tenantId}/templates` | Create / list templates |
| GET / PUT / DELETE | `/api/tenants/{tenantId}/templates/{id}` | Get / update / delete a template |
| PUT | `/api/tenants/{tenantId}/channels/{channel}` | Configure a channel (upsert) |
| GET | `/api/tenants/{tenantId}/channels` / `/{channel}` | List / get channel configs |
| GET / PUT | `/api/tenants/{tenantId}/rate-limit` | View effective rate / set an override (never above the platform default) |
| POST | `/api/tenants/{tenantId}/notifications` | Send a notification (immediate or scheduled) |
| GET | `/api/tenants/{tenantId}/notifications` | List notifications (delivery report) |
| GET | `/api/tenants/{tenantId}/notifications/{id}` | Notification detail + full delivery-attempt history |

## Assumptions & scoping decisions

- Notifications must reference a tenant-defined **template** — no ad-hoc/inline content. This leans
  into "tenant-defined templates with variable substitution" as the primary way content is composed.
- Template variables are rendered and captured **at send time**, not at dispatch time. The stored
  `renderedSubject`/`renderedBody` is exactly what was (or will be) sent, decoupling dispatch from
  later template edits and making the audit trail reflect what was actually intended.
- A channel must have an **enabled `ChannelConfig`** for a tenant before that tenant can send through
  it — sending to an unconfigured or disabled channel is rejected (409) rather than silently allowed.
- No separate "sender"/machine identity — only the two roles the assignment defines. A tenant admin
  triggers sends; a real product might add an API-key-based sender role, but that's outside the
  defined roles.
- Real channel delivery is **simulated** (see above) — implementing actual Twilio/SendGrid/APNs
  integrations isn't what's being evaluated here, and would require real credentials/accounts.
- Single-instance deployment assumed (matches "distributed systems out of scope"), so in-memory rate
  limiting and a DB-polling dispatcher are appropriate instead of a message broker or distributed
  limiter.
- `spring.jpa.hibernate.ddl-auto=update` for schema management — fine for an assignment of this
  size; a longer-lived project would switch to Flyway/Liquibase migrations.
- Rate limit overrides: a tenant admin can only set their tenant's override **at or below** the
  current platform default, never above it — the platform admin sets the ceiling.

## AI-assisted development

This project was built with Claude Code. See [`CLAUDE.md`](CLAUDE.md) for the full working notes,
conventions, and how the workflow evolved during development (per-file teach-and-review for the
first vertical slice, then direct implementation for the remainder once the time budget mattered).

## Known limitations / what I'd add with more time

- No pagination on list endpoints (`GET .../notifications`, `.../templates`) — fine at assignment
  scale, would matter in production.
- No structured metrics/observability beyond logs (explicitly out of scope).
- The bootstrap admin password is a hardcoded dev default — fine locally, would move to an
  environment variable or a proper first-run setup flow for anything real.
- Rate-limit bucket state is in-memory and per-process; restarting the app resets it (acceptable
  for a single-instance, non-persistent-limiter design).
