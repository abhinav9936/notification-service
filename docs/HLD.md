# High-Level Design

## 1. Purpose

A multi-tenant service that lets tenants define notification templates, configure delivery
channels, and send immediate or scheduled notifications, with per-tenant rate limiting, retries on
transient failure, and a full delivery audit trail. See [`assignment.md`](assignment.md) for the
original requirement.

## 2. Actors

| Actor | Responsibilities |
|---|---|
| **Platform admin** | Create/suspend tenants, create tenant-admin accounts, set the platform-wide default rate limit. |
| **Tenant admin** | Manage their own tenant's templates and channel configs, set a rate-limit override (capped at the platform default), send notifications, view delivery reports. Scoped strictly to their own tenant. |

## 3. Entities (domain model)

| Entity | Purpose |
|---|---|
| **Tenant** | A customer organization. `ACTIVE` or `SUSPENDED`. |
| **AppUser** | A login account. `PLATFORM_ADMIN` (no tenant) or `TENANT_ADMIN` (belongs to exactly one tenant). |
| **NotificationTemplate** | Tenant-owned. A channel plus a subject/body containing `{{variable}}` placeholders. |
| **ChannelConfig** | Tenant-owned. Per-channel enabled flag and a simulated provider name — a tenant must have one of these, enabled, before it can send through that channel. |
| **GlobalRateLimitConfig** | Single-row table holding the platform-wide default requests/sec. |
| **TenantRateLimit** | Optional per-tenant override of the global default (capped at it, never above). |
| **NotificationRequest** | One logical send: tenant, channel, recipient, rendered content, idempotency key, status, and retry bookkeeping (`attemptCount`, `maxAttempts`, `nextAttemptAt`). |
| **DeliveryAttempt** | One attempt at delivering a `NotificationRequest` — outcome, error, timestamp. Append-only; this is the audit trail. |

Exact column-level schema (types, constraints, foreign keys) is in [`LLD.md`](LLD.md) §1.

## 4. System context

Single-instance Spring Boot application, single PostgreSQL database. No external message broker,
no distributed cache, no real third-party channel providers — all explicitly out of scope for this
assignment (see "Out of Scope" in `assignment.md`). Concurrency and fairness are handled in-process;
Postgres itself is what makes safe concurrent work-claiming possible (`SELECT ... FOR UPDATE SKIP
LOCKED`), so the design doesn't need a broker to get that property.

```
                 HTTP (Basic Auth)
Client  ──────────────────────────────▶  Spring MVC Controllers
                                                │
                                                ▼
                                     Services (business rules,
                                     tenant isolation, validation)
                                                │
                                                ▼
                                     Spring Data JPA Repositories
                                                │
                                                ▼
                                          PostgreSQL
                                                ▲
                                                │ (claims rows)
                        ┌───────────────────────────────────────┐
                        │   NotificationDispatchService          │
                        │   (@Scheduled poller, every 2s)         │
                        └───────────────────────────────────────┘
                                │        │        │        │
                          email pool  sms pool  push pool  in-app pool
                          (bounded executors, one per channel)
```

## 5. Major components

