---
# Allowed version bumps: patch, minor, major
formidable: minor
---

Added date and datetime bounds that follow the submission day, e.g. no birth date in the future (#202)

**Upgrade impact.** You are affected if your forms use minimum or maximum dates: each bound is now a choice between a fixed date and the current date, and existing fixed bounds are converted automatically when the new version starts, in both edit and live — published forms keep rendering without a republish. Follow the documented installation order (see the upgrade notes), as for every upgrade that changes content definitions.

One case needs attention: a form export made with version 0.3.0 or earlier and imported into an instance already running this version keeps its date bounds enforced when the form is submitted, but the date pickers and the editor do not show them until the server restarts, which re-runs the conversion. Restart after such an import, or re-select the bounds in the editor.
