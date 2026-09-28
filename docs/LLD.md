# Low-Level Design

Companion to [`HLD.md`](HLD.md) — this covers schema, algorithms, and class-level detail that the
high-level document deliberately leaves out.

## 1. Schema

### `tenants`
| Column | Type | Constraints |
|---|---|---|
| id | bigint | PK, identity |
| name | varchar | not null, unique |
| status | varchar | not null (`ACTIVE`\|`SUSPENDED`) |
| created_at, updated_at | timestamptz | not null |

### `app_users`
| Column | Type | Constraints |
|---|---|---|
| id | bigint | PK, identity |
| username | varchar | not null, unique |
| password_hash | varchar | not null (BCrypt) |
| role | varchar | not null (`PLATFORM_ADMIN`\|`TENANT_ADMIN`) |
| tenant_id | bigint | FK → tenants, **nullable** (null for platform admins) |
| created_at, updated_at | timestamptz | not null |

### `notification_templates`
| Column | Type | Constraints |
|---|---|---|
| id | bigint | PK, identity |
| tenant_id | bigint | FK → tenants, not null |
| name | varchar | not null |
| channel | varchar | not null (`EMAIL`\|`SMS`\|`PUSH`\|`IN_APP`) |
| subject | varchar(500) | nullable |
| body | text | not null |
| created_at, updated_at | timestamptz | not null |
| | | **unique (tenant_id, name)** |

### `channel_configs`
| Column | Type | Constraints |
|---|---|---|
| id | bigint | PK, identity |
| tenant_id | bigint | FK → tenants, not null |
| channel | varchar | not null |
| enabled | boolean | not null, default true |
| provider_name | varchar | not null (simulated provider label) |
| created_at, updated_at | timestamptz | not null |
| | | **unique (tenant_id, channel)** — at most one config per channel per tenant |

### `global_rate_limit_config`
Single-row table (`id` is always `1`).
| Column | Type |
|---|---|
| id | bigint (always 1) |
| requests_per_second | int |
| updated_at | timestamptz |

### `tenant_rate_limits`
| Column | Type | Constraints |
|---|---|---|
| id | bigint | PK, identity |
| tenant_id | bigint | FK → tenants, not null, **unique** (one override row per tenant) |
| requests_per_second | int | not null |
| updated_at | timestamptz | not null |

### `notification_requests`
| Column | Type | Constraints |
|---|---|---|
| id | bigint | PK, identity |
| tenant_id | bigint | FK → tenants, not null |
| template_id | bigint | FK → notification_templates, not null |
| channel | varchar | not null (copied from template at send time) |
| recipient | varchar | not null |
| variables_json | text | not null (JSON-serialized `Map<String,String>`, kept for audit/debugging) |
| rendered_subject | text | nullable (email only) |
| rendered_body | text | not null — rendered **at send time**, not at dispatch time |
| status | varchar | not null (`SCHEDULED`\|`PENDING`\|`PROCESSING`\|`RETRY_PENDING`\|`SENT`\|`DEAD`) |
| scheduled_at | timestamptz | nullable — null means "send immediately" |
| idempotency_key | varchar | not null |
| attempt_count | int | not null, default 0 |
| max_attempts | int | not null, default 5 |
| next_attempt_at | timestamptz | nullable — set only while `RETRY_PENDING` |
| last_error | text | nullable |
| created_at, updated_at | timestamptz | not null |
| | | **unique (tenant_id, idempotency_key)** |

### `delivery_attempts`
| Column | Type | Constraints |
|---|---|---|
| id | bigint | PK, identity |
| notification_request_id | bigint | FK → notification_requests, not null |
| attempt_number | int | not null (1-based) |
| outcome | varchar | not null (`SUCCESS`\|`FAILURE`) |
| error_message | text | nullable |
| attempted_at | timestamptz | not null |

This table is append-only — rows are never updated or deleted. It's the audit trail.

## 2. Status state machine (`NotificationRequest.status`)

```
        scheduledAt in future           scheduledAt due (poller)
              │                                 │
   [create] ──┴──▶ SCHEDULED ─────────────────▶ PENDING ──(claimed)──▶ PROCESSING
   [create, no scheduledAt] ─────────────────────────────────────────▶ PENDING

   PROCESSING ──(send succeeds)───────────────────────────────────▶ SENT   [terminal]
   PROCESSING ──(send fails, attempts remain)──▶ RETRY_PENDING
   RETRY_PENDING ──(nextAttemptAt elapses, reclaimed)──▶ PROCESSING
   PROCESSING ──(send fails, attempts exhausted)───────────────────▶ DEAD  [terminal]
```