| Component | Responsibility |
|---|---|
| **Controllers** (`controller/*`) | HTTP boundary: request/response mapping, `@Valid` input validation, `@PreAuthorize` role checks. Thin — no business logic. |
| **Services** (`service/*`) | Business rules: uniqueness checks, tenant-scoped lookups, orchestrating repositories. One service per aggregate (`TenantService`, `NotificationTemplateService`, `ChannelConfigService`, `NotificationRequestService`, rate-limit services). |
| **`TenantAccessGuard`** | Cross-cutting: confirms the authenticated tenant admin's own tenant matches the tenant in the URL. Role checks (`@PreAuthorize`) say *what kind* of user; this says *which tenant* they may touch. |
| **Dispatch engine** (`dispatch/*`) | The concurrency/fairness/rate-limiting/retry subsystem — see below and `LLD.md` for detail. |
| **Repositories** (`repository/*`) | Spring Data JPA interfaces; a few hand-written native queries for the claim logic (SKIP LOCKED isn't expressible via derived query methods). |
| **`GlobalExceptionHandler`** | Maps every exception type the app throws to a clean JSON error body and the correct HTTP status — nothing leaks a stack trace to a client. |
| **`SecurityConfig` / `CustomUserDetailsService` / `AppUserPrincipal`** | Stateless HTTP Basic auth backed by the `AppUser` table, BCrypt-hashed passwords, CSRF disabled (no cookie session to forge). |
| **`DataSeeder`** | One-time bootstrap: creates the first platform-admin account and default rate limit on a fresh database, solving the chicken-and-egg problem of needing an admin to create the first admin. |

## 6. Core data flow: sending a notification

1. Tenant admin `POST`s to `/api/tenants/{tenantId}/notifications` with a template ID, recipient,
   variables, optional `scheduledAt`, and a client-supplied `idempotencyKey`.
2. `NotificationRequestService` checks for an existing row with that `(tenantId, idempotencyKey)` —
   if found, returns it unchanged (idempotent resubmission).
3. Otherwise: loads the template (tenant-scoped), confirms the channel is configured and enabled,
   renders the template's `{{variable}}` placeholders against the supplied variables (failing fast
   with 400 if any are missing), and persists a `NotificationRequest` as `PENDING` (or `SCHEDULED`
   if `scheduledAt` is in the future).
4. Independently of the HTTP request, the dispatch engine's poller picks the row up (see below),
   attempts delivery, and updates status + records a `DeliveryAttempt`.
5. The tenant admin can poll `GET .../notifications/{id}` at any time to see current status and the
   full attempt history — this is the delivery report.

## 7. Dispatch, concurrency & fairness (high level)

Every 2 seconds, one scheduled task:

1. Promotes any `SCHEDULED` notification whose time has come to `PENDING`.
2. Asks which tenants currently have claimable work.
3. **For each tenant independently**, asks the rate limiter how many it's allowed to claim right
   now, then atomically claims up to that many rows for that tenant only.
4. Hands each claimed row to the bounded worker pool for its channel.

Doing claiming **per tenant** rather than one global "oldest N rows" query is the fairness
mechanism: a tenant that enqueues a huge burst can never claim more than its own small per-cycle
share, so it can't crowd out other tenants in the same poll cycle. Full algorithmic detail
(the SQL, the token-bucket math, the backoff formula) is in [`LLD.md`](LLD.md).

## 8. Non-functional decisions

| Concern | Decision | Why |
|---|---|---|
| Concurrency | Per-channel bounded thread pools + DB-level row locking | Satisfies "bounded worker pools" without needing a broker; Postgres itself provides the safe-claiming primitive. |
| Fairness | Per-tenant claim batching | Simple to reason about, directly addresses "per-tenant fairness under load." |
| Rate limiting | In-memory token bucket per tenant | Single-instance app (distributed systems explicitly out of scope) — no need for a shared/distributed limiter. |
| Retries | Exponential backoff, capped, persisted attempt history | Matches "retries with backoff" and "audit trail" requirements directly. |
| Duplicate prevention | Idempotency key (client-facing) + SKIP LOCKED claiming (internal) | Two different duplication risks, two different mechanisms — see `LLD.md` §6. |
| Security | Stateless HTTP Basic + BCrypt + role/tenant checks | OAuth/SSO/MFA explicitly out of scope; this is the simplest mechanism that still enforces both roles correctly. |
| Persistence | PostgreSQL, `ddl-auto=update` | Real DB needed for `SKIP LOCKED`; migrations tooling (Flyway/Liquibase) would be the next step for a longer-lived project. |

## 9. Related documents

- [`LLD.md`](LLD.md) — schema, algorithms, class-level detail.
- [`plan.md`](plan.md) — original design plan and build order (historical, written before implementation).
- [`../README.md`](../README.md) — setup instructions, API reference, assumptions log.
