# CAPTCHA Server-side Validation

The `fmdbmix:captcha` mixin handles the **client-side** widget rendering and token collection only.
Server-side token validation is **your responsibility** and is **mandatory** — without it, your form endpoint is not protected.

## How it works

When a user completes the CAPTCHA challenge, the widget produces a short-lived token (valid for 300 seconds, single-use).
This token is submitted with the form data under the provider's native field name:

| Provider         | Field name               |
|------------------|--------------------------|
| Cloudflare Turnstile | `cf-turnstile-response` |
| hCaptcha         | `h-captcha-response`     |
| Google reCAPTCHA v2 | `g-recaptcha-response` |

Your backend must validate this token against the provider's verification endpoint before processing the form.

## Configuration

The `fmdb:captchaProvider` node stores two public values:

| Property    | Description                          |
|-------------|--------------------------------------|
| `siteKey`   | Public key used by the front-end widget |
| `scriptUrl` | Provider JS API URL                  |

The **secret key** must **never** be stored in JCR. Configure it at the server level:

```bash
# Environment variable (recommended)
TURNSTILE_SECRET_KEY=your_secret_key_here

# Or in jahia.properties / OSGi config
formidable.captcha.secretKey=your_secret_key_here
```

## Cloudflare Turnstile

### Verification endpoint

```
POST https://challenges.cloudflare.com/turnstile/v0/siteverify
Content-Type: application/x-www-form-urlencoded

secret=<SECRET_KEY>&response=<TOKEN>[&remoteip=<CLIENT_IP>]
```

### Response

```json
{
  "success": true,
  "challenge_ts": "2024-01-01T00:00:00Z",
  "hostname": "example.com",
  "error-codes": []
}
```

Reject the request if `success` is not `true`.

### Java example (Jahia Action)

```java
@Override
public Action.ActionResult doExecute(HttpServletRequest request, RenderContext renderContext,
        Resource resource, JCRSessionWrapper session, Map<String, List<String>> parameters,
        URLResolver urlResolver) throws Exception {

    String token = request.getParameter("cf-turnstile-response");
    if (token == null || token.isBlank()) {
        return Action.ActionResult.BAD_REQUEST;
    }

    String secret = System.getenv("TURNSTILE_SECRET_KEY");
    String remoteIp = Optional.ofNullable(request.getHeader("CF-Connecting-IP"))
            .orElse(request.getRemoteAddr());

    String payload = "secret=" + URLEncoder.encode(secret, StandardCharsets.UTF_8)
            + "&response=" + URLEncoder.encode(token, StandardCharsets.UTF_8)
            + "&remoteip=" + URLEncoder.encode(remoteIp, StandardCharsets.UTF_8);

    HttpClient client = HttpClient.newHttpClient();
    HttpRequest verifyRequest = HttpRequest.newBuilder()
            .uri(URI.create("https://challenges.cloudflare.com/turnstile/v0/siteverify"))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(payload))
            .build();

    HttpResponse<String> verifyResponse = client.send(verifyRequest, HttpResponse.BodyHandlers.ofString());

    JsonNode json = new ObjectMapper().readTree(verifyResponse.body());
    if (!json.path("success").asBoolean(false)) {
        return Action.ActionResult.BAD_REQUEST;
    }

    // Token is valid — process the form here
    return Action.ActionResult.OK;
}
```

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
        method: 'POST',
        body,
    }).then(r => r.json());

    if (!result.success) {
        return res.status(400).json({error: 'captcha_failed', codes: result['error-codes']});
    }

    // Token is valid — process the form here
});
```

## Other providers

| Provider         | Verification endpoint                                      | Token field (standard) |
|------------------|------------------------------------------------------------|------------------------|
| hCaptcha         | `https://hcaptcha.com/siteverify`                          | `h-captcha-response`   |
| Google reCAPTCHA v2 | `https://www.google.com/recaptcha/api/siteverify`       | `g-recaptcha-response` |

All three share the same request shape (`secret` + `response`) and the same `success` boolean in the response.
Formidable always sends the token under `fmdb-captcha-token` regardless of the provider.

## Error codes (Turnstile)

| Code                    | Meaning                                      |
|-------------------------|----------------------------------------------|
| `missing-input-secret`  | Secret key not provided                      |
| `invalid-input-secret`  | Secret key is invalid                        |
| `missing-input-response`| Token not provided                           |
| `invalid-input-response`| Token is malformed or expired                |
| `timeout-or-duplicate`  | Token already used or expired (300s limit)   |

On `timeout-or-duplicate`, the client widget should be reset. The front-end handles this automatically via the `expired-callback`, but you may also want to return a specific error code so the client can call `turnstile.reset()`.

