# Multi-tenant Notification Service

A Spring Boot service that lets multiple tenants define notification templates, configure delivery
channels, and send immediate or scheduled notifications (email/SMS/push/in-app), with per-tenant
rate limiting, retries with exponential backoff on transient failures, and a persisted audit trail
of every delivery attempt.

Full original requirements: [`docs/assignment.md`](docs/assignment.md). Design docs:
[`docs/HLD.md`](docs/HLD.md) (architecture, components, data flow) and [`docs/LLD.md`](docs/LLD.md)
(schema, algorithms, class-level detail). Original design plan / build order (written before
implementation): [`docs/plan.md`](docs/plan.md).

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

The full design write-up lives in two dedicated documents rather than here, so each can go into
proper depth without turning this README into a wall of text:

- **[`docs/HLD.md`](docs/HLD.md)** — actors, system context, major components, the core "send a
  notification" data flow, and the non-functional decisions (concurrency, fairness, rate limiting,
  retries, security) with their reasoning.
- **[`docs/LLD.md`](docs/LLD.md)** — table-by-table schema, the exact claim query (`SELECT ... FOR
  UPDATE SKIP LOCKED`) and why per-tenant batching gives fairness, the token-bucket and backoff
  formulas, the exception hierarchy, and a few Spring pitfalls hit and fixed along the way (a CSRF
  gotcha, a missing `@Bean`, a `@Transactional` self-invocation issue).

In short: a `@Scheduled` poller claims due work per-tenant (for fairness) via row-level locking (for
safety), dispatches across four bounded per-channel thread pools, retries transient failures with
capped exponential backoff, and persists every attempt as an audit trail — all backed by two
independent duplicate-delivery safeguards (idempotency keys for client retries, safe row-claiming
for internal retries).

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
