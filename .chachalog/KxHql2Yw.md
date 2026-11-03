---
# Allowed version bumps: patch, minor, major
formidable: minor
---

Added a visitor's jExperience profile property as a source of conditional logic (#422)

A field can now be shown or hidden on a property of the visitor's profile, for example "Company size" only when `countryName` equals `FR`. While the tracker is blocked or the visitor gave no consent, the condition is false and the field stays hidden. The author picks the property from the profile properties of jExperience.
