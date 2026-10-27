package org.jahia.modules.formidable.engine.api;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RedirectTargetsTest {

    /** The cases the island's check reads too (useFormSubmission.test.ts): one list, so the two rules cannot drift apart. */
    private static JSONArray sharedCases() throws IOException {
        try (InputStream in = RedirectTargetsTest.class.getResourceAsStream("/redirect-targets.json")) {
            assertNotNull(in, "redirect-targets.json");
            return new JSONObject(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getJSONArray("cases");
        }
    }

    private static Optional<String> expected(JSONObject testCase, String rule) {
        return testCase.isNull(rule) ? Optional.empty() : Optional.of(testCase.getString(rule));
    }

    @Test
    void eachSharedTargetGetsTheAnswerBothChecksAgreeOn() throws IOException {
        // Verifies both rules on the shared list: a path of this server, an absolute http(s) URL, what a browser
        // accepts and java.net.URI does not (sent encoded), and what could leave the server or break the response.
        JSONArray cases = sharedCases();
        for (int i = 0; i < cases.length(); i++) {
            JSONObject testCase = cases.getJSONObject(i);
            String target = testCase.getString("target");
            assertEquals(expected(testCase, "sameOriginPath"), RedirectTargets.sameOriginPath(target), "sameOriginPath " + target);
            assertEquals(expected(testCase, "httpUrl"), RedirectTargets.httpUrl(target), "httpUrl " + target);
        }
    }

    @Test
    void anAcceptedTargetIsAcceptedAgainUnchanged() throws IOException {
        // Verifies the encoding is done once: what a rule sends passes the same rule as it is, so nothing is encoded twice.
        JSONArray cases = sharedCases();
        for (int i = 0; i < cases.length(); i++) {
            JSONObject testCase = cases.getJSONObject(i);
            expected(testCase, "sameOriginPath").ifPresent(sent -> assertEquals(Optional.of(sent), RedirectTargets.sameOriginPath(sent), sent));
            expected(testCase, "httpUrl").ifPresent(sent -> assertEquals(Optional.of(sent), RedirectTargets.httpUrl(sent), sent));
        }
    }

    @Test
    void aTargetIsTrimmed() {
        assertEquals(Optional.of("/home.html"), RedirectTargets.sameOriginPath("  /home.html "));
        assertEquals(Optional.of("https://www.example.com/"), RedirectTargets.httpUrl(" https://www.example.com/\t"));
    }

    @Test
    void nullAndOversizedAreRefused() {
        assertTrue(RedirectTargets.sameOriginPath(null).isEmpty());
        assertTrue(RedirectTargets.sameOriginPath("/" + "a".repeat(RedirectTargets.MAX_LENGTH)).isEmpty());
        assertTrue(RedirectTargets.httpUrl(null).isEmpty());
        assertTrue(RedirectTargets.httpUrl("https://www.example.com/" + "a".repeat(RedirectTargets.MAX_LENGTH)).isEmpty());
        // the cap applies to what is sent: a target that the encoding takes past it is refused
        assertTrue(RedirectTargets.sameOriginPath("/" + "|".repeat(RedirectTargets.MAX_LENGTH / 2)).isEmpty());
    }
}
