# CLAUDE.md - Notification Service

This file documents how Claude was used while developing this repository and provides working
context for future AI-assisted changes. It is part of the assignment submission and should remain
an accurate, living record of the collaboration. It must not imply that the project was designed or
built autonomously by AI.

## Project summary

This repository contains a Spring Boot take-home project for a multi-tenant notification service.
The original requirements are preserved in [`docs/assignment.md`](docs/assignment.md). The service
supports tenant-specific templates and channel configuration, immediate and scheduled delivery,
bounded per-channel worker pools, per-tenant rate limiting, retries with exponential backoff,
idempotency, role-based access control, delivery reports, and a persisted attempt history.

The implemented design is documented in:

- [`docs/plan.md`](docs/plan.md) - initial domain model, decisions, assumptions, and build order.
- [`docs/HLD.md`](docs/HLD.md) - system context, components, flows, and architectural trade-offs.
- [`docs/LLD.md`](docs/LLD.md) - schema, algorithms, state transitions, and implementation details.
- [`README.md`](README.md) - setup, API reference, assumptions, and known limitations.

## Ownership and AI collaboration

The project author owns the solution. Claude was used as a development assistant, not as the
source of the product idea or as an autonomous implementer.

| Area | Project author's contribution | Claude's contribution |
|---|---|---|
| Requirement interpretation | Read the assignment, identified the expected flows, and decided what would be in or out of scope. | Helped clarify ambiguous requirements and asked questions about edge cases. |
| Ideation and architecture | Chose the domain model, REST-oriented approach, single-service scope, PostgreSQL persistence, tenant isolation, dispatch model, rate limiting, retry behaviour, and audit trail. | Discussed trade-offs, highlighted risks, and suggested implementation patterns for consideration. |
| Technology choices | Selected Java, Spring Boot, Maven, PostgreSQL, Spring Security, JPA, and the local development setup. | Checked framework/library syntax and compatibility and explained unfamiliar Spring concepts. |
| Production code | Wrote, reviewed, and integrated the application code in IntelliJ; made the final decisions about behaviour and structure. | Supplied targeted syntax examples and boilerplate suggestions, reviewed code, and helped diagnose compilation or configuration mistakes. |
| Error handling | Decided the public API behaviour and appropriate HTTP outcomes. | Suggested exception classes, handler patterns, validation cases, and mappings for common failure scenarios. |
| Testing | Chose the behaviours that needed verification, ran the tests, investigated failures, and accepted the final coverage. | Helped generate unit and integration test cases, fixtures, edge cases, and assertions. |
| Documentation | Supplied the design reasoning, assumptions, project narrative, and final technical decisions. | Helped organize, edit, and improve the clarity of the Markdown documentation. |
| Final responsibility | Verified changes, ran the application/test suite, and owns all submitted code and claims. | Provided suggestions only; suggestions were subject to developer review and correction. |

Some AI suggestions were accepted, some were modified, and some were rejected. Git commits reflect
the incremental implementation history, but commit wording should not be interpreted as authorship
attribution. The developer remains responsible for understanding and explaining the submitted
solution.

## How Claude was used

The typical workflow was:

1. The project author selected the next feature from `docs/plan.md` and described the intended
   behaviour.
2. Claude explained relevant Spring or Java concepts and, when useful, provided a small syntax or
   API example.
3. The author implemented or integrated the change in IntelliJ and made the final design choices.
4. Claude reviewed code or errors for issues such as incorrect imports, annotations, bean setup,
   transaction boundaries, validation, and security configuration.
5. Claude suggested test scenarios and exception-handling cases.
6. The author compiled the project, ran the tests, inspected failures, and decided whether the
   change was complete.
7. Decisions and meaningful assumptions were recorded in the repository documentation.

Claude was especially useful for:

- Java and Spring syntax checks.
- Verifying framework annotations, configuration, and library usage.
- Explaining JPA, Spring Security, validation, transactions, scheduling, and async execution.
- Reviewing compiler errors and application failures.
- Suggesting unit and integration test cases, including negative paths and tenant-isolation cases.
- Proposing exception hierarchies and centralized error-response handling.
- Identifying edge cases such as duplicate requests, missing template variables, disabled channels,
  unauthorized cross-tenant access, and retry exhaustion.
