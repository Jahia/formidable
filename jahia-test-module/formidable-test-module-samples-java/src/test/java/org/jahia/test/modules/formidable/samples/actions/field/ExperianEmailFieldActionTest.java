package org.jahia.test.modules.formidable.samples.actions.field;

import org.jahia.modules.formidable.engine.api.FieldActionGateway;
import org.jahia.modules.formidable.engine.api.FieldActionRequest;
import org.jahia.modules.formidable.engine.api.FieldActionResult;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The example implementation against Experian: what it sends, what it makes of each documented answer, and that
 * nothing the service does — or fails to do — turns into a refusal on its own. The gateway is the one seam: a
 * test that called the service would be a test of an account. The endpoint comes from the action's own
 * configuration, read by {@code configure}: the tests hand one over, or a configuration.
 */
class ExperianEmailFieldActionTest {

    @FunctionalInterface
    private interface Answer {
        FieldActionGateway.Response get() throws IOException;
    }

    /** A gateway answering what the test decided, and remembering what it was asked. */
    private static final class Gateway implements FieldActionGateway {
        final List<String> services = new ArrayList<>();
        final List<Endpoint> endpoints = new ArrayList<>();
        final List<String> paths = new ArrayList<>();
        final List<String> bodies = new ArrayList<>();
        private final Answer answer;

        Gateway(Answer answer) {
            this.answer = answer;
        }

        @Override
        public Response post(Endpoint endpoint, String path, String jsonBody) throws IOException {
            services.add(endpoint.name() + " " + endpoint.credentialName() + "=" + endpoint.credential());
            endpoints.add(endpoint);
            paths.add(path);
            bodies.add(jsonBody);
            return answer.get();
        }

        @Override
        public Response get(Endpoint endpoint, String path) {
            throw new UnsupportedOperationException("the sample posts");
        }
    }

    private static Gateway answering(int status, String body) {
        return new Gateway(() -> new FieldActionGateway.Response(status, body));
    }

    private static Gateway confident(String confidence) {
        return answering(200, "{\"result\":{\"confidence\":\"" + confidence + "\",\"email\":\"x\",\"verbose_output\":\"stub\",\"did_you_mean\":[]}}");
    }

    private static FieldActionRequest of(String value) {
        return new FieldActionRequest("form-1", "email", value, Locale.ENGLISH);
    }

    /** The endpoint the samples' configuration describes: their double of Experian, the stub's token. */
    private static final FieldActionGateway.Endpoint STUB = FieldActionGateway.Endpoint.of("Experian",
            "http://localhost:8080/modules/formidable-samples/experian-stub", "Auth-Token", "stub-token", "header", true);

    private static FieldActionResult judge(Gateway gateway, String value) {
        return new ExperianEmailFieldAction(gateway, STUB).judge(of(value));
    }

    @Test
    void theAddressIsPostedToTheDocumentedOperationAndAVerifiedMailboxIsAccepted() {
        // Verifies the contract with the service, exactly: the action's own endpoint with the token in Experian's
        // header, the v2 operation under the base URL, a body that is the address and nothing else — and the one
        // confidence that accepts.
        Gateway gateway = confident("verified");

        FieldActionResult result = judge(gateway, " ada@example.com ");

        assertEquals(FieldActionResult.Verdict.ACCEPT, result.verdict());
        assertEquals(List.of("Experian Auth-Token=stub-token"), gateway.services);
        assertEquals(List.of("email/validate/v2"), gateway.paths);
        assertEquals(List.of("{\"email\":\"ada@example.com\"}"), gateway.bodies);
    }

    @Test
    void theBandExperianDocumentsAsRejectIsRefused() {
        // Verifies the four confidences the provider's documentation lists under "reject", and that the detail
        // names the confidence for the logs: the visitor's words are the contributor's, on the node.
        for (String confidence : List.of("undeliverable", "unreachable", "illegitimate", "disposable")) {
            FieldActionResult result = judge(confident(confidence), "ada@example.com");
            assertEquals(FieldActionResult.Verdict.REJECT, result.verdict(), confidence);
            assertTrue(result.detail().contains(confidence), result.detail());
        }
    }

    @Test
    void anAnswerTheProviderCannotConcludeIsAnUnavailableCheck() {
        // Verifies that the action never concludes in the provider's place: "unknown", a timeout on the domain, an
        // accept-all domain, a relay denied, a blank confidence — every one is the check that could not run, which
        // the contributor's whenUnavailable setting decides. Refusing here would refuse every accept-all domain.
        for (String confidence : List.of("unknown", "timeout", "acceptAll", "relayDenied", "")) {
            assertEquals(FieldActionResult.Verdict.UNAVAILABLE, judge(confident(confidence), "ada@example.com").verdict(), confidence);
        }
        assertEquals(FieldActionResult.Verdict.UNAVAILABLE, judge(answering(200, "{\"result\":{}}"), "ada@example.com").verdict());
        assertEquals(FieldActionResult.Verdict.UNAVAILABLE, judge(answering(200, "{}"), "ada@example.com").verdict());
    }

