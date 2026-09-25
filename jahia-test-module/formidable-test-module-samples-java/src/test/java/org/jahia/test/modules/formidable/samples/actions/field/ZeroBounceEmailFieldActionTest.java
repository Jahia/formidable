package org.jahia.test.modules.formidable.samples.actions.field;

import org.jahia.modules.formidable.engine.api.FieldActionGateway;
import org.jahia.modules.formidable.engine.api.FieldActionRequest;
import org.jahia.modules.formidable.engine.api.FieldActionResult;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The ZeroBounce example: what it asks — a GET with the address on the URL, the key being the gateway's business —
 * and what it makes of the provider's statuses, read for what a form wants to know rather than for a mailing list.
 */
class ZeroBounceEmailFieldActionTest {

    /** A gateway answering what the test decided, and remembering what it was asked. */
    private static final class Gateway implements FieldActionGateway {
        final List<String> paths = new ArrayList<>();
        private final int status;
        private final String body;

        Gateway(int status, String body) {
            this.status = status;
            this.body = body;
        }

        @Override
        public Response post(String providerId, String path, String jsonBody) {
            throw new UnsupportedOperationException("the sample gets");
        }

        @Override
        public Response get(String providerId, String path) {
            paths.add(providerId + " " + path);
            return new Response(status, body);
        }
    }

    private static FieldActionResult judge(String status, String subStatus, String address) {
        String body = "{\"address\":\"" + address + "\",\"status\":\"" + status + "\",\"sub_status\":\"" + subStatus + "\",\"free_email\":false}";
        return new ZeroBounceEmailFieldAction(new Gateway(200, body)).judge("zerobounce", of(address));
    }

    private static FieldActionRequest of(String value) {
        return new FieldActionRequest("form-1", "email", value, Locale.ENGLISH);
    }

    @Test
    void theAddressGoesOnTheUrlOfTheDocumentedOperationAndAValidMailboxIsAccepted() {
        // Verifies the contract with the provider: a GET on v2/validate, the address encoded as the email parameter
        // and the ip_address one left empty, the key nowhere in what the action sends — the gateway appends it.
        Gateway gateway = new Gateway(200, "{\"address\":\"ada+1@example.com\",\"status\":\"valid\",\"sub_status\":\"\"}");

        FieldActionResult result = new ZeroBounceEmailFieldAction(gateway).judge("zerobounce", of(" ada+1@example.com "));

        assertEquals(FieldActionResult.Verdict.ACCEPT, result.verdict());
        assertEquals(List.of("zerobounce v2/validate?email=ada%2B1%40example.com&ip_address="), gateway.paths);
    }

    @Test
    void anAddressThatMustNotBeUsedIsRefusedAndARoleAddressIsNot() {
        // Verifies the reading of do_not_mail for a form: a role or group mailbox receives mail and is accepted, where a
        // mailing list would skip it; a disposable, toxic, suppressed or trap-like one is refused, as are the statuses
        // that say the address is bad outright. The detail names the status for the logs.
        for (String status : List.of("invalid", "spamtrap", "abuse")) {
            assertEquals(FieldActionResult.Verdict.REJECT, judge(status, "", "ada@example.com").verdict(), status);
        }
        for (String subStatus : List.of("disposable", "toxic", "global_suppression", "possible_trap", "mx_forward", "")) {
            FieldActionResult result = judge("do_not_mail", subStatus, "ada@example.com");
            assertEquals(FieldActionResult.Verdict.REJECT, result.verdict(), subStatus);
            assertTrue(result.detail().contains("do_not_mail"), result.detail());
        }
        assertEquals(FieldActionResult.Verdict.ACCEPT, judge("do_not_mail", "role_based", "info@example.com").verdict());
        assertEquals(FieldActionResult.Verdict.ACCEPT, judge("do_not_mail", "role_based_catch_all", "info@example.com").verdict());
        assertEquals(FieldActionResult.Verdict.ACCEPT, judge("Valid", "alias_address", "ada@example.com").verdict());
    }

    @Test
    void anAnswerTheProviderCannotConcludeIsAnUnavailableCheck() {
        // Verifies that the action never concludes in the provider's place: a catch-all domain, an unknown, a status
        // it has never heard of, no status at all — the check that could not run, for the contributor's setting.
        for (String status : List.of("catch-all", "unknown", "something_new", "")) {
            assertEquals(FieldActionResult.Verdict.UNAVAILABLE, judge(status, "greylisted", "ada@example.com").verdict(), status);
        }
    }

    @Test
    void aRefusedKeyAnOutageOrAnUnreadableAnswerIsAnUnavailableCheckNeverARefusal() {
        // Verifies the provider's own way of refusing a call — a 200 carrying an error field, for a wrong key or an
        // account out of credits — and the plainer failures: a status that is not 200, a body that is not its JSON.
        ZeroBounceEmailFieldAction refusedKey = new ZeroBounceEmailFieldAction(new Gateway(200, "{\"error\":\"Invalid API Key or your account ran out of credits\"}"));
        FieldActionResult result = refusedKey.judge("zerobounce", of("ada@example.com"));
        assertEquals(FieldActionResult.Verdict.UNAVAILABLE, result.verdict());
        assertTrue(result.detail().contains("Invalid API Key"), result.detail());

        for (int status : List.of(400, 401, 429, 500, 503)) {
            assertEquals(FieldActionResult.Verdict.UNAVAILABLE, new ZeroBounceEmailFieldAction(new Gateway(status, "")).judge("zerobounce", of("ada@example.com")).verdict(), String.valueOf(status));
        }
        assertEquals(FieldActionResult.Verdict.UNAVAILABLE, new ZeroBounceEmailFieldAction(new Gateway(200, "<html>maintenance</html>")).judge("zerobounce", of("ada@example.com")).verdict());
    }

    @Test
    void aValueThatIsNotAnAddressIsAcceptedWithoutACall() {
        // Verifies the engine base at work in this sample: nothing is spent on a value the field's own validation judges.
        Gateway gateway = new Gateway(200, "{\"status\":\"invalid\"}");
        ZeroBounceEmailFieldAction action = new ZeroBounceEmailFieldAction(gateway);

        assertEquals(FieldActionResult.Verdict.ACCEPT, action.judge("zerobounce", of("hello")).verdict());
        assertEquals(FieldActionResult.Verdict.ACCEPT, action.judge("zerobounce", of("")).verdict());
        assertEquals(List.of(), gateway.paths);
    }
}
