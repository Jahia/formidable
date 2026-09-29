---
# Allowed version bumps: patch, minor, major
formidable: minor
---

Improved file fields: allowed types can be given as extensions, and fields offer only the types still allowed (#351)

Every allowed type, even one an administrator adds, shows its extensions and a clear name. An empty list of allowed types now refuses every file instead of accepting any, and a type removed from the list is no longer accepted by the fields that named it. The setting `uploadAllowedMimeTypes` is renamed `uploadAllowedTypes`; its value is carried over at the upgrade, but provisioning scripts must use the new name.
