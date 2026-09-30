# Configuration files

The engine is configured through five OSGi configuration files, one per theme, all under
`digital-factory-data/karaf/etc/`. The module ships each file with every setting at its default and a
comment for each; Jahia copies a file there the first time the module starts without it, and never
overwrites the copy afterwards (its first line is the `# default configuration` marker the extender looks
for). Edit the files, or use the provisioning API (`editConfiguration` with the theme's PID); fileinstall
applies a change without a restart. Avoid the Felix Web Console for these PIDs: it rewrites the file in a
typed syntax (`L"5"`, quoted strings) a `.cfg` file does not read back.

## The five themes

| Theme | PID and file (`karaf/etc/<PID>.cfg`) | Settings | Read by |
|---|---|---|---|
| CAPTCHA | `org.jahia.modules.formidable.captcha` | `captchaSiteKey`, `captchaSecretKey`, `captchaScriptUrl`, `captchaWidgetVar`, `captchaTokenField`, `captchaVerifyUrl`, `captchaHttpConnectTimeoutSeconds`, `captchaHttpRequestTimeoutSeconds` | the widget in the page, the verification at submission — [CAPTCHA server-side validation](captcha-server-side-validation.md) |
| Uploads | `org.jahia.modules.formidable.uploads` | `uploadMaxFileSizeBytes`, `uploadMaxRequestSizeBytes`, `uploadMaxFileCount`, `uploadAllowedTypes` | the multipart parser, the early size guard, the e-mail action's attachment bound, the file field and its `accept` choicelist — [Allowed file types](#allowed-file-types) |
| Choice options | `org.jahia.modules.formidable.choiceOptions` | `optionsSources`, `optionsSourcesCacheTtlSeconds`, `optionsQueryMaxResults` | the options sources a choice field may pick — [Choice field options sources](../architecture/choice-field-options-sources.md#declaring-sources-administrator) |
| Form actions | `org.jahia.modules.formidable.formActions` | `forwardTargets`, `enableDevForwardTargets`, `devForwardTargets`, `forwardHttpConnectTimeoutSeconds`, `forwardHttpRequestTimeoutSeconds` | the forward action and its target picker |
| Field actions | `org.jahia.modules.formidable.fieldActions` | `fieldActionProviders`, `enableDevFieldActionProviders`, `devFieldActionProviders`, `fieldActionHttpConnectTimeoutSeconds`, `fieldActionHttpRequestTimeoutSeconds`, `fieldActionVerdictCacheTtlSeconds`, `fieldActionRateLimitPerMinute`, `fieldActionMaxValueLength`, `fieldActionMaxValuesPerField` | the field actions' providers and the pre-check endpoint — [Field actions: providers and limits](field-actions.md) |

The setting names are the ones of the single file of earlier builds but one: `uploadAllowedMimeTypes` is now
`uploadAllowedTypes`, since it takes extensions too. Any other line copied from an old file into its theme's
file is read as it was; the migration below reads the renamed setting under its former name, and does not carry
a list still at the former default — the new default holds the same seventeen types, written as extensions. The PIDs are dotted on purpose — `org.jahia.modules.formidable-captcha`
would declare an instance of a factory configuration, which none of these is — so the five files sort
together in `karaf/etc/` and in the Felix console.

Each file is logged when it is read (`CaptchaConfigService configured: …`, `UploadsConfigService configured: …`,
and so on), with what was accepted; a refused line — a target without HTTPS, a provider line whose sixth part is
neither `header` nor `query` — is logged with its id and the reason, never a credential. A zero or negative
timeout or bound is refused and the default applies, with a warning naming the setting.

## Allowed file types

`uploadAllowedTypes` lists the file types a file field may accept, comma-separated. Each entry is an
extension (`pdf`, `.docx`), a MIME type (`application/pdf`) or a wildcard (`image/*`). An extension stands for
the MIME type Apache Tika gives it, and a MIME alias stands for its canonical type (`audio/x-wav` is
`audio/vnd.wave`, the type Tika detects a WAV file as): the engine resolves every entry once, when it reads the
file, and logs what it resolved (`uploadAllowedTypes: read as [pdf = application/pdf, …]`); an entry that is
neither a MIME type nor an extension Tika knows is ignored, with a warning. From then on everything compares MIME
types, including the check of every uploaded file, whose real type Tika detects from its content and name: `txt`
allows `text/plain`, the type Tika gives any plain-text file whatever its extension (`.cnd`, `.pom`).

An extension shared by several types stands for one of them only — `ogg` is read as `audio/vorbis`, an Ogg video
is `ogv` (`video/ogg`). Give the MIME type when the extension is ambiguous. Tika's registry of the version the
module embeds says which type each extension stands for:
[tika-mimetypes.xml of Tika 3.3.2](https://github.com/apache/tika/blob/3.3.2/tika-core/src/main/resources/org/apache/tika/mime/tika-mimetypes.xml)
(search for `*.ogg`). The default list, `jpg,png,gif,webp,pdf,doc,docx,xls,xlsx,odt,ods,txt,csv,mp4,webm,ogv,mkv`,
holds no ambiguous extension.

Tika detects what is inside a container, which the file's name does not say: an Ogg video named `.ogv` is
detected as `video/theora`, an Opus track named `.oga` as `audio/opus`. Tika's registry declares these kinds of
`video/ogg` and `audio/ogg`, so a file is also accepted when its detected type is a kind of the type its name
stands for, and that type is listed as such — `ogv` or `video/ogg`, not only `video/*`. The registry's "kind
of" means "readable as", so three limits keep it from widening the list: the name's type must be listed itself;
both types must share their top-level type (an SVG named `.xml` is an image, and stays refused where `xml` is
listed); and the name's type must not be `text/plain` or `application/octet-stream`, of which every text or
every file is a kind (an HTML page named `.txt` stays refused where `txt` is listed).

The list is the whole of what a file field may accept:

- a field whose **Accept** setting is empty accepts every listed type;
- a field whose **Accept** setting names types keeps those still listed — a type removed from the list is no
  longer offered to the visitor nor accepted by the server, and a warning names the field and the type each time
  the field is rendered afresh — not when the page is served from the cache —
  (`Field '<path>' accepts '<type>', which the uploads configuration does not allow: ignored`);
  the content keeps the value, and the field honours it again if the type is
  listed again;
- an empty list accepts no file at all: every file field refuses every file, and the engine says so when it reads
  the file. To accept any file, list the top-level wildcards, `application/*,audio/*,image/*,text/*,video/*`;
  `*/*` is not accepted.

The server applies a change at once. Pages already rendered keep offering the former list — in the file picker, the
formats they list and their own check before sending — until the site's cache is flushed (jContent, or the
`flushSiteCache` GraphQL mutation): a type removed is then refused only when the form is sent, a type added is
refused by the page before anything is sent.

The **Accept** setting offers the listed types, each with the module's translated wording where the module ships
one (the seventeen default types), else with a label made from the type — its acronym or extension, then the
extension: "ZIP (.zip)". An administrator cannot give a type of their own a translated label; a developer adds
the key `fmdb_inputFile.accept.<mime/type>` to the module's resource bundle.

## Upgrading from the single file

Until 0.5 the engine read one PID, `org.jahia.modules.formidable`, whether from
`karaf/etc/org.jahia.modules.formidable.cfg` (written by the provisioning API, or by hand) or from ConfigAdmin
alone (the Felix console). Neither is read any more, and both are carried over at the first start of a
version with the themes:

1. Jahia copies the five theme files to `karaf/etc/` and fileinstall loads them, every setting at its default.
2. As soon as a theme's configuration comes from its file, the theme reads the old configuration through
   ConfigAdmin and copies every setting of the theme it holds at a value other than the default — as long as
   the theme's file still holds the default for it. A value already set in the new file wins, with a warning
   naming the setting. The values are written into the theme's file with a marker (`formidable.migratedFrom`);
   a theme with nothing to carry — a new installation, or an old configuration at its defaults for that theme —
   gets the marker too. The migration therefore runs once per theme, whatever it finds: an old configuration that
   appears later (a provisioning script still writing `org.jahia.modules.formidable`) is never read. The log shows
   `[org.jahia.modules.formidable.<theme>] Carried over from org.jahia.modules.formidable into the theme's file: [...]`,
   or `Nothing of org.jahia.modules.formidable to carry over`.
3. The old file, when there is one, gets a first line saying it is no longer read, whether or not anything was
   carried from it. It is never deleted — the administrator wrote it — and can be removed by hand once the five
   files are checked.
4. A write that fails (a persistence directory full or read-only) is tried three times in all, thirty seconds
   apart. Meanwhile the settings to carry stay in force — a CAPTCHA key or a forward target of the old file keeps
   working, the new file's defaults do not take over. After the third failure the theme's file rules as it stands
   and the log says `Gave up carrying the settings of org.jahia.modules.formidable over … re-enter the settings of
   this theme in it`. A restart starts the three attempts over.

**How to check**: open the five files after the upgrade. A setting that was not at its default before the
upgrade is there, in its theme's file, on a line without a comment; the marker line
`formidable.migratedFrom=org.jahia.modules.formidable` is the trace that the migration wrote the file. A
setting the old configuration held that is not in its theme's file was either at its default (then it was
not copied) or lost to a write that kept failing (then the log says so): re-enter it.

**In a cluster**, each node reads its own `karaf/etc`: the migration runs on every node at its first start, from
that node's old configuration. Keep the five files the same on every node, as for any file of `karaf/etc`.
