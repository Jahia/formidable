package org.jahia.test.modules.formidable.samples.actions.field;

import org.jahia.modules.formidable.engine.api.EmailVerificationFieldAction;
import org.jahia.modules.formidable.engine.api.FieldAction;
import org.jahia.modules.formidable.engine.api.FieldActionGateway;
import org.jahia.modules.formidable.engine.api.FieldActionResult;
import org.json.JSONObject;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import java.io.IOException;
import java.util.Locale;
import java.util.Set;

/**
 * An example implementation of a mailbox check behind a provider — Experian Email Validation v2 — on the engine's
 * {@link EmailVerificationFieldAction}: what is left to write is the call and the provider's own vocabulary turned
 * into the three verdicts. The address is posted to the provider the contributor picked on the node, and the
 * provider's confidence becomes the verdict:
 * <ul>
 *   <li>{@code verified} — the mailbox exists, is reachable and receives mail: accepted;</li>
 *   <li>{@code undeliverable}, {@code unreachable}, {@code illegitimate}, {@code disposable} — the band Experian
 *   documents as "reject": refused, with the contributor's message under the field;</li>
 *   <li>anything else — {@code unknown}, a timeout, an accept-all domain, a rate-limited answer — is Experian
 *   saying it could not conclude, and this action does not conclude in its place: an unavailable check, which the
 *   contributor's {@code whenUnavailable} setting decides.</li>
 * </ul>
 * A provider that refuses the token, is out of credits, times out or answers something other than its documented
 * JSON is an unavailable check too, never a refusal; one that cannot be reached is turned into the same by the
 * engine's base class.
 *
 * <p><strong>An example, not a supported connector.</strong> The samples module ships it and reaches no product
 * installation; a project copies it into a module of its own, with an Experian account and one provider line in the
 * engine's configuration — {@code fieldActionProviders=experian|Experian Email Validation|https://api.experianaperture.io|Auth-Token|<token>}.
 * The token stays in that file: this class never sees it, the gateway injects the header. What leaves the server is
 * the <em>address</em>, which is what the provider judges — a project owes its visitors a word about it.</p>
 *
 * @see <a href="https://docs.experianaperture.io/email-validation/experian-email-validation-v2">Experian Email Validation v2</a>
 */
@Component(service = FieldAction.class)
public class ExperianEmailFieldAction extends EmailVerificationFieldAction {

    public static final String NODE_TYPE = "fmdbsample:experianEmailAction";
    /** The v2 validation operation, under the provider's base URL {@code https://api.experianaperture.io}. */
    static final String VALIDATE_OPERATION = "email/validate/v2";
    /** The one confidence that says the mailbox exists. */
    static final String VERIFIED = "verified";
    /** The confidences Experian documents as "reject". */
    static final Set<String> REFUSED = Set.of("undeliverable", "unreachable", "illegitimate", "disposable");

    @Reference
    private FieldActionGateway gateway;

    public ExperianEmailFieldAction() {
    }

    ExperianEmailFieldAction(FieldActionGateway gateway) {
        this.gateway = gateway;
    }

    @Override
    public String getNodeType() {
        return NODE_TYPE;
    }

    @Override
    protected FieldActionGateway gateway() {
        return gateway;
    }

    @Override
    protected FieldActionResult verify(String providerId, String address) throws IOException {
        FieldActionGateway.Response response = gateway().post(providerId, VALIDATE_OPERATION, new JSONObject().put("email", address).toString());
        if (response.status() != 200) {
            // 401 a refused token, 403 no credits left, 408 the provider's own timeout, 429 its rate limit, 5xx an outage
            return FieldActionResult.unavailable("the provider answered " + response.status());
        }
        String confidence = confidenceOf(response);
        if (confidence == null) {
            return FieldActionResult.unavailable("the provider's answer is not the documented JSON");
        }
        if (VERIFIED.equalsIgnoreCase(confidence)) {
            return FieldActionResult.accept();
        }
        if (REFUSED.contains(confidence.toLowerCase(Locale.ROOT))) {
            return FieldActionResult.reject("confidence " + confidence);
        }
        return FieldActionResult.unavailable("the provider could not conclude: " + confidence);
    }

    /** The {@code result.confidence} of a v2 answer; null when the body is not that JSON or carries none. */
    static String confidenceOf(FieldActionGateway.Response response) {
        return json(response)
                .map(body -> body.optJSONObject("result"))
                .map(result -> result.optString("confidence", "").trim())
                .filter(confidence -> !confidence.isEmpty())
                .orElse(null);
    }
}
