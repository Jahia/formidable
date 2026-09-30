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
import org.osgi.service.metatype.annotations.Designate;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;

/**
 * The second example implementation of a mailbox check against an external service — ZeroBounce's email validation
 * v2 — on the engine's {@link EmailVerificationFieldAction}, beside the Experian one: the same skeleton, another
 * vocabulary, its own configuration ({@value #PID}). ZeroBounce reads its key off the URL, so the endpoint puts the
 * credential in the query, as {@code api_key}; the address goes as a query parameter too, as the service documents
 * its GET. The service's {@code status} becomes the verdict:
 * <ul>
 *   <li>{@code valid} — the mailbox receives mail: accepted;</li>
 *   <li>{@code invalid}, {@code spamtrap}, {@code abuse} — refused, with the contributor's message;</li>
 *   <li>{@code do_not_mail} — a band ZeroBounce means for mailing lists, read here for what a form wants to know:
 *   a role or group address ({@code role_based}, {@code role_based_catch_all}) receives mail and is accepted, the
 *   rest ({@code disposable}, {@code toxic}, {@code global_suppression}, {@code possible_trap}, a forwarding
 *   domain) is refused;</li>
 *   <li>{@code catch-all}, {@code unknown} and anything else — the service could not conclude: an unavailable
 *   check, which the contributor's {@code whenUnavailable} setting decides.</li>
 * </ul>
 * ZeroBounce answers a refused key or an account out of credits with a 200 carrying an {@code error} field, which
 * is an unavailable check as well, never a refusal.
 *
 * <p><strong>An example, not a supported connector</strong>, like its Experian twin: shipped by the samples module,
 * copied by a project with a ZeroBounce account, its key in the module's configuration. The service's sandbox addresses ({@code valid@example.com},
 * {@code invalid@example.com}…) answer without spending a credit and are what a local try-out types.</p>
 *
 * @see <a href="https://www.zerobounce.net/docs/email-validation-api-quickstart/v2-validate-emails">ZeroBounce API v2, validate</a>
 */
@Component(service = FieldAction.class, configurationPid = ZeroBounceEmailFieldAction.PID)
@Designate(ocd = SampleEndpointConfig.class)
public class ZeroBounceEmailFieldAction extends EmailVerificationFieldAction {

    public static final String NODE_TYPE = "fmdbsample:zeroBounceEmailAction";
    /** This action's own configuration: where ZeroBounce is, and the account's key. */
    public static final String PID = "org.jahia.test.modules.formidable.samples.zerobounce";
    /** The query parameter ZeroBounce reads the key from: the service's contract, not a setting. */
    static final String KEY_PARAMETER = "api_key";
    /** The v2 validation operation, under the service's base URL {@code https://api.zerobounce.net}. */
    static final String VALIDATE_OPERATION = "v2/validate";
    static final String VALID = "valid";
    static final String DO_NOT_MAIL = "do_not_mail";
    /** The statuses that say the address must not be used. */
    static final Set<String> REFUSED = Set.of("invalid", "spamtrap", "abuse");
    /** The do_not_mail reasons that still name a mailbox receiving mail: a role, a group. */
    static final Set<String> RECEIVING_ANYWAY = Set.of("role_based", "role_based_catch_all");

    @Reference
    private FieldActionGateway gateway;

    public ZeroBounceEmailFieldAction() {
    }

    ZeroBounceEmailFieldAction(FieldActionGateway gateway, FieldActionGateway.Endpoint endpoint) {
        this.gateway = gateway;
        useEndpoint(endpoint);
    }

    @Activate
    @Modified
    public void activate(SampleEndpointConfig config) {
        configure("ZeroBounce", config.url(), config._credential(), config.development(), KEY_PARAMETER, FieldActionGateway.Endpoint.CREDENTIAL_IN_QUERY);
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
    protected FieldActionResult verify(FieldActionGateway.Endpoint zeroBounce, String address) throws IOException {
        FieldActionGateway.Response response = gateway().get(zeroBounce,
                VALIDATE_OPERATION + "?email=" + URLEncoder.encode(address, StandardCharsets.UTF_8) + "&ip_address=");
        if (response.status() != 200) {
            return FieldActionResult.unavailable("the service answered " + response.status());
        }
        JSONObject body = json(response).orElse(null);
        if (body == null) {
            return FieldActionResult.unavailable("the service's answer is not the documented JSON");
        }
        String error = body.optString("error", "").trim();
        if (!error.isEmpty()) {
            // a refused key, an account out of credits: the service says so with a 200
            return FieldActionResult.unavailable("the service refused the call: " + error);
        }
        String status = body.optString("status", "").trim().toLowerCase(Locale.ROOT);
        String subStatus = body.optString("sub_status", "").trim().toLowerCase(Locale.ROOT);
        if (VALID.equals(status)) {
            return FieldActionResult.accept();
        }
        if (REFUSED.contains(status)) {
            return FieldActionResult.reject("status " + status + reason(subStatus));
        }
        if (DO_NOT_MAIL.equals(status)) {
            return RECEIVING_ANYWAY.contains(subStatus)
                    ? FieldActionResult.accept()
                    : FieldActionResult.reject("status do_not_mail" + reason(subStatus));
        }
        return FieldActionResult.unavailable("the service could not conclude: " + (status.isEmpty() ? "no status" : status) + reason(subStatus));
    }

    private static String reason(String subStatus) {
        return subStatus.isEmpty() ? "" : " (" + subStatus + ")";
    }
}
