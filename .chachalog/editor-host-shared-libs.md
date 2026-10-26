---
# Allowed version bumps: patch, minor, major
formidable: patch
---

Fixed jContent crashing ("Something wrong happened") on a right click or in list mode once formidable was installed on a Jahia whose app-shell loads its shared libraries before the UI extensions: the editor extensions of formidable no longer ship Apollo Client in development mode, which froze the query results jContent sorts
