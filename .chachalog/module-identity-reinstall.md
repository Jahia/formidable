---
# Allowed version bumps: patch, minor, major
formidable: minor
---

Changed the form rendering module's identity; upgrading from 0.3 or earlier requires a one-time reinstall (#187)

**Breaking change.** You are affected if any version up to 0.3.0 of the form rendering module is installed: uploading the new version fails with "Module upload failed because another module formidable-elements exists."

Earlier versions were packaged under a placeholder group id; the module is now published under the official Jahia group id, as required for distribution on the Jahia App Store. Jahia treats a module with the same name but a different group id as a different module and blocks the upload.

To upgrade: in **Administration > Server > Modules and Extensions > Modules**, stop and uninstall the old version, then install the new one. Forms and their submissions are kept, and they render again as soon as the new version starts. Later upgrades install in place as usual.
