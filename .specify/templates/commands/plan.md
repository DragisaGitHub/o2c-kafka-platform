# /speckit.plan

Generate an implementation plan (`plan.md`) for a feature spec.

## Inputs

- Feature spec: `specs/<feature>/spec.md`
- Repository constitution: `.specify/memory/constitution.md`

## Output

- `specs/<feature>/plan.md`

## Required behaviors

- MUST include a **Constitution Check** section and make it actionable.
- MUST explicitly call out:
  - service boundaries + DTO discipline
  - event/topic/status naming + versioning impacts (if Kafka involved)
  - required test types (unit + integration at minimum)
  - idempotency/retry/DLQ strategy for Kafka consumers (if Kafka involved)
  - correlation IDs + structured logging impacts
  - eventual-consistency UX states (if frontend-visible)

## Notes

- Use MUST/SHOULD language when capturing gates.
- If a feature changes `common-events`, the plan MUST include compatibility notes and migration steps.
