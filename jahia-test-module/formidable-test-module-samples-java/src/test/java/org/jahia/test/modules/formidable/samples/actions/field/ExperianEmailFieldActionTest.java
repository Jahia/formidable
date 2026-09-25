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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The example implementation against Experian: what it sends, what it makes of each documented answer, and that
 * nothing the provider does — or fails to do — turns into a refusal on its own. The gateway is the one seam: a
 * test that called the provider would be a test of an account. The node is read by one line, {@code providerIdOf},
 * left to the live spec: the rules are judged from the id it yields.
 */
class ExperianEmailFieldActionTest {

    @FunctionalInterface
    private interface Answer {
        FieldActionGateway.Response get() throws IOException;
    }

    /** A gateway answering what the test decided, and remembering what it was asked. */
    private static final class Gateway implements FieldActionGateway {
        final List<String> providers = new ArrayList<>();
        final List<String> paths = new ArrayList<>();
        final List<String> bodies = new ArrayList<>();
        private final Answer answer;

        Gateway(Answer answer) {
            this.answer = answer;
        }

        @Override
        public Response post(String providerId, String path, String jsonBody) throws IOException {
            providers.add(providerId);
            paths.add(path);
            bodies.add(jsonBody);
            return answer.get();
        }

        @Override
        public Response get(String providerId, String path) {
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

    private static FieldActionResult judge(Gateway gateway, String value) {
        return new ExperianEmailFieldAction(gateway).judge("experian", of(value));
    }

    @Test
    void theAddressIsPostedToTheDocumentedOperationAndAVerifiedMailboxIsAccepted() {
        // Verifies the contract with the provider, exactly: the id from the node, Experian's v2 operation under the
        // base URL, a body that is the address and nothing else — and the one confidence that accepts.
        Gateway gateway = confident("verified");

        FieldActionResult result = judge(gateway, " ada@example.com ");

        assertEquals(FieldActionResult.Verdict.ACCEPT, result.verdict());
        assertEquals(List.of("experian"), gateway.providers);
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
    void aNodeWithoutAProviderIsAnUnavailableCheckWithoutACall() {
        // Verifies the node side: a node that names no provider — none at all, or one the reading yields nothing
        // for — leaves the action unable to know whom to ask, which is the contributor's setting to fix; the gateway
        // is not asked to guess.
        Gateway gateway = confident("verified");
        ExperianEmailFieldAction action = new ExperianEmailFieldAction(gateway);

        assertEquals(FieldActionResult.Verdict.UNAVAILABLE, action.execute(null, of("ada@example.com")).verdict());
        assertEquals(FieldActionResult.Verdict.UNAVAILABLE, action.judge(null, of("ada@example.com")).verdict());
        assertNull(ExperianEmailFieldAction.providerIdOf(null));

        assertEquals(List.of(), gateway.paths);
    }
}
