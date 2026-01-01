# /speckit.checklist

Generate a checklist (`checklist.md`) for a feature or release.

## Inputs

- Feature context (plan/spec/tasks)
- Repository constitution: `.specify/memory/constitution.md`

## Output

- `specs/<feature>/checklist.md`

## Required behaviors

Checklist MUST include applicable items for:

- service/module boundaries + DTO discipline
- contract/naming/versioning impacts (Kafka events/topics/statuses)
- test taxonomy coverage (unit/integration/E2E)
- reliability (idempotency, retry/backoff, DLQ/quarantine, invalid messages)
- observability (correlation IDs, structured logging, stable error codes)
- UX consistency (status + error representation, eventual-consistency UI states)
