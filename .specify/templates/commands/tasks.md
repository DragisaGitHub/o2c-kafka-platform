# /speckit.tasks

Generate a task list (`tasks.md`) from feature docs.

## Inputs

- `specs/<feature>/plan.md`
- `specs/<feature>/spec.md`
- `specs/<feature>/contracts/` (if present)

## Output

- `specs/<feature>/tasks.md`

## Required behaviors

- MUST include tasks for required tests:
  - unit tests for business logic
  - integration tests for Kafka flows and DB interactions (when applicable)
- MUST keep unit vs integration vs E2E clearly separated and runnable separately.
- MUST include tasks for correlation ID propagation and structured logging when a new flow is added.
- MUST include explicit tasks for idempotency and failure handling for any Kafka consumer changes.
