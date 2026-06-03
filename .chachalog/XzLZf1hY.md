---
# Allowed version bumps: patch, minor, major
formidable: minor
---

Changed the public Java package for custom form actions to `org.jahia.modules.formidable.engine.api`. If you have custom form action implementations that import from the previous package, update your imports to use the new package and recompile your module.
