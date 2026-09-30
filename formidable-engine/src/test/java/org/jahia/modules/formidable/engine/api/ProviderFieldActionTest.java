package org.jahia.modules.formidable.engine.api;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The shape every check behind an external service inherits: the endpoint from the action's own configuration, the
 * values it declines to judge, and the failures that are the check not running rather than a verdict.
 */
class ProviderFieldActionTest {

    private static final FieldActionGateway.Endpoint CRM =
            new FieldActionGateway.Endpoint("CRM", URI.create("https://crm.example.com/api"), "X-Api-Key", "s3cr3t", false, false);

    /** A gateway that answers what the test decided, or throws, and remembers what it was asked. */
    private static final class Gateway implements FieldActionGateway {
        final List<String> asked = new ArrayList<>();
        private final IOException failure;
        private final RuntimeException refusal;

        Gateway(IOException failure, RuntimeException refusal) {
            this.failure = failure;
            this.refusal = refusal;
        }

        @Override
        public Response post(Endpoint endpoint, String path, String jsonBody) throws IOException {
            asked.add(endpoint.name() + " " + path);
            if (failure != null) {
                throw failure;
            }
            if (refusal != null) {
                throw refusal;
            }
            return new Response(200, "{\"ok\":true}");
        }

        @Override
        public Response get(Endpoint endpoint, String path) throws IOException {
            return post(endpoint, path, null);
        }
    }

    /** The least an action writes: where the service is, the call and the reading of the answer. */
    private static final class Lookup extends ProviderFieldAction {
        private final Gateway gateway;
        private final FieldActionGateway.Endpoint endpoint;

        Lookup(Gateway gateway, FieldActionGateway.Endpoint endpoint) {
            this.gateway = gateway;
            this.endpoint = endpoint;
        }

        @Override
        public String getNodeType() {
            return "test:lookup";
        }

        @Override
        protected Optional<FieldActionGateway.Endpoint> endpoint() {
            return Optional.ofNullable(endpoint);
        }

        @Override
        protected FieldActionResult ask(FieldActionGateway.Endpoint service, FieldActionRequest request) throws IOException {
            return json(gateway().post(service, "lookup", "{}"))
                    .map(body -> body.optBoolean("ok") ? FieldActionResult.accept() : FieldActionResult.reject("no"))
                    .orElse(FieldActionResult.unavailable("not json"));
        }

        @Override
        protected FieldActionGateway gateway() {
            return gateway;
        }
    }

    private static FieldActionRequest of(String value) {
        return new FieldActionRequest("form-1", "code", value, Locale.ENGLISH);
    }

    @Test
    void theActionsOwnEndpointIsAskedAndTheAnswerJudged() {
        // Verifies the nominal path end to end: the endpoint the action's configuration describes reaches the gateway,
        // the answer reaches the action's own reading — no node is read, the contributor has no service to pick.
        Gateway gateway = new Gateway(null, null);

        assertEquals(FieldActionResult.Verdict.ACCEPT, new Lookup(gateway, CRM).execute(null, of("42")).verdict());

        assertEquals(List.of("CRM lookup"), gateway.asked);
    }

    @Test
    void aServiceThatIsNotConfiguredIsAnUnavailableCheckWithoutACall() {
        // Verifies the configuration side: an action whose module has no usable endpoint yet (no credential, a refused
        // URL) cannot ask anyone — an unavailable check, which the contributor's setting decides — and nothing leaves.
        Gateway gateway = new Gateway(null, null);

        FieldActionResult result = new Lookup(gateway, null).judge(of("42"));

        assertEquals(FieldActionResult.Verdict.UNAVAILABLE, result.verdict());
        assertEquals(List.of(), gateway.asked);
    }

