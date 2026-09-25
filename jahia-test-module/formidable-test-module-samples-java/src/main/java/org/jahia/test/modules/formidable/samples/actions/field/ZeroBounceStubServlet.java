package org.jahia.test.modules.formidable.samples.actions.field;

import org.jahia.modules.formidable.engine.api.EmailAddress;
import org.json.JSONObject;
import org.osgi.service.component.annotations.Component;

import javax.servlet.Servlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * A double of ZeroBounce's email validation v2, beside the Experian one: the same operation ({@code GET v2/validate}
 * under the base URL), the key read off the URL ({@code api_key}, which the gateway appends for a provider line
 * ending in {@code |query}), the same JSON — with the status decided by the address instead of a mailbox lookup.
 * The development provider line:
 * {@code devFieldActionProviders=zerobounce-stub|ZeroBounce (stub)|http://localhost:8080/modules/formidable-samples/zerobounce-stub|api_key|stub-token|query}.
 *
 * <p>Another key is answered as the provider answers it, a 200 carrying {@code error}. The status follows the
 * address: {@code @invalid.test} is invalid, {@code @spamtrap.test} and {@code @abuse.test} say so,
 * {@code @disposable.test} and {@code @toxic.test} are do_not_mail for that reason, a role local part
 * ({@code info@}, {@code contact@}, {@code sales@}, {@code support@}) is do_not_mail / role_based,
 * {@code @catchall.test} is catch-all, {@code @unknown.test} unknown, a value that is not an address invalid for
 * its syntax; every other address is valid.</p>
 */
@Component(
    service = { HttpServlet.class, Servlet.class },
    property = { "alias=" + ZeroBounceStubServlet.ALIAS },
    immediate = true
)
public class ZeroBounceStubServlet extends ProviderStubServlet {

    private static final long serialVersionUID = 1L;

    public static final String ALIAS = "/formidable-samples/zerobounce-stub";
    static final String KEY_PARAMETER = "api_key";
    /** The operation, as the sample action appends it to the base URL. */
    static final String VALIDATE_OPERATION = "/v2/validate";
    /** What the provider answers to a wrong key or an account without credits — with a 200. */
    static final String REFUSED_KEY = "Invalid API Key or your account ran out of credits";
    private static final Set<String> ROLES = Set.of("info", "contact", "sales", "support");
    private static final Map<String, String[]> STATUS_BY_DOMAIN = Map.of(
            "invalid.test", new String[] {"invalid", "mailbox_not_found"},
            "spamtrap.test", new String[] {"spamtrap", ""},
            "abuse.test", new String[] {"abuse", ""},
            "disposable.test", new String[] {"do_not_mail", "disposable"},
            "toxic.test", new String[] {"do_not_mail", "toxic"},
            "catchall.test", new String[] {"catch-all", ""},
            "unknown.test", new String[] {"unknown", "timeout_exceeded"});

    @Override
    protected String alias() {
        return ALIAS;
    }

    @Override
    protected void get(HttpServletRequest req, HttpServletResponse resp, String operation) throws IOException {
        if (!VALIDATE_OPERATION.equals(operation)) {
            answer(resp, HttpServletResponse.SC_NOT_FOUND, new JSONObject().put("error", "No such operation"));
            return;
        }
        if (!TOKEN.equals(req.getParameter(KEY_PARAMETER))) {
            answer(resp, HttpServletResponse.SC_OK, new JSONObject().put("error", REFUSED_KEY));
            return;
        }
        String email = req.getParameter("email");
        if (email == null || email.isBlank()) {
            answer(resp, HttpServletResponse.SC_BAD_REQUEST, new JSONObject().put("error", "The email is missing"));
            return;
        }
        String[] status = statusFor(email.trim());
        answer(resp, HttpServletResponse.SC_OK, new JSONObject()
                .put("address", email.trim())
                .put("status", status[0])
                .put("sub_status", status[1])
                .put("free_email", false)
                .put("did_you_mean", JSONObject.NULL));
    }

    /** The status and sub-status the stub answers for an address. */
    static String[] statusFor(String email) {
        String domain = EmailAddress.domainOf(email);
        if (domain == null) {
            return new String[] {"invalid", "failed_syntax_check"};
        }
        String local = email.substring(0, email.lastIndexOf('@')).toLowerCase(Locale.ROOT);
        if (ROLES.contains(local)) {
            return new String[] {"do_not_mail", "role_based"};
        }
        return STATUS_BY_DOMAIN.getOrDefault(domain, new String[] {"valid", ""});
    }
}
