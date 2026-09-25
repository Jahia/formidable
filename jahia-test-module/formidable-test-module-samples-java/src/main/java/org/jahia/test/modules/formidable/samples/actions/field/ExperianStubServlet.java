package org.jahia.test.modules.formidable.samples.actions.field;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.osgi.service.component.annotations.Component;

import javax.servlet.Servlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * A double of Experian Email Validation v2, for the test suite and for a local try-out without an account: the
 * same operation ({@code POST email/validate/v2} under the base URL), the same {@code Auth-Token} header, the same
 * JSON in and out — with the confidence decided by the domain of the address instead of a mailbox lookup.
 * The samples module registers it at {@value #ALIAS} under {@code /modules}; one development provider line points
 * the engine at it:
 * {@code devFieldActionProviders=experian-stub|Experian (stub)|http://localhost:8080/modules/formidable-samples/experian-stub|Auth-Token|stub-token}
 * (with {@code enableDevFieldActionProviders=true}: a provider over plain HTTP is a development setting).
 *
 * <p>What it checks is what {@link ExperianEmailFieldAction} and the gateway must get right: the operation's path
 * under the base URL, the token header the gateway injects — any other token is refused with a 401, as Experian
 * does — and a JSON body carrying {@code email}. The answer follows the domain: {@code @undeliverable.test},
 * {@code @unreachable.test}, {@code @illegitimate.test}, {@code @disposable.test} and {@code @unknown.test} answer
 * that confidence, {@code @acceptall.test} the accept-all one, {@code @timeout.test} a 408 as the provider answers
 * when a domain does not respond; every other address is {@code verified}.</p>
 *
 * <p>Open to any caller, on purpose: it holds nothing, judges nothing real, and the samples module reaches no
 * product installation.</p>
 */
@Component(
    service = { HttpServlet.class, Servlet.class },
    property = { "alias=" + ExperianStubServlet.ALIAS },
    immediate = true
)
public class ExperianStubServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    public static final String ALIAS = "/formidable-samples/experian-stub";
    /** The token the stub accepts: the credential of the provider line that points at it. */
    public static final String TOKEN = "stub-token";
    static final String TOKEN_HEADER = "Auth-Token";
    /** The operation, as the sample action appends it to the base URL. */
    static final String VALIDATE_PATH = "/email/validate/v2";
    static final int MAX_BODY_CHARS = 4096;
    private static final int REQUEST_TIMEOUT = 408;
    /** The confidence each domain of the {@code .test} zone answers; any other domain is verified. */
    private static final Map<String, String> CONFIDENCE_BY_DOMAIN = Map.of(
            "undeliverable.test", "undeliverable",
            "unreachable.test", "unreachable",
            "illegitimate.test", "illegitimate",
            "disposable.test", "disposable",
            "unknown.test", "unknown",
            "acceptall.test", "acceptAll");

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        if (!VALIDATE_PATH.equals(operationOf(req))) {
            answer(resp, HttpServletResponse.SC_NOT_FOUND, error("No such operation"));
            return;
        }
        if (!TOKEN.equals(req.getHeader(TOKEN_HEADER))) {
            answer(resp, HttpServletResponse.SC_UNAUTHORIZED, error("Invalid token"));
            return;
        }
        String email = emailOf(read(req.getReader()));
        if (email == null) {
            answer(resp, HttpServletResponse.SC_BAD_REQUEST, error("The body must carry an email"));
            return;
        }
        String domain = EmailDeliverabilityFieldAction.domainOf(email);
        if ("timeout.test".equals(domain)) {
            answer(resp, REQUEST_TIMEOUT, error("Request timeout"));
            return;
        }
        answer(resp, HttpServletResponse.SC_OK, result(email, confidenceFor(domain)));
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
    }

    /** The path under the alias: the servlet's path info, or the request URI past the alias when the container gives none. */
    static String operationOf(HttpServletRequest req) {
        if (req.getPathInfo() != null) {
            return req.getPathInfo();
        }
        String uri = req.getRequestURI();
        int at = uri == null ? -1 : uri.indexOf(ALIAS);
        return at < 0 ? uri : uri.substring(at + ALIAS.length());
    }

    /** The confidence the stub answers for a domain: the one its name says, {@code verified} for every other; not an address at all is undeliverable, as the provider says. */
    static String confidenceFor(String domain) {
        if (domain == null) {
            return "undeliverable";
        }
        return CONFIDENCE_BY_DOMAIN.getOrDefault(domain, ExperianEmailFieldAction.VERIFIED);
    }

    /** The {@code email} of the request body; null when the body is not that JSON. */
    static String emailOf(String body) {
        try {
            String email = new JSONObject(body).optString("email", "").trim();
            return email.isEmpty() ? null : email;
        } catch (JSONException e) {
            return null;
        }
    }

    /** The provider's documented answer: the confidence, the address, a verbose reason, the suggestions. */
    static JSONObject result(String email, String confidence) {
        return new JSONObject().put("result", new JSONObject()
                .put("confidence", confidence)
                .put("email", email)
                .put("verbose_output", "stub")
                .put("did_you_mean", new JSONArray()));
    }

    private static JSONObject error(String message) {
        return new JSONObject().put("error", new JSONObject().put("message", message));
    }

    private static String read(Reader reader) throws IOException {
        StringBuilder body = new StringBuilder();
        char[] chunk = new char[512];
        int read;
        while (body.length() < MAX_BODY_CHARS && (read = reader.read(chunk)) != -1) {
            body.append(chunk, 0, read);
        }
        return body.toString();
    }

    private static void answer(HttpServletResponse resp, int status, JSONObject body) throws IOException {
        resp.setStatus(status);
        resp.setContentType("application/json");
        resp.setCharacterEncoding(StandardCharsets.UTF_8.name());
        resp.getWriter().write(body.toString());
    }
}