    @Test
    void aValueTheActionDoesNotJudgeIsAcceptedWithoutACall() {
        // Verifies the default of concerns(): a blank value is the required-field validation's business, and every
        // answer of a service has a price — so nothing leaves.
        Gateway gateway = new Gateway(null, null);

        assertEquals(FieldActionResult.Verdict.ACCEPT, new Lookup(gateway, CRM).judge(of("")).verdict());
        assertEquals(FieldActionResult.Verdict.ACCEPT, new Lookup(gateway, CRM).judge(of("   ")).verdict());
        assertEquals(FieldActionResult.Verdict.ACCEPT, new Lookup(gateway, null).judge(of(null)).verdict());

        assertEquals(List.of(), gateway.asked);
    }

    @Test
    void aServiceThatDoesNotAnswerOrIsRefusedIsAnUnavailableCheckNeverARefusal() {
        // Verifies the outage rule at the base, once for every module: the network failing, and a URL the endpoint
        // rule refuses (the gateway's IllegalArgumentException) — neither is a verdict on the value.
        FieldActionResult down = new Lookup(new Gateway(new IOException("connection refused"), null), CRM).judge(of("42"));
        FieldActionResult refused = new Lookup(new Gateway(null, new IllegalArgumentException("The URL of CRM is refused")), CRM).judge(of("42"));

        assertEquals(FieldActionResult.Verdict.UNAVAILABLE, down.verdict());
        assertTrue(down.detail().contains("IOException"), down.detail());
        assertEquals(FieldActionResult.Verdict.UNAVAILABLE, refused.verdict());
    }

    @Test
    void aConfigurationGivesAnEndpointOnlyWithACredentialAndAUsableUrlAndSaysWhyNot() {
        // Verifies endpointOf, what a module calls when its configuration is read: no credential yet, a refused URL —
        // empty, with a line naming the service, never the credential; a complete configuration, the endpoint.
        PrintStream previous = System.err;
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        System.setErr(new PrintStream(captured, true, StandardCharsets.UTF_8));
        Optional<FieldActionGateway.Endpoint> noCredential;
        Optional<FieldActionGateway.Endpoint> plainHttp;
        try {
            noCredential = ProviderFieldAction.endpointOf("Experian", "https://api.experianaperture.io", "Auth-Token", " ", "header", false);
            plainHttp = ProviderFieldAction.endpointOf("Experian", "http://api.experianaperture.io", "Auth-Token", "t0k3n", "header", false);
        } finally {
            System.setErr(previous);
        }

        assertTrue(noCredential.isEmpty());
        assertTrue(plainHttp.isEmpty());
        String logged = captured.toString(StandardCharsets.UTF_8);
        assertTrue(logged.contains("No credential is configured for Experian"), logged);
        assertTrue(logged.contains("the URL of Experian is refused"), logged);
        assertFalse(logged.contains("t0k3n"), logged);
        assertEquals("t0k3n", ProviderFieldAction.endpointOf("Experian", "https://api.experianaperture.io", "Auth-Token",
                "t0k3n", "header", false).orElseThrow().credential());
    }

    @Test
    void theAnswerIsReadAsOneJsonObjectOrNotAtAll() {
        // Verifies the helper every module reads its answer through: a body that is not one object — empty, a
        // maintenance page, an array — is nothing to judge, which the module turns into an unavailable check.
        assertTrue(ProviderFieldAction.json(new FieldActionGateway.Response(200, "{\"ok\":true}")).isPresent());
        assertTrue(ProviderFieldAction.json(new FieldActionGateway.Response(200, "")).isEmpty());
        assertTrue(ProviderFieldAction.json(new FieldActionGateway.Response(200, null)).isEmpty());
        assertTrue(ProviderFieldAction.json(new FieldActionGateway.Response(503, "<html>maintenance</html>")).isEmpty());
        assertTrue(ProviderFieldAction.json(new FieldActionGateway.Response(200, "[1,2]")).isEmpty());
        assertTrue(ProviderFieldAction.json(null).isEmpty());
    }
}
