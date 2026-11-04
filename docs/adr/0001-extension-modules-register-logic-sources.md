---
status: proposed
date: 2026-11-04
decision-makers: the Formidable maintainers
---

# Extension modules register their conditional-logic sources

## Context and Problem Statement

A conditional-logic rule shows or hides a field from a source: another field of the form, a JavaScript variable, a URL parameter or a cookie. A jExperience profile property is a source too, but the jExperience module is optional and many sites do not run it. When the core modules list that source, the editor, the page script and the server each name jExperience, ship code for a module the site may not have, and change again for every new source.

Which part of Formidable knows the sources that an extension module adds?

## Decision Drivers

* No core module names an extension module.
* A source that an extension module adds exists only while that module is deployed.
* A rule whose source is missing fails closed: the field stays hidden on the page and on the server.
* The editor and the page agree on the identifier and the stored shape of each source.

## Considered Options

* A public registry: the core lists its built-in sources, and an extension module registers its own in the editor and on the page
* A core list that names each source of each extension module, because the core then ships code for modules the site may lack, and changes for each new source
* A server-side registry that the editor and the page query, because a source is read in the visitor's browser, so each source still needs a browser half and the page pays a request

## Decision Outcome

Chosen option: "A public registry: the core lists its built-in sources, and an extension module registers its own in the editor and on the page", because it is the only option where the core names no extension and a source exists only when its module is deployed.

### Consequences

* Good, because a new source ships in its own module, with no change to the core.
* Good, because a site without the extension module runs no code of that module's source.
* Bad, because the shape of a registered source is a public contract, so a change to it breaks extension modules and follows the semantic versioning of the engine and the elements modules.
* Bad, because an extension module that adds a source carries its own jContent bundle.

### Confirmation

Review confirms that no core module names an extension module. The tests of the core run the shared evaluation on a fake registered source, and the jExperience module tests that its editor and page entries agree.
