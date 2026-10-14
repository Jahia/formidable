# Redirect action

A form action that sends the visitor to another page of the site once the submission is accepted, and the
mechanism under it: any [`ContextualFormAction`](../extension/how-to-create-form-action.md) can ask the browser to
navigate. Status: **implemented** (0.6.0). It fills a gap the [import from Jahia Forms](forms-import.md) found: Forms
has two redirect actions (to a page, to a URL), Formidable had none, so a contributor could only show the
submission message.

## What the contributor gets

A **Redirect** action (`fmdb:redirectAction`) in the form's actions zone, next to *Save to JCR* and the email
actions. It sends the visitor, once the form is submitted, either to **a page of the site** picked in the page
picker, or to **a path of the site** typed in. While it redirects, the visitor sees the form's submission message.

## Decisions

| Decision | Why |
|---|---|
| **The browser navigates; the server only says where** | A Formidable form is submitted by `XMLHttpRequest`, so the server cannot answer with an HTTP redirect the page would follow. The servlet adds the target to the JSON body of an accepted submission and the island navigates. |
| **Only once the submission is accepted** | The key is added to a `200` body only: when an action fails or the pipeline rejects the submission, the visitor stays on the form with its error. |
| **A mechanism, not only an action** | The redirect travels as the reserved key `redirect` of the body, written by the servlet from what the contextual actions returned (`FormActionResult.redirect`). The accounts actions (create account, sign in) use it; so can a third-party action. A response enricher cannot write the key. |
| **A page is a weak reference, not a path** | The page picker stores the node: the page can move or be renamed and the redirect follows; the URL is built on the server from the node, in the visitor's language, through the platform's URL rewriting (vanity URLs), as `PageLinks.pathOf`. Forms stored a path string per language, which broke on a move. The property is named `page`, not `j:linknode`: that name is the platform's, injected by its link mixins. |
| **Same-origin relative paths only, never a host** (changed from the first proposal, which allowed an absolute `http`/`https` URL) | A redirect target that a request can influence (`/login?redirect=...` read from the page's URL by the accounts actions) must not be able to name another site: that is an open redirect. One rule for every source, `RedirectTargets.sameOriginPath`: one leading slash, no scheme, no authority, no backslash, no control character, no whitespace, at most 2048 characters. The pipeline applies it again to whatever an action returned and drops the rest with a warning. A contributor who needs another website links to it from the submission message. |
| **No field value in the URL** | A query string built from the answers would put personal data in browser history, server logs and referers. |
| **One redirect per form** | Two targets make no sense: the pipeline keeps the **first** redirect of the action list. |
| **Read-only compatible** | The action writes nothing, so it carries `fmdbmix:readOnlyCompatibleAction`. |
| **Fails nothing** | A missing, unpublished or unusable target is logged and the submission is accepted without a redirect. |

## Content model

In `formidable-engine`, beside the other action types (`definitions.cnd`):

```cnd
[fmdb:redirectAction] > jnt:content, fmdbmix:formAction, fmdbmix:readOnlyCompatibleAction, mix:title
 - jcr:title (string) = resourceBundle('fmdb_redirectAction') autocreated i18n
 - targetType (string, choicelist[resourceBundle]) = 'page' autocreated indexed=no < 'page', 'path'
 - page (weakreference, picker[type='page'])
 - path (string) i18n indexed=no
```

`path` is i18n so a multilingual site can send each language to its own page. The authoring card shows
`targetType` as its key parameter (the first telling property after the title).

## Server

1. `RedirectFormAction` (engine, `actions/form/redirect`) is the `ContextualFormAction` of `fmdb:redirectAction`.
2. Resolution:
   - `page`: the `page` node read in **live**, in the submission's locale (the action node came from that
     session); its URL is `node.getUrl()` passed through the outbound URL rewriting, the servlet's `/modules`
     context path swapped for the platform's (`PageLinks`). An unpublished or deleted page does not resolve: no
     redirect, a warning.
   - `path`: the value in the submission's locale, kept only if `RedirectTargets.sameOriginPath` accepts it.
3. `FormSubmissionPipeline.keepFirstInstruction` keeps the first redirect and the first message of the action
   list; `FormSubmitServlet.addBrowserInstruction` writes them as `redirect` and `userMessage`, both in
   `RESERVED_KEYS`.

`RedirectTargets` also offers what an action that reads the target from the request needs: `pathOfReferer` (the
path and query of the page the visitor submitted from, when it is on the server the request reached) and
`returnPath` (one query parameter of that page, validated: the `/login?redirect=/account` pattern).

## Client

In `useFormSubmission`, after a successful answer:

1. the success event `formidable:submitted` is dispatched as before (the jExperience integration sends its
   submission event from it), its detail carrying the body, `redirect` included;
2. the success message is shown (the action's `userMessage` when it sent one, the form's otherwise) after the
   minimum feedback pause, the form is reset;
3. if the body holds `redirect` and it passes the browser's own check (a path starting with a single `/`, the same
   rule), the island navigates with `globalThis.location.assign(redirect)` in a task of its own, so the
   visitor's history keeps the form page for the *back* button and the event's listeners ran.

No redirect in edit mode or preview, where submission is already disabled.

## Import from Jahia Forms

Once this action ships, the [import](forms-import.md), which recreates the Forms forms, can map
`fcnt:redirectToAPageAction` to a `page` target (the Forms path resolved in the target site, reported when the page
is not found) and `fcnt:redirectToUrlAction` to a `path` target when the Forms URL is a path of the site, reported
otherwise: an absolute URL is not a possible target any more. The import itself does not map them yet.

## Tests

- Unit (engine): `RedirectTargetsTest` (what is a same-origin path, the Referer and the return parameter, the
  context path swap), `PageLinksTest`, `RedirectFormActionTest`, `FormSubmissionPipelineContextualActionsTest`
  (first redirect wins, an unsafe redirect is dropped), `FormSubmitServletBrowserInstructionTest` (the keys, the
  escaping, the reservation).
- Unit (elements): `useFormSubmission.dom.test.ts` (navigation after the success message, refusal of an unsafe
  `redirect`, `userMessage`).
- Cypress: `tests/cypress/e2e/actions/90-redirect-action.cy.ts` (a form redirecting to a page, to a path, a hostile
  path stored in the node answering without a redirect).

## Open points

| # | Point |
|---|---|
| 1 | A delay before redirecting, so the visitor can read the submission message, is not built: the island navigates after the success message and the minimum feedback pause (half a second). |
| 2 | Does the jExperience submission event reach jCustomer when the page navigates right after it (the tracker's send is asynchronous)? To measure; a short wait or `sendBeacon` if not. |
| 3 | A redirect to a page of another site of the platform: the picker allows it, the same-origin rule keeps it only when both sites answer on the same server name. |
