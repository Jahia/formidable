# Field actions: providers and limits

A field action checks one field's value server-side, while the visitor fills the form and again at submission
([Field actions](../architecture/field-actions.md)). What an administrator sets is where the checks may call
out, and how much the pre-check endpoint may be asked. Everything is in `org.jahia.modules.formidable.cfg`
(or the Felix Web Console); no JCR node is needed. A change reaches the checks immediately.

## Configuration

| Property | Description |
|---|---|
| `fieldActionProviders` | The external services a field action may call, one per line: `id|Label|https://base-url|Credential-name|credential`, with an optional sixth part saying where the credential goes — `header` (the default, a request header of that name) or `query` (a query parameter of that name, for a provider that reads its key off the URL). The id is what a contributor picks on the action; the URL and the credential never leave this file. HTTPS only. Empty by default: no check calls out |
| `enableDevFieldActionProviders` | `true` accepts the list below. `false` by default; never in production |
| `devFieldActionProviders` | Development-only providers, in the same form, over plain HTTP on `localhost` or `host.docker.internal` — a double of a provider, such as the samples module's. Ignored unless the switch above is on. A development id never shadows a standard one |
| `fieldActionHttpConnectTimeoutSeconds` | Time to establish the connection to a provider. Default 5 |
| `fieldActionHttpRequestTimeoutSeconds` | Total time for one call. A slower provider is an unavailable check, which the contributor's **If the check cannot run** setting decides. Default 10 |
| `fieldActionVerdictCacheTtlSeconds` | How long a verdict on one value is kept, per action, language and value, so that the check run while the visitor typed costs no second call at submission. `0` disables the cache. Default 300 |
| `fieldActionRateLimitPerMinute` | Pre-checks accepted per client address and minute on the endpoint (`FMDB-016` past it). `0` switches the endpoint off: the checks then run at submission only. Default 30 |
| `fieldActionMaxValueLength` | Longest value the endpoint accepts (`FMDB-003` above). Default 512 |
| `fieldActionMaxValuesPerField` | Distinct answers of one field the submission judges — each may cost a call — before refusing the submission (`FMDB-017`). Default 50 |

Two provider lines, as the samples expect them:

```
fieldActionProviders=experian|Experian Email Validation|https://api.experianaperture.io|Auth-Token|<token>\n\
  zerobounce|ZeroBounce|https://api.zerobounce.net|api_key|<key>|query
```

The log line `FormidableConfigService field actions: N provider(s), …` says how many lines were accepted; a
refused line is logged with its id and the reason, never its credential.

## What runs behind the endpoint

The pre-check endpoint, `/modules/formidable-engine/field-action`, is open to the site's own pages only (the
security-filter scope `formidable-field-action`, auto-applied to hosted origins; the engine ships the CSRF
whitelist line it needs) and answers a verdict, never a stack trace. What it may cost a provider is bounded by
the rate limit, the value length, the verdict cache and, at submission, the cap on distinct values. A
captcha-protected form does not re-check the captcha on the pre-check: the rate limit is the bound there, or
`0` to keep the checks at submission.

## Trying a provider without an account

The samples module (`formidable-test-module-samples-java`, installed by the test provisioning only)
registers a double of each provider it has a check for, at `/modules/formidable-samples/experian-stub` and
`/modules/formidable-samples/zerobounce-stub`: the same operation, the same credential (`stub-token`), the
same JSON, the verdict decided by the address (`ada@undeliverable.test`, `ada@invalid.test`,
`info@example.test`…, see the design page). Declared as development providers:

```
enableDevFieldActionProviders=true
devFieldActionProviders=experian-stub|Experian (stub)|http://localhost:8080/modules/formidable-samples/experian-stub|Auth-Token|stub-token\n\
  zerobounce-stub|ZeroBounce (stub)|http://localhost:8080/modules/formidable-samples/zerobounce-stub|api_key|stub-token|query
```

Then, on an email field of a published form, switch **Enable field actions** on, add **Email mailbox check
(ZeroBounce)** with the provider **ZeroBounce (stub)**, and type the addresses above on the live page.

With a real account, ZeroBounce's sandbox addresses (`valid@example.com`, `invalid@example.com`,
`disposable@example.com`, `role_based@example.com`, `catch_all@example.com`, `unknown@example.com`) answer
without spending a credit; Experian offers a trial in Australia, Canada, New Zealand and the United States.
