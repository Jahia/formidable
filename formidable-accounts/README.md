# formidable-accounts

Two actions that let a site visitor **create an account** and **sign in** from a [Formidable](../README.md) form.
Jahia 8.2.3.0 or later (the sign-in uses `AuthenticationService`, an OSGi service from that release).

| Action | Node type | What it does |
|---|---|---|
| Create account | `fmdbacc:createAccountAction` | Creates the user from the e-mail and the password (hashed by Jahia), optionally adds it to a group, signs it in, sends it to a page, or e-mails an activation link first |
| Sign in | `fmdbacc:signInAction` | A real Jahia login: session, login event (jExperience merges the anonymous profile into the user's), redirect to a page or back to where the visitor was going |

The password lives in a `fmdbext:password` field of `formidable-extended-inputs`, a **sensitive field**: the engine
hands its value to these two actions only. Save to JCR, the e-mail actions, forward, the response enrichers
(jExperience included) and the field actions never receive it.

**Full guide for authors and administrators: [how-to-accounts-actions](../docs/administration/how-to-accounts-actions.md).**

## Security in one paragraph

CAPTCHA required on the form (the operator can switch it off on a development box only); attempts counted per remote
address and per account, in memory; one sentence for every kind of failure, so nothing tells which accounts exist;
the password never stored, mailed, logged or returned; activation tokens random, hashed at rest, single use and
expiring and built only on the configured `publicBaseUrl`; with activation, a taken address reads "check your mailbox" like a
new one; redirects limited to a path of the same site; groups limited to the administrator's `allowedGroups` and refused
when they give back-office access; back-office accounts never sign in through a form (MFA and SSO stay on the platform's
login); an action runs only when last saved by someone holding `formidableAccountsActions` (site administrators); failed
sign-ins counted per account and address, so nobody locks someone else out.

## Configuration

`org.jahia.modules.formidable.accounts.cfg` (shipped with its defaults, kept as edited):

| Key | Default | Meaning |
|---|---|---|
| `requireCaptcha` | `true` | Refuse the actions on a form without the CAPTCHA option |
| `maxAttemptsPerAddress` / `addressWindowSeconds` | `20` / `600` | Submissions per remote address in the window |
| `maxAttemptsPerAccount` / `accountWindowSeconds` | `5` / `900` | Attempts per account typed (sign-ups; failed sign-ins per account and address) |
| `maxTrackedKeys` | `10000` | Bound on what the limiter remembers |
| `allowedGroups` | empty | Groups a Create account action may add a visitor to (global, or of the form's site); empty: none |
| `publicBaseUrl` | empty | Scheme and host of the activation link; required for activation, never taken from the request |
| `mailFrom` | empty | From of the activation e-mail; empty: Jahia's default sender |

## Build and deploy

```bash
# from the repository root, JDK 17
mvn -pl formidable-engine,formidable-accounts -am install -Pdev-unsigned   # -Pdev-unsigned only for a local Jahia Enterprise
```

Deploy in this order: the engine jar, then `formidable-extended-inputs` (for the password field), then
`formidable-accounts`; enable `formidable-accounts` on the site. The module declares `Jahia-Depends:
default,formidable-engine=0.6` and requires Jahia 8.2.3.0.
