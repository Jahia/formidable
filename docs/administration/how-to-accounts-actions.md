# Create accounts and sign in from a form (formidable-accounts)

The `formidable-accounts` module adds two form actions: **Create account** (`fmdbacc:createAccountAction`) and
**Sign in** (`fmdbacc:signInAction`). A visitor registers, or signs in, from a Formidable form; the sign-in is a real Jahia
login, so the platform's login event fires and, with jExperience, the visitor's anonymous profile is merged into the user's.

Requirements: Jahia **8.2.3.0 or later** (the sign-in goes through `AuthenticationService`, an OSGi service from that
release), `formidable-engine` 0.6, and `formidable-extended-inputs` for the password field. Module reference:
[`formidable-accounts/README.md`](../../formidable-accounts/README.md).

## Contents

- [Before you start (administrator)](#before-you-start-administrator)
- [Build a sign-up form (author)](#build-a-sign-up-form-author)
- [Build a sign-in form (author)](#build-a-sign-in-form-author)
- [Where the visitor goes next](#where-the-visitor-goes-next)
- [E-mail activation](#e-mail-activation)
- [What the module protects, and what it does not](#what-the-module-protects-and-what-it-does-not)
- [Troubleshooting](#troubleshooting)

## Before you start (administrator)

1. Deploy, in this order, the engine, `formidable-extended-inputs`, then `formidable-accounts`; enable
   `formidable-accounts` and `formidable-extended-inputs` on the site.
2. **Configure a CAPTCHA** ([CAPTCHA server-side validation](captcha-server-side-validation.md)). Both actions refuse to run on a
   form without the CAPTCHA option: a public sign-up or sign-in form without one is an open door for bots. On a local
   development instance only, `requireCaptcha=false` in `org.jahia.modules.formidable.accounts.cfg` lifts the rule.
3. **Decide who configures the actions.** An action runs only when the person who **last saved it** holds the permission
   `formidableAccountsActions` on it. The module declares it under `site-admin`, so **site administrators** hold it; grant it
   to another role in Administration > Roles and permissions if others must set up sign-up forms. Any editor of the form can
   still add or change the action, but then it refuses every visitor (as "temporarily unavailable", logged) until someone who
   holds the permission saves it again.
4. Read `org.jahia.modules.formidable.accounts.cfg` (shipped with its defaults, kept as edited):

| Key | Default | Meaning |
|---|---|---|
| `requireCaptcha` | `true` | Refuse the actions on a form without the CAPTCHA option |
| `maxAttemptsPerAddress`, `addressWindowSeconds` | `20`, `600` | Submissions of these actions per remote address in the window, whatever their outcome |
| `maxAttemptsPerAccount`, `accountWindowSeconds` | `5`, `900` | Attempts per account (the e-mail or user name typed, existing or not): every sign-up attempt for an address; for a sign-in, the failures of that account **from one remote address**, so that a stranger's guesses never lock the owner out of the form; a successful sign-in clears the count |
| `maxTrackedKeys` | `10000` | Upper bound on what the limiter remembers; the least recently seen is forgotten first |
| `allowedGroups` | empty | Groups, separated by semicolons, that a Create account action may add a visitor to: the only choices of its **Group** field. A name is a global group, or else a group of the form's own site. Empty: no action adds anyone to a group |
| `publicBaseUrl` | empty | Scheme and host of the activation link (`https://www.example.com`). **Required for e-mail activation**: empty, or not an `http`/`https` origin, an action with activation refuses every sign-up. Never taken from the request |
| `mailFrom` | empty | From address of the activation e-mail; empty: the sender of Jahia's mail settings |

The limits are counted **in memory, per JVM**: a restart, or another node of a cluster, starts from zero. The remote
address is `request.getRemoteAddr()`: behind a proxy, configure the container so that it is the visitor's address, or every
visitor shares the proxy's limit.

## Build a sign-up form (author)

1. Create the form. Turn on its **CAPTCHA** option.
2. Add the fields: an **E-mail** field, a **Password** field (*Input Password*, from the extended inputs), and optionally text
   fields for the first and the last name. Say in the password field's help text what the password must contain.
3. Add a **Create account** action and fill it in:

| Setting | What it does |
|---|---|
| **E-mail field** | The field that holds the address. It is also the user name of the account (lower case, characters Jahia does not accept replaced by `_`, checked with the platform's own rule) |
| **Password field** | The password field. A plain text field is refused: its value would be stored and mailed like any other answer |
| **First name field**, **Last name field** | Optional; become the first and the last name of the account |
| **Group** | Optional. One of the groups the administrator listed in `allowedGroups` that exists globally or on this site. A group giving back-office access is refused at sign-up even when listed (see [below](#what-the-module-protects-and-what-it-does-not)) |
| **Sign the visitor in** | On by default: the visitor is signed in as soon as the account exists. Ignored when e-mail activation is required |
| **Page to go to afterwards**, **Return parameter** | See [Where the visitor goes next](#where-the-visitor-goes-next) |
| **Password rule** | Regular expression the whole password must match. Empty: at least one digit, one lower-case letter, one upper-case letter and one symbol, 8 to 64 characters. The password is capped at 256 characters whatever the rule |
| **Require e-mail activation** and its settings | See [E-mail activation](#e-mail-activation) |

4. **Publish** the form. Submissions run against the live workspace.

The action does **not** carry the read-only compatibility marker: it writes, so the form is unavailable during a read-only
maintenance window.

## Build a sign-in form (author)

Same as above with a field for the e-mail or the user name, a **Password** field (set **Browser password manager** to
*Current password*), the CAPTCHA, and a **Sign in** action: the login field, the password field, optionally the page to go to
and a **Return parameter**, and a **Message when the sign-in fails** if you want your own words (one message for every cause).

A visitor can type the e-mail in any case. `root` and `guest` can never be signed in through a public form, nor can any
**back-office account** (see below): staff sign in on the platform's own login page, where a second factor and SSO apply.

## Where the visitor goes next

Both actions can send the visitor somewhere once they succeed:

- **Page to go to afterwards**: a page of the site picked in the page picker. It follows the page when it is moved or renamed
  and must be published.
- **Return parameter**: the name of a query-string parameter of the form's own page, for the "send me back where I was going"
  pattern: with the parameter `redirect`, a visitor sent to `/login?redirect=/members` lands on `/members`. It applies when the
  visitor is signed in, ahead of the page above, and **only when its value is a path of this site** (a single leading slash, no
  scheme, no host, no backslash). Anything else is ignored: the visitor controls this parameter, so it is never trusted beyond
  that rule. The page is read from the `Referer` header, which must name the server the request reached.

The browser navigates once the success message was shown; see [Redirect action](../architecture/redirect-action.md).

## E-mail activation

Off by default. When **Require e-mail activation** is on, the account is created **locked** and the visitor receives a link:

0. `publicBaseUrl` must be set: the link is built on it, never on the host the request named (a visitor controls the `Host`
   header, and the token would be mailed to the address they chose with a link to their server).
1. A random token is generated (256 bits). Only its SHA-256 hash is stored, on the user, with an expiry (**Link validity**,
   48 hours by default, 720 at most). The account stays locked (`j:accountLocked`) until the link is followed.
2. The link is mailed with Jahia's mail service (its SMTP settings must be configured: the sign-up is refused, and nothing is
   created, when it is not). Subject and message come from the module in the visitor's language, or from the action's
   **Activation e-mail subject** and **message**, where `${link}` and `${hours}` are replaced. The module writes them, and every
   sentence the visitor reads, in English, French and European Portuguese (`pt`, for `pt_PT` too); another language
   reads English.
3. The link is `/modules/formidable-accounts/verify?t=...`. It unlocks the account, removes the token, and sends the visitor to
   the **Page after activation** (which receives `fmdbActivation=done`, a good place for the sign-in form), or shows a short
   confirmation. Every failure (unknown, used, expired, malformed) is one generic page.
4. The visitor is told to check the mailbox (the module's sentence, or **Message while awaiting activation**) and is **not**
   signed in; they sign in after activating.

If the e-mail cannot be sent, the account is removed so the visitor can try again. An account whose link expired does not hold
the address for ever: the next sign-up with it replaces it. A link is followed with GET, as every e-mail link is: a mail
scanner that opens it consumes the token, and the visitor then simply signs in with the password they chose.

## What the module protects, and what it does not

**Protected**

- **The password is never stored, mailed, forwarded, logged, echoed or sent to jExperience.** The password field is a *sensitive
  field* (`fmdbmix:sensitiveField`): the engine sets its value apart before any action runs and hands it only to an action that
  declares it consumes sensitive values: these two. So **Save to JCR, Email Form Content, Forward Data, the jExperience event
  and field actions never receive it**, whatever else is on the form: you may put Save to JCR next to Create account to keep
  the e-mail and the name, and the results will hold no password. See
  [Sensitive fields](../architecture/form-submission-flow.md#sensitive-fields).
- **No account enumeration with activation.** With e-mail activation on, a sign-up with an address that is already
  registered reads "check your mailbox", exactly like a new one, and nothing is mailed. A group problem or any other platform
  failure reads one generic sentence. A failed sign-in (unknown account, wrong password, invalid name, back-office account)
  reads the same sentence.
  The rate-limit sentence is the same for an account that exists and one that does not. The only distinct sentence, "your account
  is not active yet", is shown after the password was verified.
- **Abuse.** CAPTCHA required; per-address and per-account limits; the activation mailbox cannot be flooded (sign-ups are
  counted per address typed); failed sign-ins are counted per account and remote address, so nobody locks someone else out;
  an activation token is unguessable, hashed at rest, single use and expiring.
- **No CSRF whitelist.** The actions ride the form-submit endpoint, protected by the `formidable-submit` Security Filter scope
  (same origin) and the CAPTCHA. The verification endpoint only follows an unguessable single-use token.
- **No open redirect.** Every redirect is a path of the same site, validated on the server and again in the browser.
- **No privilege handed out from a public form.** The Group field offers only the administrator's `allowedGroups`, global or
  of the form's site, never another site's; at every sign-up the group is checked again and refused when it gives back-office
  access: a member, directly or through other groups, of `privileged`, `administrators`, a site's `site-privileged` (where Jahia
  puts whoever holds an editing role) or `site-administrators`, or holding a server role. A group granting a site role such as
  `reader` (members-only pages) is fine.
- **Who configures the actions.** Only an action last saved by someone holding `formidableAccountsActions` runs.
- **No second-factor bypass.** The Sign in action calls the platform's authentication service directly, like `/cms/login`,
  which the UPA (MFA) module asks to block at the proxy. It therefore refuses every back-office account (the test above, for a
  user): only site visitors sign in through a form.
- **No secret in messages.** A `${password}` (or any password control) in the form's success or error message prints nothing.

**Not protected, by design or by limit**

- **Without** e-mail activation, a sign-up form cannot hide that an address is taken: the visitor is signed in at once when it is
  not. Turn activation on for a public form where that matters. With activation, the time the answer takes can still differ (a
  new address waits for the mail server).
- The time the server takes can differ between an unknown account and a wrong password.
- The limits are per JVM and in memory (see above): a restart, or another node of a cluster, starts from zero, and behind a proxy
  the remote address must be the visitor's.
- The password travels in the submission request like any form: serve the site over HTTPS.

## Troubleshooting

| Symptom | Cause | Fix |
|---|---|---|
| The visitor reads "This form is temporarily unavailable" | The form has no CAPTCHA option, the site did not enable `formidable-accounts`, the action was last saved by someone without `formidableAccountsActions`, a field of the action is not chosen, the password field is not a *Password* input, or activation is on and Jahia's mail service is off or `publicBaseUrl` is not set | The server log says which (`[formidable-accounts]`), with the form's or the action's path |
| The **Group** list of the action is empty | `allowedGroups` is empty, or none of its names exists globally or on this site | Ask the administrator to list the group in `org.jahia.modules.formidable.accounts.cfg` |
| Sign-ups fail with the generic sentence and the log says `GROUP` | The action's group is no longer in `allowedGroups`, no longer exists, or now gives back-office access | Pick another group, or fix the configuration |
| A staff member cannot sign in through the form | Back-office accounts never sign in through a form | Use the platform's login page |
| The action is missing from the actions list | The module is not enabled on the site, or the jContent tab is stale | Enable it, reload the tab |
| The field lists of the action are empty | The action is not inside a form, or the form has no field of that kind (an e-mail field, a Password input) | Add the field, then reopen the action |
| `Too many attempts` for everybody | Behind a proxy every visitor shares one remote address | Configure the container so `getRemoteAddr()` is the visitor's, or raise `maxAttemptsPerAddress` |
| The module does not start | Jahia older than 8.2.3.0: `AuthenticationService` is not an OSGi service there | Upgrade Jahia |
| A visitor who just followed the activation link reads "not active yet" for a moment | Jahia publishes user changes to the live workspace in the background, and a sign-in reads the live copy first | Wait a few seconds and sign in again |
| The visitor is not signed in after the sign-up | The automatic sign-in was refused (logged as a warning with the account's identifier) or **Sign the visitor in** is off | Check the log; the visitor can sign in by hand |
