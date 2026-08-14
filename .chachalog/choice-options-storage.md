---
# Allowed version bumps: patch, minor, major
formidable: minor
---

Changed choice-field option storage to one shared format; existing forms are converted automatically at startup (#193)

**Breaking change.** You are affected if you built custom views, queries or integrations that read a choice field's option list directly from its stored properties: selects, radios and checkboxes previously each stored their options under their own property, and they now all share a single one. Regular forms need no action: the conversion runs once when the new version starts, in both edit and live, and published forms keep rendering without a republish.

One case needs attention: a form export made with version 0.3.0 or earlier and imported into an instance already running this version shows empty option lists on its choice fields until the server restarts, which re-runs the conversion. Restart after such an import, or re-save the options in the editor.
