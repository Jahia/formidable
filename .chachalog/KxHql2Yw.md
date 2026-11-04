---
# Allowed version bumps: patch, minor, major
formidable: minor
---

Added a visitor's jExperience profile property as a source of conditional logic (#422)

A field can now be shown or hidden on a property of the visitor's profile, for example "Company size" only when `countryName` equals `FR`. While the tracker is blocked or the visitor gave no consent, the condition is false and the field stays hidden. The author picks the property from the profile properties of jExperience.

A module of your own can now add a source of conditional logic too, through `@jahia/formidable-library/logic-sources`. A rule on a source whose module is not installed keeps its field hidden.
