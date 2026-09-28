---
# Allowed version bumps: patch, minor, major
formidable: minor
---

Changed the engine's configuration from one file to five, one per theme: CAPTCHA, uploads, choice options, form actions, field actions.

**Breaking change for administrators.** The single file `karaf/etc/org.jahia.modules.formidable.cfg` (PID `org.jahia.modules.formidable`) is no longer read. Each theme now has its own file, `karaf/etc/org.jahia.modules.formidable.<theme>.cfg`, with `<theme>` one of `captcha`, `uploads`, `choiceOptions`, `formActions`, `fieldActions`; the setting names are unchanged. **Who is affected:** every installation that changed a setting — CAPTCHA keys, upload limits, options sources, forward targets, field action providers — through the file, the provisioning API or the Felix console. **What happens at the first start:** the module copies the five files with their defaults, then carries every setting held at a non-default value in the old configuration into its theme's file (the log line `Carried over from org.jahia.modules.formidable into the theme's file` names them); the old file gets a first line saying it is no longer read, and is never deleted. **What to do:** open the five files after the upgrade and check your settings are there; re-enter any the log reports as not carried over (`Gave up carrying …`); point your provisioning scripts (`editConfiguration`) at the theme's PID. Details in the administration guide, "Configuration files".
