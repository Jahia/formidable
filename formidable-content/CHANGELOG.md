# formidable-content Changelog

Release notes for the whole repository are assembled by [chachalog](../.chachalog) into the root
`CHANGELOG.md`; this file tracks the module itself so it can be read (or extracted) on its own.

## Unreleased (0.6.0-SNAPSHOT)

### New Features

* New module: **Create content** (`fmdbcnt:createContentAction`) form action. A signed-in user's submission becomes a content
  node of a chosen type: the form's fields fill its properties (values converted to the type of each property: string, long,
  double, decimal, boolean, date as `yyyy-MM-dd`, multiple), the uploaded files are stored as `jnt:file` nodes the content
  references (unique, sanitized names, the detected MIME type, `jcr:title` the original name, `jmix:image` with width and height
  for an image the JDK can read, an optional mixin and properties on every file), and both are published.
* A **group prefix** names the submitter's subfolder under the content folder and the files folder (prefix `orvane-supplier-`,
  group `orvane-supplier-northlight`, subfolder `northlight`). The subfolders must exist: they are never created.
* The title is written in every language of the site; the submitter's name, organization and the page the form was submitted
  from fill properties of their own; fixed values; required companion files (`captions<-media:video/*`) with a message the
  author writes.
* English, French and European Portuguese labels, tooltips and visitor messages; `dev-unsigned` Maven profile for local
  deployments on Jahia Enterprise.

### Security

* Guests are refused. The write uses a system session (the submitter holds no write right in `default`), so the submitter's own
  live session must be able to read the content subfolder and the files subfolder before anything is written: one company
  cannot post into another's folder, even through a misconfigured form.
* A property that the type does not declare, a protected one, or one of the platform's own is refused; a value that does not fit
  is refused; what the visitor reads never names a folder, a group or a setting.
* The page property comes from the `Referer` only when it names a page, or a content with its own page, of the same site that
  the submitter can read in live.
