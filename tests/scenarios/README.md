# Test Scenarios

This directory contains two kinds of documents:
- coverage summaries for the Cypress E2E suites
- scenario documents for areas that are driven as explicit regression topics

## Coverage summaries

- `fields.md` - explains what the `fields` E2E suite is responsible for:
  field rendering, interaction, and submission behavior for the supported form
  elements.
- `validation.md` - explains what the `validation` E2E suite is responsible
  for: client-side constraint enforcement, validation messages, focus handling,
  and multi-step validation flow.

## Active scenarios

- `permissions.md` - results access-control and ACL propagation scenarios.
- `security.md` - submission security and fail-closed behavior scenarios.

## Todo scenarios

Scenario documents that are still in design, incomplete, or not yet treated as
active regression coverage live in [`todo/`](./todo/).
