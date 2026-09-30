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
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

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
 * {@code credential=<token>}. The samples' own file points at their double of the service instead
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

    /** Where the check calls: the base URL and the account's token, from {@value #PID}. */
    @ObjectClassDefinition(name = "Formidable samples — Experian email check",
            description = "Where the samples' Experian mailbox check calls, and the account's token.")
    public @interface Config {

        @AttributeDefinition(name = "URL", description = "The base URL of Experian Email Validation: https://api.experianaperture.io.")
        String url() default "https://api.experianaperture.io";

        @AttributeDefinition(name = "Token", description = "The Experian account's token. Empty: the check is unavailable.",
                type = AttributeType.PASSWORD)
        String credential() default "";

        @AttributeDefinition(name = "Development double", description = "The URL is a double of the service on this "
                + "machine, over plain HTTP on localhost or host.docker.internal (the samples' stub). Never in production.")
        boolean development() default false;
    }

    @Reference
    private FieldActionGateway gateway;

    private final AtomicReference<Optional<FieldActionGateway.Endpoint>> endpoint = new AtomicReference<>(Optional.empty());

    public ExperianEmailFieldAction() {
    }

    ExperianEmailFieldAction(FieldActionGateway gateway, FieldActionGateway.Endpoint endpoint) {
        this.gateway = gateway;
        this.endpoint.set(Optional.ofNullable(endpoint));
    }

    @Activate
    @Modified
    public void configure(Config config) {
        endpoint.set(endpointOf("Experian", config.url(), TOKEN_HEADER, config.credential(),
                FieldActionGateway.Endpoint.CREDENTIAL_IN_HEADER, config.development()));
    }

    @Override
    protected Optional<FieldActionGateway.Endpoint> endpoint() {
        return endpoint.get();
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
        FieldActionGateway.Response response = gateway().post(experian, VALIDATE_OPERATION, new JSONObject().put("email", address).toString());
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
