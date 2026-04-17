# Form Submission Flow

## Overview

Form submission is handled server-side by `FormSubmitAction`, a Jahia `Action` OSGi service.

The pipeline is driven by the child nodes of the `actions` node (`fmdb:actionList`) autocreated on every `fmdb:form`. Actions are executed in order, as configured by the contributor.

---

## Pipeline

```
Browser
  └─ POST FormData → /cms/render/live/{locale}/{formPath}.formidableSubmit.do
       Jahia pipeline (enforced order):
         1. CAPTCHA server-side verification (if fmdbmix:captcha mixin is present)
         2. Actions in order (fmdb:actionList child nodes)
       └─ { "success": true | "success": false, "message": "..." }
  └─ show success or error message
```

---

## CAPTCHA

| Condition | Behaviour |
|---|---|
| `fmdbmix:captcha` mixin present on the form | Token verified server-side before any action runs |
| `fmdbmix:captcha` mixin absent | No verification, pipeline runs immediately |

CAPTCHA configuration (`siteKey`, `scriptUrl`, `verifyUrl`, `secretKey`) is read from `org.jahia.modules.formidable.captcha.cfg` — not stored in JCR.

Token field names auto-injected by the widget:

| Provider | Field name |
|---|---|
| Cloudflare Turnstile | `cf-turnstile-response` |
| hCaptcha | `h-captcha-response` |
| Google reCAPTCHA v2 | `g-recaptcha-response` |

---

## Action nodes (`fmdb:actionList`)

Every `fmdb:form` has an autocreated `actions` child node of type `fmdb:actionList`. It contains a `save2jcr` node (`fmdb:save2jcrAction`) by default, which the contributor can delete.

The contributor adds action nodes inside `actions` via the Content Editor. Actions are executed in the order they appear in the list.

| Node type | Description |
|---|---|
| `fmdb:save2jcrAction` | Saves form data as JCR child nodes (default, autocreated) |
| `fmdb:emailAction` | Sends email via Jahia `MailService`; `${fieldName}` interpolation in subject and body |

To add a new action type, see `AGENTS.md` → *Form action pipeline*.

---

## Key files

| File | Role |
|---|---|
| `src/components/Form/Form.client.tsx` | `handleSubmit` — POST to `submitActionUrl` |
| `src/components/Form/default.server.tsx` | Computes `submitActionUrl`; reads `fmdbmix:captcha` for widget config |
| `formidable-engine/.../FormSubmitAction.java` | Jahia `Action` OSGi — runs CAPTCHA then iterates `actions` child nodes |
| `formidable-engine/.../FormAction.java` | Interface implemented by each action type |
| `formidable-engine/.../CaptchaConfigService.java` | Reads captcha cfg and verifies token against provider siteverify API |
