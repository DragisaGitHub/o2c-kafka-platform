# /speckit.constitution

Update the repository constitution at `.specify/memory/constitution.md`.

## Required behaviors

- MUST preserve the template’s heading structure.
- MUST use enforceable language (MUST/SHOULD) and keep rules testable.
- MUST prepend a Sync Impact Report (HTML comment) describing version change and template updates.
- MUST update dependent templates in `.specify/templates/*` when constitution-derived gates change.
- MUST update version using semantic versioning:
  - MAJOR: principle removal or incompatible governance changes
  - MINOR: new principle/section or material expansion
  - PATCH: clarifications/typos
