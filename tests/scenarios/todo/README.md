# Todo Scenarios

This directory contains scenario documents that are not yet treated as active
regression coverage.

## Current contents

- `conditional-logic.md` - backend and runtime conditional-logic scenarios,
  including direct HTTP submission ideas.
- `quarantine.md` - the two scenarios of the quarantine of uploads that the
  plan dropped, `Q17` and `Q19`, each with why (ADR 0002).

These files stay here until the matching automated coverage is implemented and
maintained as part of the regular test suite. `quarantine.md` stays for good,
as the record of why the plan dropped its scenarios.
