# Field actions: services and limits

A field action checks one field's value server-side, while the visitor fills the form and again at submission
([Field actions](../architecture/field-actions.md); a developer writes one with [How to create a field action](../extension/how-to-create-field-action.md)).
What an administrator sets is in two places: the service a check calls, in the configuration of the module that
ships the check, and how every check calls and how much the pre-check endpoint may be asked, in
`org.jahia.modules.formidable.fieldActions.cfg`, one of the five [configuration files](configuration.md). No JCR node
is needed; a change reaches the checks immediately.

## The service a check calls

A check that asks an external service — a mailbox check, a CRM lookup — reads where the service is and its
credential from the configuration of its own module: one `karaf/etc/<the module's PID>.cfg`, which the module
ships with its defaults and Jahia copies there at its first start. Nothing to pick for a contributor: the check
knows its service. The module's documentation names the file and its settings; the samples' checks take three:

| Setting | Description |
|---|---|
| `url` | The service's base URL, without a query string (a key the service reads off the URL is the credential). HTTPS only — plain HTTP on `localhost` or `host.docker.internal` only with `development=true` |
| `.credential` | The secret the service gave you — the key starts with a dot, which keeps it off the list of OSGi services where the other settings are visible. Never logged, never shown to a contributor. Empty: the check does not run — an unavailable check, which the contributor's **If the check cannot run** setting decides — and the log says so once |
| `development` | `true` for a double of the service on this machine, such as the samples module's — called only while `enableDevFieldActionEndpoints` is on (below). `false` by default; never in production |

For the samples' Experian check, `karaf/etc/org.jahia.test.modules.formidable.samples.experian.cfg`:

```properties
url=https://api.experianaperture.io
.credential=<your Experian token>
development=false
```

The ZeroBounce one reads `org.jahia.test.modules.formidable.samples.zerobounce.cfg` the same way, its key sent on
the URL as ZeroBounce expects. A setting that describes no usable service — a plain-HTTP URL outside
development, a malformed one — is logged once with the service's name and the reason, never the credential.

## Limits

| Property | Description |
|---|---|
| `enableDevFieldActionEndpoints` | `true` lets a check whose own file says `development=true` call its double over plain HTTP on `localhost` or `host.docker.internal`, as `enableDevForwardTargets` does for the forward targets. `false` by default; never in production |
| `fieldActionHttpConnectTimeoutSeconds` | Time to establish the connection to the service. Default 5 |
| `fieldActionHttpRequestTimeoutSeconds` | Total time for one call. A slower service is an unavailable check, which the contributor's **If the check cannot run** setting decides. Default 10 |
| `fieldActionVerdictCacheTtlSeconds` | How long a verdict on one value is kept, per action, language and value, so that the check run while the visitor typed costs no second call at submission. `0` disables the cache. Default 300 |
| `fieldActionRateLimitPerMinute` | Pre-checks accepted per client address and minute on the endpoint (`FMDB-016` past it). `0` switches the endpoint off: the checks then run at submission only. Default 30 |
| `fieldActionMaxValueLength` | Longest value the endpoint accepts (`FMDB-003` above). Default 512 |
| `fieldActionMaxValuesPerField` | Distinct answers of one field the submission judges — each may cost a call — before refusing the submission (`FMDB-017`). Default 50 |

Earlier 0.5 snapshot builds listed the services in this file (`fieldActionProviders`, `enableDevFieldActionProviders`,
`devFieldActionProviders`); those settings are no longer read, and a warning names those that still hold a value.

## What runs behind the endpoint

The pre-check endpoint, `/modules/formidable-engine/field-action`, is open to the site's own pages only (the
security-filter scope `formidable-field-action`, auto-applied to hosted origins; the engine ships the CSRF
whitelist line it needs) and answers a verdict, never a stack trace. What it may cost a service is bounded by
the rate limit, the value length, the verdict cache and, at submission, the cap on distinct values. A
captcha-protected form does not re-check the captcha on the pre-check: the rate limit is the bound there, or
`0` to keep the checks at submission.

## Trying a check without an account

The samples module (`formidable-test-module-samples-java`, installed by the test provisioning only)
registers a double of each service it has a check for, at `/modules/formidable-samples/experian-stub` and
`/modules/formidable-samples/zerobounce-stub`: the same operation, the same credential (`stub-token`), the
same JSON, the verdict decided by the address (`ada@undeliverable.test`, `ada@invalid.test`,
`info@example.test`…, see the design page). Its two configuration files point the checks at those doubles
(`url=http://localhost:8080/modules/formidable-samples/…-stub`, `.credential=stub-token`, `development=true`);
switch `enableDevFieldActionEndpoints=true` on in `org.jahia.modules.formidable.fieldActions.cfg` for them to be
called.

On an email field of a published form, switch **Enable field actions** on, add **Email mailbox check
(ZeroBounce)**, and type the addresses above on the live page.

With a real account, set the real URL, your `.credential` and `development=false` in the check's file. ZeroBounce's
sandbox addresses (`valid@example.com`, `invalid@example.com`, `disposable@example.com`, `role_based@example.com`,
`catch_all@example.com`, `unknown@example.com`) answer without spending a credit; Experian offers a trial in
Australia, Canada, New Zealand and the United States.
