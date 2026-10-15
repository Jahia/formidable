# formidable-content

One action that lets a **signed-in** user create content from a [Formidable](../README.md) form: **Create content**
(`fmdbcnt:createContentAction`). The form's fields fill the properties of a node of a chosen type, the uploaded files are stored
as `jnt:file` nodes the content references, and both are published, in the folder the user's own group is entitled to.
Jahia 8.2.2.0 or later.

**Full guide for authors and administrators: [how-to-content-action](../docs/administration/how-to-content-action.md).**

## What it does

| Setting | Node property | Notes |
|---|---|---|
| Content type | `contentType` | Checked at every submission: exists, concrete, creatable in the folder |
| Folders | `parentFolder`, `filesFolder` | Weak references; read in live |
| Group prefix | `groupPrefix` | The rest of the name of the user's group is the subfolder; never created |
| Title | `titleField` | `jcr:title` in every language; names the node |
| Mappings | `fieldMappings`, `fileMappings`, `fileProperties`, `fixedValues` | Lines `left=right`; values converted to the property's type |
| File extras | `fileMixin`, `fileProperties` | Added to every stored file |
| From the platform | `submitterProperty`, `organizationProperty`, `pageProperty` | Override the form's fields |
| Companion files | `requiredPairs`, `pairRefusalMessage` | `captions<-media:video/*` |
| Publication | `publish` | On by default |

## Security in one paragraph

Guests refused. The write is a system session (the submitter has no write right in `default`), so what decides where it may
write is decided first and at every submission: the submitter's group names the subfolder (none, several, an unsafe name: refused),
the subfolder must exist (never created), and **the submitter's own live session must be able to read it** (and the files
subfolder), which applies the folder's ACLs as the user and keeps one company out of another's folder even when the form is
misconfigured. Every mapped property is checked against the node type, every value converted strictly, the page found from the
`Referer` only if it is a readable page of the same site. The visitor reads generic sentences; the log has the detail.

## Layout

```
org.jahia.modules.formidable.content
  action/       CreateContentFormAction (the ContextualFormAction and its checks), ActionProps
  choicelist/   FormFieldsInitializer (the form's fields, for the title field)
  convert/      ValueConverter, PropertyShape, TypedValue (text to the property's type)
  folders/      SubmitterFolder (the group's subfolder)
  mapping/      Mappings (left=right lines), PairRule (companion files)
  names/        NodeNames (safe, unique node names), ImageSize (header only)
  page/         RefererPages (the Referer as repository paths)
  plan/         PlanBuilder, ContentPlan, Settings (everything decided and validated before a write)
  platform/     Submitters, TypeCatalog (seams over the platform) and their Jahia implementations
  store/        ContentWriter, JcrContentWriter (the only code that writes, and cleans up)
  messages/     VisitorText (what a visitor can read, EN, FR, PT)
```

## Build and deploy

```bash
# from the repository root, JDK 17
mvn -pl formidable-engine,formidable-content -am package -Pdev-unsigned   # -Pdev-unsigned only for a local Jahia Enterprise
```

Deploy the engine jar, then `formidable-content`; enable it on the site. The module declares
`Jahia-Depends: default,formidable-engine=0.6`.
