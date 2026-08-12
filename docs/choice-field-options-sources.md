# Choice field options sources

Choice fields (`fmdb:select`, `fmdb:radio`, `fmdb:checkbox`) can fill their option
list from an **options source** instead of manually typed options. The options are
resolved live, at render time, in the language of the rendered form. Two source
kinds exist:

- **declared sources**: curated Jahia choicelist initializers, declared by the
  administrator in the module configuration;
- **categories**: the contributor picks a root category, the options are the
  categories directly underneath — no configuration involved, since categories
  are already contributor-curated content governed by JCR permissions.

## Storage model

The mode lives on the engine-owned mixin pair (industrial's mediaSource pattern):

- `fmdbmix:optionsSource` carries the `fmdb:optionsMode` switch (`manual` default);
- `fmdbmix:manualOptions` carries the unified `fmdb:options` property
  (i18n, multiple, JSON-encoded `{"value","label","selected"}` strings);
- `fmdbmix:sourcedOptions` carries only `fmdb:optionsSourceKey`;
- `fmdbmix:categoryOptions` carries only `fmdb:optionsRootCategory`, a
  weakreference to a `jnt:category` node picked with the category picker.

In both non-manual modes nothing else is stored — the option list never
materializes in the JCR.

The Content Editor switches the two `jmix:dynamicFieldset` mixins through the
`addMixin` wiring declared in the fieldset JSON overrides of formidable-elements.

## Declaring sources (administrator)

Sources are declared in `org.jahia.modules.formidable.cfg`, one per line:

```properties
optionsSources=countries|Countries|country\n\
  tv|TV screens|fmdbSampleCategoryTree|product/tv
optionsSourcesCacheTtlSeconds=300
```

Each entry has the form `id|Label|initializerKey` or `id|Label|initializerKey|param`:

| Segment | Role |
|---|---|
| `id` | Stable identifier stored in JCR (`fmdb:optionsSourceKey`) |
| `Label` | Shown to contributors in the source picker |
| `initializerKey` | Key of the Jahia choicelist initializer to evaluate (e.g. `country`) |
| `param` | Optional parameter string handed to the initializer |

The label is either a literal, or a resource-bundle key of the form
`<module>:<resource.key>` (for example
`formidable-test-module-samples-java:sample.optionsSource.tv`) resolved
server-side against that module's Java resource bundle; an unresolvable key falls
back to the raw label, so a misconfiguration stays visible. The resolution
language is the one the Content Editor hands to choicelist initializers: the
**UI language** since jcontent PR #2570 (2026-07-20) — the same language that
resolves the neighboring editor labels — and the edited content language on
older jcontent versions. A literal containing a colon (e.g. `Type: TV`) is not
mistaken for a key — the key form is strictly `module:key` without spaces.

Only declared sources are exposed to contributors — never the raw platform-wide
initializer list, most of which is context-dependent and meaningless as a form
options source. An empty `optionsSources` disables sourced options entirely
(fail-safe default).

### Initializer parameters

The parameter is **one opaque string**: everything after the third `|` is passed
verbatim to the initializer (the entry is split with a limit, so the parameter may
itself contain `|`). The Jahia initializer API
(`getChoiceListValues(epd, param, values, locale, context)`) receives a single
string — there is **no platform-wide separator for multiple parameters**; each
initializer defines its own convention. The core `nodes` initializer uses `;`
(`/path;jnt:type`), and `;` is the recommended convention for custom initializers
(for example `product/tv;2` for a hypothetical `path;depth` contract). This is the
same behavior as the CND `choicelist[initializer='param']` syntax, where the quotes
also carry a single string. (Not to be confused with the comma in
`choicelist[init1,init2]`, which separates chained initializers, not parameters.)

## Category mode

The options of a category-mode field are the categories **directly under** the
picked root: the category name is the submitted value, its localized title the
displayed label. The weakreference resolves in the workspace of the caller, so a
live form only shows **published** categories, and an unpublished or deleted root
category degrades like any failing source (see below). Category options are not
TTL-cached: the read is in-JVM, backed by Jahia's JCR caches, and a category
publication shows up on the next render.

## Resolution, cache and failures

`FormidableOptionsSourceService.resolve(sourceKey, languageTag)` (engine) evaluates
the initializer and answers in the manual-options JSON format, so the rendering
code cannot tell sourced and manual options apart. Server views call it in-process
(`server.osgi.getService`). Results are cached in memory per (source, language) for
`optionsSourcesCacheTtlSeconds` (default 300); a configuration change takes effect
immediately (cache entries remember the source definition they were resolved from),
and failures are never cached.

When a source cannot deliver (unknown key, initializer missing or failing), the
field renders an inline error instead of an empty list:

- **required field**: the form is not submittable (the submit button is disabled);
- **optional field**: the form stays usable without it.

Submitted values are validated server-side against the re-resolved list, exactly
like manual options, with no tolerance: a non-empty value absent from the list is
rejected (FMDB-010), and when the source cannot be resolved at validation time a
non-empty value is rejected as unverifiable. An empty or absent value follows the
field's `required` flag, like any other field.

## Writing a source initializer

Any module can contribute one by registering a `ModuleChoiceListInitializer` OSGi
service with a fixed key. `SampleCategoryTreeInitializer`
(formidable-test-module-samples-java) is a complete parameterized example: it lists
the child categories of a category-tree node, the parameter being the starting
point relative to `/sites/systemsite/categories`. Return localized labels using the
`locale` argument; blank values are dropped by the resolver, and a blank label
falls back to the value.
