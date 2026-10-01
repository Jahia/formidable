package org.jahia.test.modules.formidable.samples.actions.field;

import org.jahia.modules.formidable.engine.api.EmailVerificationFieldAction;
import org.jahia.modules.formidable.engine.api.FieldAction;
import org.jahia.modules.formidable.engine.api.FieldActionGateway;
import org.jahia.modules.formidable.engine.api.FieldActionResult;
import org.json.JSONObject;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.AttributeType;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

import java.io.IOException;
import java.util.Locale;
import java.util.Set;

/**
 * An example implementation of a mailbox check against an external service — Experian Email Validation v2 — on the
 * engine's {@link EmailVerificationFieldAction}: what is left to write is where the service is, the call and the
 * service's own vocabulary turned into the three verdicts. The address is posted to the service this class's own
 * configuration names ({@value #PID}), and the service's confidence becomes the verdict:
 * <ul>
 *   <li>{@code verified} — the mailbox exists, is reachable and receives mail: accepted;</li>
 *   <li>{@code undeliverable}, {@code unreachable}, {@code illegitimate}, {@code disposable} — the band Experian
 *   documents as "reject": refused, with the contributor's message under the field;</li>
 *   <li>anything else — {@code unknown}, a timeout, an accept-all domain, a rate-limited answer — is Experian
 *   saying it could not conclude, and this action does not conclude in its place: an unavailable check, which the
 *   contributor's {@code whenUnavailable} setting decides.</li>
 * </ul>
 * A service that refuses the token, is out of credits, times out or answers something other than its documented
 * JSON is an unavailable check too, never a refusal; one that cannot be reached is turned into the same by the
 * engine's base class.
 *
 * <p><strong>An example, not a supported connector.</strong> The samples module ships it and reaches no product
 * installation; a project copies it into a module of its own, with an Experian account, and gives the module's
 * configuration file its PID and the account's token: {@code url=https://api.experianaperture.io},
 * {@code .credential=<token>}. The samples' own file points at their double of the service instead
 * ({@link ExperianStubServlet}). The token goes to the gateway in the endpoint, which injects the header and never
 * logs it. What leaves the server is the <em>address</em>, which is what the service judges — a project owes its
 * visitors a word about it.</p>
 *
 * @see <a href="https://docs.experianaperture.io/email-validation/experian-email-validation-v2">Experian Email Validation v2</a>
 */
@Component(service = FieldAction.class, configurationPid = ExperianEmailFieldAction.PID)
@Designate(ocd = ExperianEmailFieldAction.Config.class)
public class ExperianEmailFieldAction extends EmailVerificationFieldAction {

    public static final String NODE_TYPE = "fmdbsample:experianEmailAction";
    /** This action's own configuration: where Experian is, and the account's token. */
    public static final String PID = "org.jahia.test.modules.formidable.samples.experian";
    /** The header Experian reads the token from: the service's contract, not a setting. */
    static final String TOKEN_HEADER = "Auth-Token";
    /** The v2 validation operation, under the service's base URL {@code https://api.experianaperture.io}. */
    static final String VALIDATE_OPERATION = "email/validate/v2";
    /** The one confidence that says the mailbox exists. */
    static final String VERIFIED = "verified";
    /** The confidences Experian documents as "reject". */
    static final Set<String> REFUSED = Set.of("undeliverable", "unreachable", "illegitimate", "disposable");

    /**
     * This action's configuration, named like the engine's own entries — Formidable, the theme, the check as the contributor
     * sees it — so the configuration manager lists every field action together, one row per check, told apart by the
     * service. The wording of the three settings is {@link SampleEndpointSettings}.
     */
    @ObjectClassDefinition(name = "Formidable — Field actions — Email mailbox check (Experian)", description = "Where the Experian check calls Experian Email Validation, and the account's token.")
    public @interface Config {

        @AttributeDefinition(name = SampleEndpointSettings.URL_NAME, description = SampleEndpointSettings.URL_DESCRIPTION)
        String url() default "";

        /** The file's {@code .credential}: the leading dot keeps it off the service registry. */
        @AttributeDefinition(name = SampleEndpointSettings.CREDENTIAL_NAME, description = SampleEndpointSettings.CREDENTIAL_DESCRIPTION,
                type = AttributeType.PASSWORD)
        String _credential() default "";

        @AttributeDefinition(name = SampleEndpointSettings.DEVELOPMENT_NAME, description = SampleEndpointSettings.DEVELOPMENT_DESCRIPTION)
        boolean development() default false;
    }

    @Reference
    private FieldActionGateway gateway;

    public ExperianEmailFieldAction() {
    }

    ExperianEmailFieldAction(FieldActionGateway gateway, FieldActionGateway.Endpoint endpoint) {
        this.gateway = gateway;
        useEndpoint(endpoint);
    }

    @Activate
    @Modified
    public void activate(Config config) {
        configure("Experian", config.url(), config._credential(), config.development(), TOKEN_HEADER, FieldActionGateway.Endpoint.CREDENTIAL_IN_HEADER);
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
    protected FieldActionResult verify(FieldActionGateway.Endpoint experian, String address) throws IOException {
        return verdictOf(gateway().post(experian, VALIDATE_OPERATION, new JSONObject().put("email", address).toString()));
    }

    /** Experian's answer turned into the verdict: the status first, then the confidence of a v2 answer. */
    static FieldActionResult verdictOf(FieldActionGateway.Response response) {
        if (response.status() != 200) {
            // 401 a refused token, 403 no credits left, 408 the service's own timeout, 429 its rate limit, 5xx an outage
            return FieldActionResult.unavailable("the service answered " + response.status());
        }
        String confidence = confidenceOf(response);
        if (confidence == null) {
            return FieldActionResult.unavailable("the service's answer is not the documented JSON");
        }
        if (VERIFIED.equalsIgnoreCase(confidence)) {
            return FieldActionResult.accept();
        }
        if (REFUSED.contains(confidence.toLowerCase(Locale.ROOT))) {
            return FieldActionResult.reject("confidence " + confidence);
        }
        return FieldActionResult.unavailable("the service could not conclude: " + confidence);
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
