---
# Allowed version bumps: patch, minor, major
formidable: minor
---

Added a jExperience profile property as a source of conditional logic: a field can be shown or hidden on a property of the visitor's profile, for example "Company size" only when `country` equals `FR` (#422)

The browser reads the property from the context that jExperience's tracker loads, and the server never calls jCustomer. While the tracker is blocked or the visitor gave no consent, the condition is false and the field stays hidden.
