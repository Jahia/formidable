# Redirect action

A form action that sends the visitor to a page or to a web address once the submission is accepted, instead of the
submission message, and the mechanism under it: any [`ContextualFormAction`](../extension/how-to-create-form-action.md)
can ask the browser to navigate. It matches the two redirect actions of Jahia Forms (`fcnt:redirectToAPageAction`,
`fcnt:redirectToUrlAction`), which the [import](forms-import.md) converts.

## What the contributor gets

A **Redirect** action (`fmdb:redirectAction`) in the form's actions zone, next to *Save to JCR* and the email
actions. It sends the visitor, once the form is submitted, either to **a page** picked in the page picker, or to
**a web address** typed in, over `http` or `https`: another website, or a page the picker cannot reach. The visitor does not see
the submission message: the spinner stays on screen until the next page shows.

In the actions zone, the card of a redirect shows its target (the page's title, or the address), and a redirect
that comes after another one carries a warning.

## Decisions

| Decision | Why |
|---|---|
| **The browser navigates; the server only says where** | A Formidable form is submitted by `XMLHttpRequest`, so the server cannot answer with an HTTP redirect the page would follow. The servlet adds the target to the JSON body of an accepted submission and the island navigates. |
| **Only once the submission is accepted** | The key is added to a `200` body only: when an action fails or the pipeline rejects the submission, the visitor stays on the form with its error. This differs from Jahia Forms, which caught a failing action, carried on and still redirected: a migrated form whose email action fails now shows the error instead of the thank-you page. |
| **The redirect replaces the success message** | As in Jahia Forms: the next page is the feedback. The island's side, the back-forward cache included, is the [contextual actions](form-submission-flow.md#contextual-actions-the-response-a-redirect-a-message-for-the-visitor)' behaviour. |
| **A mechanism, not only an action** | The redirect travels as the reserved key `redirect` of the body, written by the servlet from what the contextual actions returned (`FormActionResult.redirect`). A sign-up or sign-in action of another module uses it; so can a third-party action. A response enricher cannot write the key. |
| **A page is a weak reference, not a path** | The page picker stores the node: the page can move or be renamed and the redirect follows; the URL is built on the server from the node, in the visitor's language, through the platform's URL rewriting (vanity URLs), as `PageLinks.urlOf`. Forms stored a path string per language, which broke on a move. The property is named `page`, not `j:linknode`: that name is the platform's, injected by its link mixins. |
| **A typed address is an `http`/`https` URL** | Jahia Forms accepts an external URL, and so does Formidable, without the flaws of Forms: it follows a URL its own validator refused, checks nothing in the browser, and prefixes `http://` to whatever has no scheme. A typed URL is a redirect to a URL read from the configuration (`FormActionResult.redirectToUrl`, held to `RedirectTargets.httpUrl`: a host, no user information), which the browser checks again. A value without a scheme (`www.example.com`) is refused, not completed. |
| **A page of the site is picked, never typed** | A typed path (`/sites/mysite/thank-you.html`) misses what the page target gives: it breaks when the page moves, ignores the visitor's language and the vanity URLs, and lacks the context path of a Jahia deployed under one. The web address takes no path, and the page picker holds a page of the site. |
| **A URL is read from the action node, never from the request** | A target a request can influence (`/login?redirect=...` read from the page's URL by a sign-in action) must not be able to name another host: that is an open redirect. The redirect action reads its URL from its own node, which is what `redirectToUrl` is for; the [contextual actions](form-submission-flow.md#contextual-actions-the-response-a-redirect-a-message-for-the-visitor) state the two rules. |
| **No `authorizedRedirectHosts` check** | That platform setting guards the redirects core reads from request parameters (login, logout, `jcrRedirectTo`). A contributor-typed target is not one, and Jahia Forms never applied the setting: checking it would break imported redirects to unlisted hosts. An allowlist, if a security review requires one, reuses `SettingsBean.getAuthorizedRedirectHosts()`, not a new Formidable key. |
| **A language without a URL falls back** | The URL of the site's default language, else the first language that has one, so that a multilingual form redirects in every language. Jahia Forms took the first translation it found. |
| **The page is read as the system** | The actions run in a system session, so a page the visitor may not read resolves; its access rights apply when the browser loads it, as for any link. The CND limits the reference to `jnt:page`, the picker to pages. Jahia Forms behaved the same way. |
| **No field value in the URL** | A query string built from the answers would put personal data in browser history, server logs and referers. |
| **The first usable redirect wins** | Two targets make no sense: the pipeline keeps the first redirect of the action list that passes the rule, so a redirect whose page is unpublished leaves the next one to apply. Jahia Forms followed its *last* redirect; the import converts only that one, so a migrated form keeps its target. |
| **Read-only compatible** | The action writes nothing, so it carries `fmdbmix:readOnlyCompatibleAction`. |
| **Fails nothing** | A missing, unpublished or unusable target is logged and the submission is accepted without a redirect: the visitor reads the submission message. |

## Content model

In `formidable-engine`, beside the other action types (`definitions.cnd`):

```cnd
[fmdb:redirectAction] > jnt:content, fmdbmix:formAction, fmdbmix:readOnlyCompatibleAction, mix:title
 - jcr:title (string) = resourceBundle('fmdb_redirectAction') autocreated i18n
 - targetType (string, choicelist) = 'page' autocreated indexed=no < 'page', 'url'

[fmdbmix:redirectToPage] > jmix:dynamicFieldset mixin
 extends = fmdb:redirectAction
 - page (weakreference, picker[type='page']) < 'jnt:page'

[fmdbmix:redirectToUrl] > jmix:dynamicFieldset mixin
 extends = fmdb:redirectAction
 - url (string) i18n indexed=no < '[hH][tT][tT][pP][sS]?://.*'
```

`targetType` is the switch, and each target lives in a mode fieldset ([mixin catalogue](mixin-catalogue.md)): the
engine's override `fieldsets/fmdb_redirectAction.json` adds `fmdbmix:redirectToPage` for `page` and
`fmdbmix:redirectToUrl` for `url`, so the Content Editor shows the one target the action reads, and switching removes
the other one with its value. The default `page` activates the page mixin on creation, and
`fieldsets/fmdbmix_redirectToPage.json` makes the page required. Both happen in the Content Editor only: a client that
creates the node through GraphQL or REST, the import included, adds `fmdbmix:redirectToPage` or
`fmdbmix:redirectToUrl` before it sets the target, since the type alone has no definition for `page` or `url`.

`url` is i18n so a multilingual site can send each language to its own page; `page` is shared, the page being the
same node in every language. The constraint on `url` refuses, as the contributor saves it, a value that does not
start with `http://` or `https://` (`www.example.com`, `/sites/mysite/home.html`), with the message
`fmdbmix_redirectToUrl.url.constraint.error.message`; the full rule is the action's.

## Server

1. `RedirectFormAction` (engine, `actions/form/redirect`) is the `ContextualFormAction` of `fmdb:redirectAction`.
2. Resolution:
   - `page`: the `page` node read in **live**, in the submission's locale (the action node came from that
     session); its URL is `node.getUrl()` passed through the outbound URL rewriting, the servlet's
     `<context>/modules` context path swapped for the platform's (`PageLinks`). The rewriting calls
     `response.encodeURL`, which adds `;jsessionid=...` for a visitor with no session cookie yet, since the platform's
     `web.xml` leaves URL session tracking on: `PageLinks` hands it a response whose `encodeURL` leaves the URL as it
     is. An absolute URL of the server the request reached is reduced to its path and returned with `redirectTo(path)`;
     a page of a site with another server name keeps its absolute URL, returned with `redirectToUrl(url)`. An
     unpublished or deleted page does not resolve: no redirect, a warning on one line, naming the action.
   - `url`: the value in the submission's locale, else in the site's default language, else in the first language
     that has one, returned with `redirectToUrl(url)` when `RedirectTargets.httpUrl` accepts it; anything else is no
     redirect, a warning.
3. The pipeline and the servlet do the rest, as for any
   [contextual action](form-submission-flow.md#contextual-actions-the-response-a-redirect-a-message-for-the-visitor):
   each kind of target is checked by its own rule, the first redirect of the list wins, and the servlet writes it as
   `redirect`.

## Client

The island needs nothing of its own for the action: it navigates to `redirect` once it checked the target again,
without the success message, and dispatches `formidable:submitted` first, which the jExperience integration sends
its submission event from ([contextual actions](form-submission-flow.md#contextual-actions-the-response-a-redirect-a-message-for-the-visitor)).
The jExperience script hands the promise of the tracker's request to the event's `waitUntil`, and the island holds the
navigation until that promise settles, for 1 second at most, so the form event reaches jCustomer before the page
unloads. No redirect in edit mode or preview, where submission is already disabled.

## Authoring

The `hidden.authoring` view of `fmdb:redirectAction` (`formidable-elements`, `components/RedirectAction`) draws the
card every action gets, with the target as its key detail (`redirectTargetDetail`: the page's displayable name, or
the URL in the current language), and, when an earlier sibling of the list is a redirect too
(`followsAnotherRedirect`), a warning under the description: the card carries
`data-fmdb-authoring-state="overridden-redirect"`. The warning says the earlier redirect comes first, not that this
one is ignored: it applies when no earlier redirect has a usable target. A URL the redirect drops
(`hasUnusableUrl`, the rule of the browser's `isHttpUrl`) carries its own warning instead, marked
`data-fmdb-authoring-state="unusable-redirect-url"`. An unpublished or deleted page carries none.

## Import from Jahia Forms

The [import](forms-import.md) converts `fcnt:redirectToAPageAction` and `fcnt:redirectToUrlAction` into a
`fmdb:redirectAction`, as that document details.

## Tests

- Unit (engine): `RedirectFormActionTest` (each target and the kind of redirect it becomes, the language fallback,
  a page that no longer resolves), `PageLinksTest` (no session id in the URL), `RedirectTargetsTest` (the path of a
  URL of this server, the context path swap, beside the two rules).
- Unit (elements): `redirectAction.server.test.ts` (the card's detail and its two warnings).
- Cypress: `tests/cypress/e2e/actions/89-redirect-action.cy.ts` (a form redirecting to a path, to another website,
  to a page, live, with no submission message before the next page and no submitted value in its URL; the constraint
  refusing an address without a scheme; unusable targets answering without a redirect; the first usable redirect
  winning; the two warnings of the actions zone).
