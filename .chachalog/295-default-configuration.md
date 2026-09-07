---
# Allowed version bumps: patch, minor, major
formidable: minor
---

Changed the configuration file to be deployed with the module, ready to edit, with every setting at its default.

Upgrading from 0.4 or earlier: settings made through the provisioning API or the Felix console (no configuration file existed then) are carried over into the new file when the module is upgraded on a running server. After an upgrade done while Jahia was stopped, open the file and re-enter the settings it reset to their defaults.
