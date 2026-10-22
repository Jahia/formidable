# Redirecting after a submission

How a form action sends the visitor to another page once the submission is accepted: any
[`ContextualFormAction`](../extension/how-to-create-form-action.md) returns a target, the servlet names it in the
response, the island navigates. Status: **implemented** (0.6.0). It fills a gap the
[import from Jahia Forms](forms-import.md) found: Forms has two redirect actions (to a page, to a URL), Formidable had
no way to send the visitor anywhere, so a contributor could only show the submission message.

The engine ships the **mechanism**, not a redirect action. The Redirect action contributors add to a form (a page of
the site or a same-origin path) is the module `formidable-redirect` of
[Jahia/formidable-extended-actions](https://github.com/Jahia/formidable-extended-actions), with the other actions
kept outside the core release; the sign-up and sign-in actions of `formidable-accounts` use the same mechanism.

## Decisions

| Decision | Why |
|---|---|
| **The browser navigates; the server only says where** | A Formidable form is submitted by `XMLHttpRequest`, so the server cannot answer with an HTTP redirect the page would follow. The servlet adds the target to the JSON body of an accepted submission and the island navigates. |
| **Only once the submission is accepted** | The key is added to a `200` body only: when an action fails or the pipeline rejects the submission, the visitor stays on the form with its error. |
| **A result of the action, not a response enricher** | The target travels as the reserved key `redirect` of the body, written by the servlet from what the contextual actions returned (`FormActionResult.redirect`). A response enricher cannot write the key. |
| **A page is turned into a path on the server** | An action that stores a page (a weak reference: it follows a move or a rename) builds its URL with `PageLinks.pathOf`, in the visitor's language, through the platform's URL rewriting (vanity URLs), never as a hand-built `/<lang>/<path>.html`. |
| **Same-origin relative paths only, never a host** | A target that a request can influence (`/login?redirect=...` read from the page's URL by a sign-in action, for instance) must not be able to name another site: that is an open redirect. One rule for every source, `RedirectTargets.sameOriginPath`: one leading slash, no scheme, no authority, no backslash, no control character, no whitespace, at most 2048 characters. The pipeline applies it again to whatever an action returned and drops the rest with a warning. |
| **No field value in the URL** | A query string built from the answers would put personal data in browser history, server logs and referers. |
| **One redirect per form** | Two targets make no sense: the pipeline keeps the **first** redirect of the action list. |

## Server

1. A `ContextualFormAction` returns `FormActionResult.redirect(path)` (with or without a message).
2. `FormSubmissionPipeline.keepFirstInstruction` keeps the first redirect and the first message of the action list
   and drops a redirect that `RedirectTargets.sameOriginPath` refuses.
3. `FormSubmitServlet.addBrowserInstruction` writes them as `redirect` and `userMessage`, both in `RESERVED_KEYS`.

What an action has for computing a target:

| Helper | For |
|---|---|
| `PageLinks.pathOf(page, request, response)` | the public path of a page node, the servlet's `/modules` context path swapped for the platform's; empty when the page does not resolve |
| `RedirectTargets.sameOriginPath(value)` | a path a contributor typed, or any computed value |
| `RedirectTargets.pathOfReferer(request)` | the path and query of the page the visitor submitted from, when it is on the server the request reached |
| `RedirectTargets.returnPath(request, parameter)` | one query parameter of that page, validated: the `/login?redirect=/account` pattern |

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

The [import](forms-import.md) reports `fcnt:redirectToAPageAction` and `fcnt:redirectToUrlAction` with their target.
It can map them once the site has the Redirect action of `formidable-redirect`: a page target (the Forms path
resolved in the target site, reported when the page is not found) and a path target when the Forms URL is a path of
the site, reported otherwise (an absolute URL is not a possible target).

## Tests

- Unit (engine): `RedirectTargetsTest` (what is a same-origin path, the Referer and the return parameter, the
  context path swap), `PageLinksTest`, `FormSubmissionPipelineContextualActionsTest` (first redirect wins, an
  unsafe redirect is dropped), `FormSubmitServletBrowserInstructionTest` (the keys, the escaping, the reservation).
- Unit (elements): `useFormSubmission.dom.test.ts` (navigation after the success message, refusal of an unsafe
  `redirect`, `userMessage`).
- End to end: with the actions that use it, in formidable-extended-actions.

## Open points

| # | Point |
|---|---|
| 1 | A delay before redirecting, so the visitor can read the submission message, is not built: the island navigates after the success message and the minimum feedback pause (half a second). |
| 2 | Does the jExperience submission event reach jCustomer when the page navigates right after it (the tracker's send is asynchronous)? To measure; a short wait or `sendBeacon` if not. |
| 3 | A redirect to a page of another site of the platform: the same-origin rule keeps it only when both sites answer on the same server name. |
