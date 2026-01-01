# /speckit.spec

Generate a feature specification (`spec.md`).

## Inputs

- User request ($ARGUMENTS)
- Repository constitution: `.specify/memory/constitution.md`

## Output

- `specs/<feature>/spec.md`

## Required behaviors

- MUST include user scenarios with acceptance scenarios.
- MUST include a **Constitution-Driven Constraints** section that captures boundary, contract,
  testing, observability, and eventual-consistency requirements.