- Editing technical documentation for clarity and consistency.

## Stack and scope decisions

- **Java 21 and Maven** - selected for a conventional, readable Spring Boot project.
- **Spring Boot 4.1.1** - selected through Spring Initializr when the project was created.
- **Spring MVC, Spring Data JPA, Validation, Security, Lombok, and PostgreSQL**.
- **PostgreSQL running locally** - chosen instead of an in-memory development database so the
  implementation can use real relational constraints and `FOR UPDATE SKIP LOCKED` semantics.
- **HTTP Basic with BCrypt and stateless security** - sufficient for the two required roles;
  OAuth, SSO, and MFA are outside the assignment scope.
- **Single Spring Boot service** - distributed systems and microservices are explicitly out of
  scope.
- **Simulated channel providers** - real SendGrid, Twilio, APNs, or similar integrations would
  require external accounts and credentials and are not necessary to demonstrate the core design.
- **No UI, containerization, deployment pipeline, or production observability**, following the
  assignment boundaries.

## Important design decisions

- A tenant administrator can access only resources belonging to their own tenant. Role checks and
  tenant-ownership checks are separate concerns.
- Templates are rendered when a notification request is created, preserving the exact content in
  the audit record even if the template changes later.
- Client retries are deduplicated using a key unique to `(tenant_id, idempotency_key)`.
- Dispatch work is claimed safely through PostgreSQL row locking with `FOR UPDATE SKIP LOCKED`.
- Separate bounded executors are used for email, SMS, push, and in-app delivery so one slow channel
  cannot consume every worker.
- Work is claimed in per-tenant batches to reduce the possibility of one noisy tenant starving
  others.
- Rate limiting uses an in-memory token bucket because this assignment assumes one application
  instance.
- Every delivery attempt is appended to the audit history. Transient failures use capped
  exponential backoff; exhausted requests move to `DEAD`.

## Coding conventions

- Base package: `com.notificationservice`.
- Current organization is package-by-layer: `entity`, `repository`, `service`, `controller`, `dto`,
  `security`, `config`, `dispatch`, `exception`, and `util`.
- Controllers should remain thin; business rules belong in services.
- JPA entities must not be returned directly from controllers; map them to DTOs.
- Use `jakarta.validation` annotations on request DTOs.
- Use Lombok for routine boilerplate where it improves readability.
- Tenant-scoped operations must verify ownership, not only the user's role.
- Preserve idempotency, status-transition, retry, and audit-trail invariants when changing dispatch
  code.
- Do not introduce distributed infrastructure, real provider integrations, or other out-of-scope
  components without an explicit design decision.

## Test and verification expectations

Run the complete suite before considering a change finished:

```bash
./mvnw test
```

The tests use the separate `notification_service_test` PostgreSQL database. Current coverage
includes:

- Pure unit tests for template rendering and tenant service behaviour.
- Controller integration tests for authentication, authorization, validation, CRUD, and error
  responses.
- A notification-flow integration test covering request creation, idempotency, tenant isolation,
  validation, asynchronous claiming, dispatch, and final delivery state.

For new behaviour, test both the successful path and relevant failures. Do not weaken or delete a
test merely to make a build pass; first determine whether the implementation or the expectation is
wrong.

## Guidance for future Claude sessions

Before proposing a change:

1. Read `docs/assignment.md`, this file, and the relevant design document.
2. Inspect the existing implementation and tests instead of assuming a blank project.
3. Explain any material architectural trade-off and preserve assignment scope.
4. Prefer focused changes over unrelated refactoring.
5. Check current framework APIs when uncertain; do not invent annotations, configuration keys, or
   library behaviour.
6. Add or update tests for changed behaviour and run `./mvnw test`.
7. Never claim that AI made a product or architecture decision that was actually made by the
   project author.

## Assumptions and limitations

The authoritative assumptions and known limitations are maintained in `README.md`, `docs/HLD.md`,
and `docs/LLD.md`. When a new meaningful assumption is introduced, update the appropriate design
document and summarize it in the README rather than leaving it only in an AI conversation.