`SENT` and `DEAD` are terminal — no transition leaves them. A row in `PROCESSING` that a worker
never finishes (e.g. app crash mid-attempt) is a known gap: there's no separate "processing timeout"
sweep to reclaim it. See `HLD.md` limitations.

## 3. Claiming algorithm (concurrency + fairness)

`NotificationClaimService` (kept as its own Spring bean — see §7 on why):

```sql
-- 1. Promote due scheduled sends (JPQL bulk update, parameterized on the two enum values):
UPDATE notification_requests SET status = 'PENDING'
WHERE status = 'SCHEDULED' AND scheduled_at <= :now;

-- 2. Which tenants have claimable work right now:
SELECT DISTINCT tenant_id FROM notification_requests
WHERE (status = 'PENDING')
   OR (status = 'RETRY_PENDING' AND next_attempt_at <= :now);

-- 3. For each such tenant, atomically claim up to `limit` rows (limit = rate-limiter permits,
--    capped at PER_TENANT_BATCH_SIZE = 5):
SELECT id FROM notification_requests
WHERE tenant_id = :tenantId
  AND ((status = 'PENDING') OR (status = 'RETRY_PENDING' AND next_attempt_at <= :now))
ORDER BY created_at
LIMIT :limit
FOR UPDATE SKIP LOCKED;

-- 4. Immediately, same transaction:
UPDATE notification_requests SET status = 'PROCESSING' WHERE id IN (:claimedIds);
```

`FOR UPDATE SKIP LOCKED` is what makes step 3 safe under concurrency: if another transaction
already has one of those rows locked (e.g. a previous poll cycle whose executor submission hasn't
released it yet), this query simply skips it instead of blocking — so two claim attempts can never
return the same row, without any application-level locking or coordination needed.

**Fairness** comes from doing step 3 **per tenant**, in a loop over the tenant list from step 2,
rather than one global `ORDER BY created_at LIMIT N` across all tenants. A tenant with 10,000 queued
notifications still only ever claims min(5, its rate-limit permits) per 2-second cycle — it cannot
consume the whole cycle's capacity and starve other tenants.

## 4. Rate limiting algorithm

`RateLimiterService` holds one `TokenBucket` per tenant (`ConcurrentHashMap<Long, TokenBucket>`,
created lazily). `TokenBucket` (package-private, `dispatch` package):

```java
tokens = min(capacity, tokens + elapsedSeconds * refillPerSecond)   // continuous refill
take   = min(maxWanted, floor(tokens))                              // on tryConsumeUpTo(maxWanted)
tokens -= take
return take
```

- `capacity == refillPerSecond` (no separate burst allowance — kept simple).
- The configured rate is re-read from the DB (`TenantRateLimit` override, else
  `GlobalRateLimitConfig`) on **every** call and pushed into the bucket via `updateRate(...)`, so a
  rate-limit change takes effect on the next poll cycle without restarting the app, while the
  bucket's accumulated token count is preserved across the rate change.
- In-memory, per-process, not persisted — resets on restart. Acceptable because "distributed
  systems" (multi-instance deployment) is explicitly out of scope; there's only ever one process's
  worth of state to keep consistent.

## 5. Retry / backoff algorithm

In `NotificationDispatchService.processAttempt(...)`:

```java
attemptNumber = request.attemptCount + 1
try {
    channelSender.send(recipient, renderedSubject, renderedBody)
    → save DeliveryAttempt(attemptNumber, SUCCESS)
    → status = SENT
} catch (Exception ex) {
    → save DeliveryAttempt(attemptNumber, FAILURE, ex.getMessage())
    if (attemptNumber >= maxAttempts) {
        status = DEAD; nextAttemptAt = null
    } else {
        backoffMillis = min(2000 * 2^(attemptNumber - 1), 60000)   // 2s, 4s, 8s, 16s, 32s, capped 60s
        status = RETRY_PENDING; nextAttemptAt = now + backoffMillis
    }
}
notificationRequestRepository.save(request)   // explicit save -- see §7
```

## 6. Duplicate-delivery prevention (two distinct mechanisms)

| Risk | Mechanism |
|---|---|
| **Client-side**: caller resubmits the same logical send (e.g. their own retry after a network timeout on the original request) | `idempotencyKey`, unique per `(tenant_id, idempotency_key)`. `NotificationRequestService.send()` checks for an existing row with that key first and returns it unchanged instead of inserting a new one. |
| **Internal**: our own backoff loop re-attempting a failed send | Never creates a new `NotificationRequest` row — the same row is reclaimed (`RETRY_PENDING` → `PROCESSING`) and a new `DeliveryAttempt` is appended. Combined with `SKIP LOCKED` claiming (§3), a given row is never concurrently claimed by two workers, so it's never sent twice for the same attempt. |

## 7. Notable class-level design choices

