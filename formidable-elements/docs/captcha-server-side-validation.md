# CAPTCHA Server-side Validation

The captcha integration in Formidable covers both the front-end widget (rendering + token collection) and, depending on the form mode, server-side token verification.

## Configuration

Add a `fmdb:captchaAction` node anywhere in the site content tree and reference it from the form via the `captcha` property of `fmdbmix:actionPipeline`.

| Property | Where | Description |
|---|---|---|
| `siteKey` | `fmdb:captchaAction` | Public key — used by the front-end widget |
| `scriptUrl` | `fmdb:captchaAction` | Provider JS API URL — determines the provider |
| `secretKey` | `fmdb:captchaAction` | Secret key — used for server-side verification (JCR mode only) |

The provider (Turnstile / hCaptcha / reCAPTCHA v2) is derived automatically from `scriptUrl` at runtime — no explicit provider property needed.

---

## When is the token verified server-side?

| Form mode | Captcha widget | Jahia verifies token |
|---|---|---|
| **JCR mode** (`destination` = `save2jcr` or absent) | ✅ rendered | ✅ `CaptchaVerificationFormAction` calls the provider siteverify API |
| **Transfer mode** (`destination` = `sendData`) | ✅ rendered | ❌ never — the browser forwards the token to the third party with the full `FormData` |

The token is single-use. In transfer mode Jahia must not consume it; the destination endpoint is responsible for its own verification.

---

## How verification works (JCR mode)

`CaptchaVerificationFormAction.execute()` is called first in the pipeline, before `save2jcr` and any side effects. It:

1. Reads `secretKey` from the `fmdb:captchaAction` node
2. Derives the provider from `scriptUrl`
3. Reads the token from the POST parameters under the provider's native field name
4. POSTs to the provider's `siteverify` endpoint
5. Throws `FormActionException.badRequest()` if `success` is not `true` — stopping the pipeline

## Token field names (auto-injected by the widget)

| Provider | Field name in POST |
|---|---|
| Cloudflare Turnstile | `cf-turnstile-response` |
| hCaptcha | `h-captcha-response` |
| Google reCAPTCHA v2 | `g-recaptcha-response` |

## Verification endpoints

| Provider | Endpoint |
|---|---|
| Cloudflare Turnstile | `https://challenges.cloudflare.com/turnstile/v0/siteverify` |
| hCaptcha | `https://hcaptcha.com/siteverify` |
| Google reCAPTCHA v2 | `https://www.google.com/recaptcha/api/siteverify` |

All three share the same request shape (`secret` + `response` + optional `remoteip`) and the same `{ "success": boolean }` response.

---

## Transfer mode — third-party verification

In transfer mode the browser POSTs `FormData` (including the captcha token) directly to `targetUrl`. The third party must verify the token itself before processing the submission.

### Node.js / Express example

```js
app.post('/submit', async (req, res) => {
    const token = req.body['cf-turnstile-response'];
    if (!token) return res.status(400).json({error: 'missing_token'});

    const body = new URLSearchParams({
        secret: process.env.TURNSTILE_SECRET_KEY,
        response: token,
        remoteip: req.headers['cf-connecting-ip'] ?? req.ip,
    });

    const result = await fetch('https://challenges.cloudflare.com/turnstile/v0/siteverify', {
        method: 'POST', body,
    }).then(r => r.json());

    if (!result.success) {
        return res.status(400).json({error: 'captcha_failed', codes: result['error-codes']});
    }

    // Token is valid — process the form
});
```

Return a non-2xx status if verification fails — Formidable stops the submission and shows the error message to the user.

---

## Error codes (Turnstile)

| Code | Meaning |
|---|---|
| `missing-input-secret` | Secret key not provided |
| `invalid-input-secret` | Secret key is invalid |
| `missing-input-response` | Token not provided |
| `invalid-input-response` | Token is malformed or expired |
| `timeout-or-duplicate` | Token already used or expired (300 s limit) |

