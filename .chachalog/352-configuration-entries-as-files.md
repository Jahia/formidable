---
# Allowed version bumps: patch, minor, major
formidable: minor
---

Changed options sources and forward targets to one configuration file each (#352)

**Breaking change for administrators.** These two lists are no longer lines of one setting (`id|Label|…`) in their theme's configuration file: each entry is a file of its own in `karaf/etc/`, named after its id — `org.jahia.modules.formidable.choiceOptions.source-<id>.cfg` and `org.jahia.modules.formidable.formActions.target-<id>.cfg` — and a development target is an entry marked `development=true`. An entry can also be added from the Felix console, and a module can ship entries of its own. **Who is affected:** installations that declared options sources or forward targets. **What happens at the first start:** the existing lines become one file each, and the log lists their ids (`… lines became one file each`). An id is now letters, digits, dashes and underscores only: a line whose id holds a dot or a space is not converted, and the forms storing that id must be pointed at an entry declared under a valid id. **What to do:** check the new files in `karaf/etc/`; change provisioning scripts to write one entry per id (`editConfiguration` with the file's name without `.cfg`) instead of the list settings, which are no longer read. Details in the administration guide, "Configuration files".
