---
# Allowed version bumps: patch, minor, major
formidable: minor
---

Changed the configuration file to be deployed with the module, ready to edit, with every setting at its default.

Upgrading from 0.4 or earlier: the file `org.jahia.modules.formidable.cfg` did not exist in `digital-factory-data/karaf/etc`, so any configuration made through the provisioning API or the Felix console (CAPTCHA keys, upload limits, forward targets, option sources) is reset to the defaults the first time the upgraded module starts. Re-apply those settings once after the upgrade; from then on they are kept in that file.