**Why `NotificationClaimService` is a separate bean from `NotificationDispatchService`:**
Spring's `@Transactional` is proxy-based AOP — it only intercepts calls that arrive from *outside*
the bean. `NotificationDispatchService.pollAndDispatch()` (itself not transactional) needs to call
transactional claim logic; if that logic were just another method on the same class, calling it via
`this.claimBatch(...)` would silently skip the transaction (a classic, easy-to-miss Spring pitfall).
Splitting it into its own bean means the call crosses a real proxy boundary and the transaction
actually applies.

**Why `processAttempt(...)` uses explicit `.save()` instead of relying on JPA dirty-checking:**
For the same self-invocation reason, `processAttempt` isn't (and can't cleanly be) wrapped in a
single `@Transactional` spanning "load entity → mutate fields → commit auto-flushes it." Instead
each repository call (`findById`, `save`) runs in its own short transaction, and the entity is
explicitly saved at the end — simpler to reason about than fighting the proxy boundary for a
background-thread code path, at the cost of the `DeliveryAttempt` insert and the `NotificationRequest`
update being two separate transactions rather than one atomic unit (acceptable: a crash between them
would leave an attempt recorded without the parent's status updated, which is a rare, non-corrupting
edge case rather than a duplicate-send risk).

**Why DTOs never expose entities directly:** decouples the wire format from the JPA schema, and
avoids Jackson trying to serialize lazy-loaded associations (`Tenant`, `NotificationTemplate`)
outside of an open Hibernate session.

**Why `TenantAccessGuard` is a separate small class rather than folded into `@PreAuthorize`:**
`@PreAuthorize` SpEL *can* reference path variables (e.g. `#tenantId == principal.tenantId`), but
that requires the `-parameters` javac flag to be relied upon implicitly and puts security-critical
logic in a string expression that's harder to unit test and to see failing clearly. An explicit,
injectable, testable Java method is more transparent for a project of this size.

## 8. Exception hierarchy

```
RuntimeException
 ├─ ResourceNotFoundException (→ 404)
 │   ├─ TenantNotFoundException
 │   ├─ TemplateNotFoundException
 │   ├─ ChannelNotConfiguredException
 │   └─ NotificationNotFoundException
 ├─ ConflictException (→ 409)
 │   ├─ DuplicateTenantNameException
 │   ├─ DuplicateTemplateNameException
 │   ├─ DuplicateUsernameException
 │   ├─ ChannelDisabledException
 │   └─ RateLimitExceedsGlobalException
 ├─ IllegalArgumentException (→ 400, Spring built-in)
 │   └─ MissingTemplateVariableException
 └─ TransientDeliveryException (caught internally by the dispatch loop, never reaches the client)
```

`GlobalExceptionHandler` maps each family to a status code in one place, plus
`MethodArgumentNotValidException` (bean-validation failures), `HttpMessageNotReadableException`
(malformed JSON / invalid enum values), `AccessDeniedException` (role or tenant-ownership failures,
→ 403), `DataIntegrityViolationException` (DB constraint races, → 409), and a catch-all `Exception`
→ 500 that logs server-side but never leaks a stack trace to the client.

## 9. Security implementation detail

- `AppUserPrincipal implements UserDetails` wraps an `AppUser`, exposing `getTenantId()` (null for
  platform admins) in addition to the standard `UserDetails` contract.
- `CustomUserDetailsService` loads by username via `AppUserRepository.findByUsername`.
- `SecurityConfig`: `BCryptPasswordEncoder` bean, `SecurityFilterChain` with CSRF disabled (stateless
  API, no cookie session to forge — see `HLD.md` for why this is safe here), session policy
  `STATELESS`, `@EnableMethodSecurity` so `@PreAuthorize("hasRole('...')")` works per-controller.
- Authorization is two layers on every tenant-scoped endpoint: `@PreAuthorize` (role) then
  `TenantAccessGuard.requireOwnTenant(principal, tenantId)` (tenant ownership) called explicitly as
  the first line of each controller method.

## 10. Test strategy mapping

| Layer | Tests |
|---|---|
| Pure logic, no Spring context | `TemplateRendererTest` (variable substitution, missing-variable failure), `TenantServiceTest` (Mockito — create/duplicate/not-found/update) |
| MockMvc + real Spring context, transactional rollback | `TenantControllerIntegrationTest` — auth, RBAC, CRUD, validation, 404/409 mapping |
| MockMvc + real Spring context, **no** rollback (needs the real background scheduler/executors to see committed data) | `NotificationFlowIntegrationTest` — full send→claim→dispatch→`SENT` path, idempotency dedup, missing-variable validation, cross-tenant isolation, platform-vs-tenant role separation |

Test DB is a separate `notification_service_test` database (`create-drop` schema per run) so the
suite never touches development data.