    @Test
    void aProviderThatDoesNotAnswerIsAnUnavailableCheckNeverARefusal() {
        // Verifies the outage rule on every way the call can fail: the network, each status the provider documents
        // for a refused token, spent credits, its own timeout, its rate limit, an outage — and an answer that is not
        // its JSON, or a provider id the configuration no longer declares. None is a verdict on the address.
        assertEquals(FieldActionResult.Verdict.UNAVAILABLE, judge(new Gateway(() -> {
            throw new IOException("connection refused");
        }), "ada@example.com").verdict());
        for (int status : List.of(400, 401, 403, 408, 429, 500, 503)) {
            assertEquals(FieldActionResult.Verdict.UNAVAILABLE, judge(answering(status, ""), "ada@example.com").verdict(), String.valueOf(status));
        }
        assertEquals(FieldActionResult.Verdict.UNAVAILABLE, judge(answering(200, "<html>maintenance</html>"), "ada@example.com").verdict());
        assertEquals(FieldActionResult.Verdict.UNAVAILABLE, judge(answering(200, null), "ada@example.com").verdict());
        assertEquals(FieldActionResult.Verdict.UNAVAILABLE, judge(new Gateway(() -> {
            throw new IllegalArgumentException("No field action provider is configured under the id 'experian'");
        }), "ada@example.com").verdict());
    }

    @Test
    void aValueThatIsNotAnAddressIsAcceptedWithoutACall() {
        // Verifies that nothing is spent on a value the field's own validation will judge: every answer of the
        // provider is chargeable, and the shape of an address is step 9's business.
        Gateway gateway = confident("undeliverable");

        assertEquals(FieldActionResult.Verdict.ACCEPT, judge(gateway, "hello").verdict());
        assertEquals(FieldActionResult.Verdict.ACCEPT, judge(gateway, "").verdict());
        assertEquals(FieldActionResult.Verdict.ACCEPT, judge(gateway, "a@b@c.example").verdict());

        assertEquals(List.of(), gateway.paths);
    }

    @Test
    void anActionWithoutAConfiguredServiceIsAnUnavailableCheckWithoutACall() {
        // Verifies the configuration side: the action's configuration has no token yet (the annotation's default),
        // so there is nobody to ask — an unavailable check, which the contributor's setting decides; the gateway is
        // not asked. A configuration with its token gives the endpoint, the credential in Experian's header.
        Gateway gateway = confident("verified");
        ExperianEmailFieldAction action = new ExperianEmailFieldAction(gateway, null);

        assertEquals(FieldActionResult.Verdict.UNAVAILABLE, action.execute(null, of("ada@example.com")).verdict());
        assertEquals(List.of(), gateway.paths);

        action.activate(config("https://api.experianaperture.io", "t0k3n", false));
        assertEquals(FieldActionResult.Verdict.ACCEPT, action.judge(of("ada@example.com")).verdict());
        assertEquals(List.of("Experian Auth-Token=t0k3n"), gateway.services);

        action.activate(config("https://api.experianaperture.io", "", false));
        assertEquals(FieldActionResult.Verdict.UNAVAILABLE, action.judge(of("ada@example.com")).verdict());
    }

    /** A configuration as DS hands it: the three settings, the rest of the annotation's methods never called. */
    static SampleEndpointConfig config(String url, String credential, boolean development) {
        return new SampleEndpointConfig() {
            @Override public Class<? extends java.lang.annotation.Annotation> annotationType() { return SampleEndpointConfig.class; }
            @Override public String url() { return url; }
            @Override public String _credential() { return credential; }
            @Override public boolean development() { return development; }
        };
    }

    @Test
    void theConfigurationBecomesTheEndpointWithTheTokenInExperiansHeader() {
        // Verifies the path a copying project relies on, field by field: the administrator's URL — not a default —,
        // the token in the Auth-Token header, never on the URL where access logs keep it, and the development flag.
        Gateway gateway = confident("verified");
        ExperianEmailFieldAction action = new ExperianEmailFieldAction(gateway, null);

        action.activate(config("https://eu.experianaperture.io/v2", "t0k3n", false));
        action.judge(of("ada@example.com"));
        FieldActionGateway.Endpoint endpoint = gateway.endpoints.get(0);
        assertEquals(java.net.URI.create("https://eu.experianaperture.io/v2"), endpoint.baseUri());
        assertEquals("Auth-Token", endpoint.credentialName());
        assertFalse(endpoint.credentialInQuery());
        assertFalse(endpoint.development());

        action.activate(config("http://localhost:8080/stub", "stub-token", true));
        action.judge(of("bob@example.com"));
        assertTrue(gateway.endpoints.get(1).development());
    }
}
