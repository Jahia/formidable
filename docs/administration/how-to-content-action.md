# Create content from a form (formidable-content)

The `formidable-content` module adds one form action: **Create content** (`fmdbcnt:createContentAction`). A **signed-in** user
fills in a Formidable form; the action turns the submission into a content node of a type you choose (the form's fields fill its
properties), stores the uploaded files as files that the content references, and publishes both, in a folder that **the user's
own group** is entitled to.

It is meant for the extranet case: suppliers, partners or members who send briefs, proposals, photos or videos, each into
the folder of their own company, without ever being given write access to the repository.

Requirements: Jahia **8.2.2.0 or later** and `formidable-engine` 0.6. Nothing else: no configuration file, no CSRF entry, no
endpoint of its own (the form is submitted through the engine's endpoint). Module reference:
[`formidable-content/README.md`](../../formidable-content/README.md).

## Contents

- [Before you start (administrator)](#before-you-start-administrator)
- [Configure the action (author)](#configure-the-action-author)
- [The mapping lines](#the-mapping-lines)
- [Files](#files)
- [The security model](#the-security-model)
- [A worked example](#a-worked-example)
- [Troubleshooting](#troubleshooting)

## Before you start (administrator)

1. Deploy the engine, then `formidable-content`; enable `formidable-content` on the site.
2. Create the **folders**. With the group mechanism below, that is one folder tree for the content and one for the files, and
   **one subfolder per company** in each. Create the subfolders yourself, with their ACLs: the action never creates a folder,
   because their access rights are your decision, not the form's.
3. For each company's subfolder, **break the ACL inheritance** and grant the company's group the `reader` role (or any role
   that includes read in live). That ACL is what the action relies on (see the security model): the group name only *names* a
   folder, the folder's rights decide who may use it.
4. **Publish** the folders: the action reads them in the live workspace.
5. Create the groups, one per company, named with a common prefix (`orvane-supplier-northlight`, `orvane-supplier-harbor`), and put
   each user in their company's group. A user in **two** of those groups cannot send content (the action will not guess which
   company they mean).

## Configure the action (author)

Add a **Create content** action to the form. Turn on the form's **Require authentication** option too: the engine then rejects
an anonymous submission (FMDB-009) before any action runs. The action refuses guests anyway, so the option is a convenience, not
the protection.

| Setting | What it does |
|---|---|
| **Content type** | The node type to create, with its prefix (`ctmd:submission`). It must exist, not be abstract, and be allowed in the target folder; checked at every submission |
| **Content folder** | Where the content goes. With a group prefix, the folder that holds one subfolder per group |
| **Files folder** | Where uploaded files go (same subfolder layout). Required as soon as a file is mapped |
| **Group prefix** | Optional. The submitter's groups are searched for the one whose name starts with it; the rest of the name is the subfolder, in both folders. None, several or a missing subfolder refuse the submission |
| **Title field** | The field whose value is the title (`jcr:title`) of the content, in every language of the site; it also names the node. The type needs `mix:title` |
| **Field mappings** | `formField=property`, one line per field |
| **File mappings** | `fileField=property`, one line per upload field |
| **File mixin**, **File properties** | A mixin added to every stored file, and `formField=fileProperty` lines written on every stored file |
| **Fixed values** | `property=value`, one line per value |
| **Submitter property** | Receives the user name of the person who submitted |
| **Organization property** | Receives the user's `j:organization` |
| **Page property** | Receives a reference to the page the form was submitted from |
| **Required companion files**, **Message for a missing companion file** | `dependentField<-field:mimePattern` rules and the sentence the visitor reads when one is broken |
| **Publish** | On by default: the content and its files are published, in every language of the site |

Publish the form. Submissions run against the live workspace. The action does **not** carry the read-only compatibility marker:
it writes, so the form is unavailable during a read-only maintenance window.

## The mapping lines

Every mapping setting is a list of lines `left=right`. The line is cut at its **first** `=` (a fixed value may contain one);
both sides are trimmed; blank lines are ignored. A malformed line is a configuration error: the submission is refused (the
visitor reads "temporarily unavailable"), and the log names the setting and the line.

**Field mappings** convert the submitted text to the type the property's definition declares:

| Property type | What is accepted |
|---|---|
| string | Any text |
| long | A whole number (`42`, `-7`), nothing else: no `4.2`, no `0x10`, no `1e3` |
| double, decimal | A plain decimal literal (`1250.5`, `1e3`); no `NaN`, no `Infinity`, no `1d` |
| boolean | `true`, `false`, `on`, `off`, `yes`, `no`, `1`, `0` |
| date | `yyyy-MM-dd` (what a date field sends), stored as the start of that day in UTC |
| multiple property | Every value of the field (a checkbox group); a single property takes the first |

A field left blank writes nothing. A value that does not fit refuses the submission with "one of the values you entered is
not valid" (the log says which field, never what was typed). A property that the type does not declare, a protected one, one of
the platform's own (`jcr:` and `rep:`, except `jcr:title` and `jcr:description`), or a type that text cannot fill (a reference,
a binary) is a configuration error.

When several settings fill the same property, the later one wins: **the form's fields, then the fixed values, then the
submitter, the organization and the page**. What a visitor types can never override what the platform knows.

## Files

**File mappings** (`fileField=property`) store the files of an upload field and point the content at them. The property must be a
`reference` or `weakreference` property; if it is not multiple, a submission with more than one file for it is refused.

Each file is stored as a `jnt:file` under the files folder (and the user's subfolder):

- its node name is the original name made safe: lower case, accents removed, anything but letters and digits turned into a
  hyphen, at most 80 characters, the extension kept; a name that is taken gets `-1`, `-2`... (and a random suffix in the rare
  case of two submissions at the same instant);
- `jcr:title` is the original name, in every language of the site;
- the MIME type is the one the engine detected, never the browser's;
- an image the JDK can read (PNG, JPEG, GIF, BMP) also gets `jmix:image` with its width and height (read from the header, the image
  is not decoded);
- the **file mixin** is added, and the **file properties** are written (`credit=ctmdCredit`: the value of the field `credit`).

Files whose field is not in the file mappings are not stored. **Required companion files** (`captions<-media:video/*`) say
that when `media` holds *n* files whose detected type matches the pattern (`video/mp4`, `video/*`, `*/*`),
`captions` must hold at least *n* files; otherwise the submission is refused with your sentence (or a generic one) before
anything is written.

## The security model

The form is public; the action is not, and it writes with a **system session**. The submitter holds no write right in the
`default` workspace (a reader in an extranet never does), so no session of theirs could create the content: this is the usual
pattern for public-facing writes, with the submitter recorded on the node. The session is attributed to the submitter
(`jcr:createdBy`), but it applies no ACL. Everything that decides *where* the system session may write is therefore decided
**before** it is used, and again at **every** submission, in this order:

1. **Only signed-in users.** A guest is refused with 401 before the settings are read.
2. **The submitter's live session** must be a live session (it is, when the form is submitted normally).
3. **The submitter's folder.** With a group prefix, the submitter's groups (the platform's membership, nested groups included;
   only global groups and groups of the form's own site count) are searched for the one that starts with the prefix. No group,
   several groups, or what is left of the name not being a plain node name (no `/`, no `..`) refuses the submission. The
   subfolder must **already exist** under the content folder (and the files folder): it is never created.
4. **The submitter's own live session must be able to read the content subfolder, and the files subfolder.** An unreadable
   node is a `PathNotFoundException` for a user session, so this applies the folder's ACLs as the user. This is the check that
   keeps one company from posting into another's folder, **even if the form is misconfigured** (a wrong folder, a group named
   like another company's): the folder named by a group is only used when the folder's own rights say the user belongs there. A
   check that cannot be made refuses too.
5. **The type and the mappings.** The type must exist, be concrete, and be creatable in that folder; every mapped property must
   be declared by it; every value must convert.
6. **Then the write**: files first (each saved on its own), then the content with the references to them, then the translations,
   then the publication. If a step fails, what the earlier steps created is removed (and unpublished when publication had
   started); the visitor reads "we could not save your submission".

What the visitor reads is always generic: "your account is not set up to send content here" for 3 and 4, "one of the values is
not valid" for a bad value, "temporarily unavailable" for a configuration error. Folder names, group names and properties go to
the server log only.

**The page property** is found from the `Referer` the browser sent, which the visitor controls. It is only used to *find*
a node: the address must be on the server the request reached, under `/sites/<this site>/` (behind a language such as `/fr/` or
Jahia's `/cms/render/live/fr/` prefix; the query, the fragment and the extension are dropped), with no empty, dotted or
encoded-separator segment; the node it names must be readable by the submitter in live **and** be a page or a content with its
own page. Anything else (another site, a vanity URL, an unreadable or ordinary node) leaves the property empty, never an error.

**What the module does not do**

- It adds **no CAPTCHA and no rate limit**: users are signed in and accountable. Turn on the form's CAPTCHA if you want one; the
  engine's upload limits (size, types) apply.
- It trusts the form's **author**: whoever can edit the action chooses the type, the folders and the properties. Protect the
  form's editing like any other configuration.
- It trusts the **folder ACLs** you set. A group with read access to a folder is allowed to send content into it.
- Publication follows the **Publish** setting, not the submitter's own right to publish: the whole point is that the
  submitter has none.
- Names are made unique by checking, then by retrying; two submissions of one file name at the same instant can cost one of
  them a retry. Nothing is overwritten.

## A worked example

Suppliers send briefs, each into their own folder, from the page of a brief.

- Groups `orvane-supplier-northlight` and `orvane-supplier-harbor`; folders `/sites/orvane/contents/briefs/<name>` and
  `/sites/orvane/files/briefs/<name>`, each closed to everyone but its group, published.
- A content type `ctmd:submission` (`mix:title`, a `submissionStatus` string, `submittedBy`, `company`, a `brief` weak reference,
  a multiple weak reference `media`, a multiple weak reference `captions`) and a mixin `ctmdmix:credited` with `ctmdCredit`.

The action:

| Setting | Value |
|---|---|
| Content type | `ctmd:submission` |
| Content folder / Files folder | `/sites/orvane/contents/briefs` / `/sites/orvane/files/briefs` |
| Group prefix | `orvane-supplier-` |
| Title field | `title` |
| Field mappings | `summary=description` |
| File mappings | `media=media`<br>`captions=captions` |
| File mixin / File properties | `ctmdmix:credited` / `credit=ctmdCredit` |
| Fixed values | `submissionStatus=submitted` |
| Submitter, Organization, Page property | `submittedBy`, `company`, `brief` |
| Required companion files | `captions<-media:video/*` |
| Message for a missing companion file | Every video needs its captions file (WebVTT). |

A member of `orvane-supplier-northlight` who submits from `/sites/orvane/contents/briefs/summer-launch.html` creates
`/sites/orvane/contents/briefs/northlight/<title>`, with `submissionStatus` `submitted`, `submittedBy` their name, `company` their
organization and `brief` pointing at the summer-launch brief; their files are in `/sites/orvane/files/briefs/northlight`. They see
it in live as soon as the publication ends. A redirect action placed after it can send them to the list of their submissions.

## Troubleshooting

The server log says what the visitor is not told, with the prefix `[formidable-content]`.

| The visitor reads | The log says | Cause |
|---|---|---|
| Please sign in to send this form | `the submitter is a guest` | Not signed in. Turn on **Require authentication** on the form |
| Your account is not set up to send content here | `has no folder through the groups of <user> ... (NONE)` | No group of the user starts with the prefix |
| same | `(SEVERAL)` | The user is in two company groups |
| same | `(UNSAFE)` | What is left of the group name is not a plain node name |
| same | `The folder <path> of <user> does not exist` | The subfolder was not created: create it, with its ACL, and publish it |
| same | `<user> cannot read <path> in live` | The folder's ACL does not give the user's group a read role, or it is not published |
| This form is temporarily unavailable | `the action is misconfigured. Setting '...'` | A malformed line, an unknown property, a type that cannot be created there, a folder that is not published |
| One of the values you entered is not valid | `field '...': Property '...' expects ...` | A value that does not fit its property |
| The author's sentence, or "a file is missing" | `a file of '...' matching ... has no companion` | A required companion file is missing |
| We could not save your submission | `could not be created in <path>: nothing was kept` | The platform refused a step (mandatory property, constraint, publication); the cause is logged |
