package org.jahia.test.modules.formidable.samples.actions.field;

import org.jahia.modules.formidable.engine.api.EmailVerificationFieldAction;
import org.jahia.modules.formidable.engine.api.FieldAction;
import org.jahia.modules.formidable.engine.api.FieldActionGateway;
import org.jahia.modules.formidable.engine.api.FieldActionResult;
import org.json.JSONObject;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;

/**
 * The second example implementation of a mailbox check behind a provider — ZeroBounce's email validation v2 — on
 * the engine's {@link EmailVerificationFieldAction}, beside the Experian one: the same skeleton, another vocabulary.
 * ZeroBounce reads its key off the URL, which is what the provider line's {@code query} placement is for
 * ({@code fieldActionProviders=zerobounce|ZeroBounce|https://api.zerobounce.net|api_key|<key>|query}); the address
 * goes as a query parameter too, as the provider documents its GET. The provider's {@code status} becomes the verdict:
 * <ul>
 *   <li>{@code valid} — the mailbox receives mail: accepted;</li>
 *   <li>{@code invalid}, {@code spamtrap}, {@code abuse} — refused, with the contributor's message;</li>
 *   <li>{@code do_not_mail} — a band ZeroBounce means for mailing lists, read here for what a form wants to know:
 *   a role or group address ({@code role_based}, {@code role_based_catch_all}) receives mail and is accepted, the
 *   rest ({@code disposable}, {@code toxic}, {@code global_suppression}, {@code possible_trap}, a forwarding
 *   domain) is refused;</li>
 *   <li>{@code catch-all}, {@code unknown} and anything else — the provider could not conclude: an unavailable
 *   check, which the contributor's {@code whenUnavailable} setting decides.</li>
 * </ul>
 * ZeroBounce answers a refused key or an account out of credits with a 200 carrying an {@code error} field, which
 * is an unavailable check as well, never a refusal.
 *
 * <p><strong>An example, not a supported connector</strong>, like its Experian twin: shipped by the samples module,
 * copied by a project with a ZeroBounce account. The provider's sandbox addresses ({@code valid@example.com},
 * {@code invalid@example.com}…) answer without spending a credit and are what a local try-out types.</p>
 *
 * @see <a href="https://www.zerobounce.net/docs/email-validation-api-quickstart/v2-validate-emails">ZeroBounce API v2, validate</a>
 */
@Component(service = FieldAction.class)
public class ZeroBounceEmailFieldAction extends EmailVerificationFieldAction {

    public static final String NODE_TYPE = "fmdbsample:zeroBounceEmailAction";
    /** The v2 validation operation, under the provider's base URL {@code https://api.zerobounce.net}. */
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

    ZeroBounceEmailFieldAction(FieldActionGateway gateway) {
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
        FieldActionGateway.Response response = gateway().get(providerId,
                VALIDATE_OPERATION + "?email=" + URLEncoder.encode(address, StandardCharsets.UTF_8) + "&ip_address=");
        if (response.status() != 200) {
            return FieldActionResult.unavailable("the provider answered " + response.status());
        }
        JSONObject body = json(response).orElse(null);
        if (body == null) {
            return FieldActionResult.unavailable("the provider's answer is not the documented JSON");
        }
        String error = body.optString("error", "").trim();
        if (!error.isEmpty()) {
            // a refused key, an account out of credits: the provider says so with a 200
            return FieldActionResult.unavailable("the provider refused the call: " + error);
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
        return FieldActionResult.unavailable("the provider could not conclude: " + (status.isEmpty() ? "no status" : status) + reason(subStatus));
    }

    private static String reason(String subStatus) {
        return subStatus.isEmpty() ? "" : " (" + subStatus + ")";
    }
}
