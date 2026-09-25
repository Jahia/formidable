package org.jahia.test.modules.formidable.samples.actions.field;

import org.jahia.modules.formidable.engine.api.FieldAction;
import org.jahia.modules.formidable.engine.api.FieldActionGateway;
import org.jahia.modules.formidable.engine.api.FieldActionRequest;
import org.jahia.modules.formidable.engine.api.FieldActionResult;
import org.jahia.services.content.JCRNodeWrapper;
import org.json.JSONException;
import org.json.JSONObject;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;
import java.io.IOException;
import java.util.Locale;
import java.util.Set;

/**
 * An example implementation of a check behind a provider: <strong>does the mailbox exist?</strong> — asked of
 * Experian Email Validation v2, the service a project names once the domain check of
 * {@link EmailDeliverabilityFieldAction} is not enough. The address is posted to the provider the contributor
 * picked on the node ({@code providerId}, one of the ids declared under {@code fieldActionProviders}), and the
 * provider's confidence becomes the verdict:
 * <ul>
 *   <li>{@code verified} — the mailbox exists, is reachable and receives mail: accepted;</li>
 *   <li>{@code undeliverable}, {@code unreachable}, {@code illegitimate}, {@code disposable} — the band Experian
 *   documents as "reject": refused, with the contributor's message under the field;</li>
 *   <li>anything else — {@code unknown}, a timeout, an accept-all domain, a rate-limited answer — is Experian
 *   saying it could not conclude, and this action does not conclude in its place: an unavailable check, which the
 *   contributor's {@code whenUnavailable} setting decides.</li>
 * </ul>
 * A provider that cannot be reached, refuses the token, is out of credits, times out or answers something other
 * than its documented JSON is an unavailable check too, never a refusal: an outage or a mistyped token does not
 * turn visitors away on its own.
 *
 * <p><strong>An example, not a supported connector.</strong> The samples module ships it and reaches no product
 * installation; a project copies it into a module of its own, with an Experian account and one provider line in
 * the engine's configuration — {@code fieldActionProviders=experian|Experian Email Validation|https://api.experianaperture.io|Auth-Token|<token>}.
 * The token stays in that file: this class never sees it, the gateway injects the header.</p>
 *
 * <p>What leaves the server is the <em>address</em>, which is what the provider judges — a project owes its
 * visitors a word about it. A value that is not an address at all is accepted without a call: the shape of a value
 * is the field's own validation, at step 9, and nothing is spent on it. Every answer is chargeable: the trigger
 * {@code submit} keeps it to one call per submission attempt, and the engine's verdict cache makes the pipeline's
 * own re-check free for a value the browser already asked about.</p>
 *
 * @see <a href="https://docs.experianaperture.io/email-validation/experian-email-validation-v2">Experian Email Validation v2</a>
 */
@Component(service = FieldAction.class)
public class ExperianEmailFieldAction implements FieldAction {

    public static final String NODE_TYPE = "fmdbsample:experianEmailAction";
    static final String PROVIDER_ID = "providerId";
    /** The v2 validation operation, under the provider's base URL {@code https://api.experianaperture.io}. */
    static final String VALIDATE_OPERATION = "email/validate/v2";
    /** The one confidence that says the mailbox exists. */
    static final String VERIFIED = "verified";
    /** The confidences Experian documents as "reject". */
    static final Set<String> REFUSED = Set.of("undeliverable", "unreachable", "illegitimate", "disposable");

    private static final Logger log = LoggerFactory.getLogger(ExperianEmailFieldAction.class);

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
    public FieldActionResult execute(JCRNodeWrapper actionNode, FieldActionRequest request) {
        return judge(providerIdOf(actionNode), request);
    }

    /**
     * The verdict on a value once the provider is known — the whole of the action but the reading of the node, so
     * that its rules are tested without a repository; the id is null when the node names no provider.
     */
    FieldActionResult judge(String providerId, FieldActionRequest request) {
        if (EmailDeliverabilityFieldAction.domainOf(request.value()) == null) {
            // Not an address: the field's own validation judges the shape, and no call is spent on it.
            return FieldActionResult.accept();
        }
        if (providerId == null) {
            return FieldActionResult.unavailable("no provider is set on the action node");
        }
        FieldActionGateway.Response response;
        try {
            response = gateway.post(providerId, VALIDATE_OPERATION, new JSONObject().put("email", request.value().trim()).toString());
        } catch (IOException | IllegalArgumentException e) {
            // Unreachable, timed out, or a provider id the configuration no longer declares: not a verdict.
            log.info("Formidable could not ask the email provider '{}' about field '{}': {}", providerId, request.fieldName(), e.getMessage());
            return FieldActionResult.unavailable("the provider did not answer: " + e.getClass().getSimpleName());
        }
        if (response.status() != 200) {
            // 401 a refused token, 403 no credits left, 408 the provider's own timeout, 429 its rate limit, 5xx an outage
            return FieldActionResult.unavailable("the provider answered " + response.status());
        }
        String confidence = confidenceOf(response.body());
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

    /** The provider id the contributor picked on the node; null when the node lacks it or cannot be read. */
    static String providerIdOf(JCRNodeWrapper actionNode) {
        try {
            if (actionNode == null || !actionNode.hasProperty(PROVIDER_ID)) {
                return null;
            }
            String providerId = actionNode.getProperty(PROVIDER_ID).getString().trim();
            return providerId.isEmpty() ? null : providerId;
        } catch (RepositoryException e) {
            return null;
        }
    }

    /** The {@code result.confidence} of a v2 answer; null when the body is not that JSON or carries none. */
    static String confidenceOf(String body) {
        if (body == null) {
            return null;
        }
        try {
            JSONObject result = new JSONObject(body).optJSONObject("result");
            String confidence = result == null ? null : result.optString("confidence", "").trim();
            return confidence == null || confidence.isEmpty() ? null : confidence;
        } catch (JSONException e) {
            return null;
        }
    }
}
