<!--
Sync Impact Report

- Version change: N/A (template) → 1.0.0
- Modified principles:
	- Template Principle 1 → Service & Module Boundaries (NON-NEGOTIABLE)
	- Template Principle 2 → Contracts & Naming are APIs
	- Template Principle 3 → Testing Pyramid & Test Taxonomy (NON-NEGOTIABLE)
	- Template Principle 4 → Reliability & Event Processing
	- Template Principle 5 → Observability, UX Consistency & Ownership
- Added sections:
	- Platform Standards
	- Workflow & Quality Gates
- Removed sections: None
- Templates requiring updates:
	- ✅ .specify/templates/plan-template.md
	- ✅ .specify/templates/spec-template.md
	- ✅ .specify/templates/tasks-template.md
	- ✅ .specify/templates/checklist-template.md
	- ✅ .specify/templates/commands/*.md
- Follow-up TODOs: None
-->

# O2C Kafka Platform Constitution

## Core Principles

### Service & Module Boundaries (NON-NEGOTIABLE)

- Each microservice (order-service, checkout-service, payment-service) MUST be independently
	buildable, testable, and deployable.
- Each microservice MUST own its database and schema; services MUST NOT read or write another
	service’s database.
- Services MUST communicate only via:
	- REST APIs (synchronous), and
	- Kafka events (asynchronous).
- Services MUST NOT share internal code or domain models across service boundaries.
	The ONLY shared runtime module across services is `common-events`, and it MUST contain only
	Kafka event contracts (schemas/types) and contract-level utilities (e.g., serialization helpers).
- REST request/response models and Kafka event payloads MUST use boundary DTOs.
	Persistence entities (e.g., JPA entities) MUST NOT be used as DTOs.
- The web client (browser UI) MUST NOT access Kafka directly; it MUST consume backend services via REST.

Rationale: Prevents tight coupling and enables independent evolution.

### Contracts & Naming are APIs

- Kafka topics, event names, and status values MUST be explicit, stable, and versioned.
- Event types MUST follow an action-oriented naming convention and include a schema version, e.g.
	`OrderPlacedV1`, `CheckoutCompletedV1`, `PaymentFailedV1`.
- Kafka topic names MUST be explicit and stable (no “misc”, no overloaded topics). Topic naming MUST
	communicate domain + intent + version.
- Status representation MUST be consistent across backend and frontend:
	- A status MUST have a single canonical identifier.
	- Any mapping (e.g., backend enum ↔ UI string) MUST be lossless and documented.
- Contract changes MUST follow compatibility rules:
	- Backward-compatible additive changes SHOULD be preferred.
	- Breaking changes MUST introduce a new versioned event/topic (or parallel schema) and include a
		migration plan.

Rationale: Contracts are the real integration surface in an event-driven system.

### Testing Pyramid & Test Taxonomy (NON-NEGOTIABLE)

- Business logic MUST be covered by unit tests.
	“Business logic” includes domain decisions, state transitions, pricing/tax rules, idempotency keys,
	and mapping logic.
- Kafka flows and database interactions MUST be covered by integration tests.
	Integration tests MUST exercise the real wiring (serialization, consumer/producer config,
	persistence, transactions).
- Unit tests, integration tests, and end-to-end (E2E) tests MUST be clearly distinguished.
	The distinction MUST be enforceable via build tooling (e.g., Gradle source sets and/or JUnit 5
	tags) so CI can run them intentionally.
- Tests MUST be deterministic and isolated.
	Flaky tests MUST be treated as defects and fixed or removed before merging.
- Frontend features that span multiple services SHOULD have E2E coverage for the user journey,
	including eventual-consistency states.

Rationale: Prevents regressions while keeping feedback cycles fast.

### Reliability & Event Processing

- Asynchronous workflows SHOULD be preferred for cross-service coordination.
	Synchronous REST calls across services MUST be justified (latency, coupling, failure domains).
- Kafka consumers MUST be idempotent.
	Re-processing the same message MUST NOT create duplicate side effects.
- Consumers MUST implement explicit failure handling:
	- Retries with bounded backoff for transient failures.
	- A dead-letter strategy (DLQ or quarantine topic) for messages that cannot be processed.
	- Explicit handling for invalid messages (schema violations, missing required fields).
- Invalid/poison messages MUST NOT block a consumer group indefinitely.
- Exactly-once semantics MUST NOT be assumed.
	Designs MUST be correct under at-least-once delivery.

Rationale: Kafka delivery patterns require explicit resilience.

### Observability, UX Consistency & Ownership

- A correlation identifier MUST be propagated across:
	- inbound REST requests,
	- outbound REST calls, and
	- Kafka message headers.
	If a request arrives without a correlation ID, the edge service MUST create one.
- Logs MUST be structured and machine-parseable.
	Logs MUST include correlation ID, service name, event type/topic (when applicable), and a stable
	error code for failures.
- Error handling MUST be predictable:
	- APIs MUST return a consistent error shape and stable error codes.
	- User-facing messages MUST be safe and actionable (no stack traces, no internal IDs).
- Frontend state MUST model eventual consistency.
	UI flows MUST represent pending/processing states when backend operations complete asynchronously.
- Every Kafka event type and topic MUST have a clear owner (a single service/team).
	Contract changes MUST be reviewed/approved by the owning service.

Rationale: Operability and consistent UX are core product features.

## Platform Standards

- Backend services MUST use Java 21, Gradle, Spring Boot.
- Event contracts MUST live in `common-events` and MUST be treated as a versioned API.
- Each service MUST own and evolve its persistence model independently.
- Backend-to-frontend communication MUST be via REST only.
- Status values used in REST responses and UI MUST be derived from the canonical domain status.

Operational expectations:

- Where applicable, services SHOULD avoid blocking operations on request threads.
	If blocking I/O is required, it MUST be bounded and measured.
- Kafka consumers/producers MUST have explicit configuration for retries, timeouts, and message
	sizes, and these values MUST be documented.

## Workflow & Quality Gates

- Every change MUST pass a “Constitution Check” in PR review:
	- Boundaries respected (no cross-service DB/code coupling).
	- DTOs at boundaries (no entity leakage).
	- Naming/versioning conventions for topics/events/statuses.
	- Required tests added and correctly categorized.
	- Correlation IDs and structured logging preserved.
- CI MUST run unit tests and integration tests.
	E2E tests SHOULD run in CI for user journeys that span services and the frontend.
- Any change to `common-events` MUST include:
	- migration/compatibility notes,
	- producer/consumer impact assessment, and
	- integration test updates where relevant.

## Governance
<!-- Example: Constitution supersedes all other practices; Amendments require documentation, approval, migration plan -->

- This constitution is the highest-level engineering policy for this repository.
	If other docs conflict, this constitution wins.
- Amendments MUST be made via PR and MUST include:
	- the motivation,
	- a migration plan for any breaking governance change, and
	- updates to `.specify/templates/*` if they embed constitution-derived gates.
- Versioning policy:
	- MAJOR: incompatible governance changes (principle removal or redefinition).
	- MINOR: new principle/section or material expansion of enforcement.
	- PATCH: clarifications, wording fixes, non-semantic improvements.
- Review expectation:
	- Every PR SHOULD explicitly confirm compliance with the Core Principles.
	- Any intentional violation MUST be documented with a concrete justification and mitigation.

**Version**: 1.0.0 | **Ratified**: 2026-01-01 | **Last Amended**: 2026-01-01
