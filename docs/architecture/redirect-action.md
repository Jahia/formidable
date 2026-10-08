# Redirect action

Specification of a form action that sends the visitor to another page once the submission is accepted.
Status: **proposal for review** — nothing of it is implemented yet. It fills a gap the
[import from Jahia Forms](forms-import.md) found: Forms has two redirect actions (to a page, to a URL),
Formidable none, so a contributor can only show the submission message.

## What the contributor gets

A **Redirect** action in the form's actions zone, next to *Save to JCR* and the email actions. It sends
the visitor, once the form is submitted, either to **a page of the site** picked in the page picker, or to
**an external URL**. While it redirects, the visitor sees the form's submission message.

## Decisions

| Decision | Why |
|---|---|
| **The browser navigates; the server only says where** | A Formidable form is submitted by `fetch`, so the server cannot answer with an HTTP redirect the page would follow. The servlet adds the target to the JSON body of an accepted submission and the client navigates — the pattern Forms used (`redirectUrl` in its answer, `RedirectToAPageAction`). |
| **Only once the submission is accepted** | The target is added to a `200` body only: when an action fails or the pipeline rejects the submission, the visitor stays on the form with its error, as today. |
| **A page is a weak reference, not a path** | The page picker stores the node (`j:linknode`): the page can move or be renamed and the redirect follows; the URL is built on the server from the node, in the visitor's language, through the platform's URL rewriting (vanity URLs). Forms stored a path string per language, which broke on a move. |
| **An external URL is `http` or `https` only, read from the action node, never from the request** | No `javascript:` or data URL, and no open redirect: the visitor cannot choose where the form sends them. Unlike the [forward action](form-submission-flow.md), no host allow-list: the browser goes there, no submission data does. |
| **No field value in the URL** (first version) | A query string built from the answers would put personal data in browser history, server logs and referers. Reconsider on demand, with an explicit list of fields. |
| **One redirect per form** | Two targets make no sense; the first redirect action in the list wins, and the actions zone warns the contributor about the others. |
| **Read-only compatible** | The action writes nothing, so it carries `fmdbmix:readOnlyCompatibleAction` and does not block its form during a maintenance window. |

## Content model

In `formidable-engine`, beside the other action types (`definitions.cnd`):

```cnd
[fmdb:redirectAction] > jnt:content, fmdbmix:formAction, fmdbmix:readOnlyCompatibleAction, mix:title
 - jcr:title (string) = resourceBundle('fmdb_redirectAction') autocreated i18n
 - targetType (string, choicelist[resourceBundle]) = 'page' autocreated indexed=no < 'page', 'url'
 - j:linknode (weakreference, picker[type='page']) < 'jmix:navMenuItem'
 - url (string) i18n indexed=no
```

The two targets are shown according to `targetType` (a dynamic fieldset per choice, as the date bounds
do). `url` is i18n so a multilingual site can send each language to its own external page; `j:linknode`
is shared, the page being the same node in every language. The authoring card shows the target as its
key parameter (the page's title, or the URL).

## Server

1. `RedirectFormAction` (engine, `actions/form/redirect`) is the `FormAction` of `fmdb:redirectAction`. Its
   `execute` resolves the target and keeps it on the request; it fails nothing — a missing page or an
   invalid URL is logged as a warning and the submission is accepted without a redirect.
2. Resolution:
   - `page`: the `j:linknode` node read in **live**, in the submission's locale (the `lang` parameter);
     its URL built as the platform builds a page link, then passed through the outbound URL rewriting
     (vanity URLs). Unpublished or deleted page: no redirect, a warning.
   - `url`: the value in the submission's locale, falling back to the form's building language; kept only
     if it parses as an absolute `http`/`https` URL.
3. The submit servlet, once every action succeeded, adds `"redirect": "<url>"` to the body
   (`redirect` joins the servlet's reserved keys in `FormSubmitServlet.RESERVED_KEYS`).

## Client

In `useFormSubmission`, after a successful answer:

1. show the submission message, as today;
2. dispatch the success event as today — the jExperience integration sends its submission event from it;
3. if the body holds `redirect`, navigate with `window.location.assign(redirect)` once the event's
   listeners ran (next task), so the visitor's history keeps the form page for the *back* button.

No redirect in edit mode or preview, where submission is already disabled.

## Import from Jahia Forms

Once this action ships, the [import](forms-import.md), which recreates the Forms forms, maps `fcnt:redirectToAPageAction` to a `page` target —
the Forms path resolved in the target site, reported when the page is not found — and
`fcnt:redirectToUrlAction` to a `url` target, its value per language.

## Tests

- Unit: URL resolution (scheme check, locale fallback), the reserved key.
- Cypress: a live form redirecting to a page (lands on it, in French on the French form, through a vanity
  URL), to an external URL (the navigation is asked for: stubbed `location.assign`), a failing action (no
  redirect, the error shown), a deleted target page (accepted, no redirect), the actions zone card and
  its warning for a second redirect.

## Open points

| # | Point |
|---|---|
| 1 | A delay before redirecting, so the visitor can read the submission message — or redirect at once and skip the message? |
| 2 | Does the jExperience submission event reach jCustomer when the page navigates right after it (the tracker's send is asynchronous)? To measure; a short wait or `sendBeacon` if not. |
| 3 | A redirect to a page of another site of the platform: allowed by the picker? |
