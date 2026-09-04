---
# Allowed version bumps: patch, minor, major
formidable: minor
---

Changed the configuration file to be deployed with the module, ready to edit, with every setting at its default.

Upgrading from 0.4 or earlier: settings made through the provisioning API or the Felix console (no configuration file existed then) are carried over into the new file the first time the upgraded module starts.
