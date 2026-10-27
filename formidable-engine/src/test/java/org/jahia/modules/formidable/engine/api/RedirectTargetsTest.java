package org.jahia.modules.formidable.engine.api;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
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
    void aNoBreakSpaceAroundAUrlIsNotTrimmedButRefused() {
        // Verifies what the authoring card (redirectAction.server.ts) trims as this class does: String.trim() strips only
        // what is up to U+0020, so a no-break space or a U+FEFF pasted around the address stays, and the card warns.
        for (String candidate : List.of("https://example.com/ ", " https://example.com/", "https://example.com/﻿")) {
            assertTrue(RedirectTargets.httpUrl(candidate).isEmpty(), candidate);
        }
        assertEquals(Optional.of("https://example.com/"), RedirectTargets.httpUrl(" https://example.com/\t"));
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

    @Test
    void anAbsoluteUrlOfThisServerIsReducedToItsPath() {
        // Verifies pathOnServer: the path and query of a URL of the host the request reached, never another host's.
        assertEquals(Optional.of("/fr/merci.html?x=1"), RedirectTargets.pathOnServer("https://www.mysite.com/fr/merci.html?x=1", "www.mysite.com"));
        assertEquals(Optional.of("/"), RedirectTargets.pathOnServer("http://WWW.mysite.com", "www.mysite.com"));
        assertTrue(RedirectTargets.pathOnServer("https://other.example/fr/merci.html", "www.mysite.com").isEmpty());
        assertTrue(RedirectTargets.pathOnServer("javascript://www.mysite.com/%0aalert(1)", "www.mysite.com").isEmpty());
        assertTrue(RedirectTargets.pathOnServer(null, "www.mysite.com").isEmpty());
    }

    @Test
    void theServletContextPathIsSwappedForThePlatforms() {
        // Verifies swapContextPath: a link rewritten under the OSGi servlet's /modules carries the platform's context.
        assertEquals("/jahia/fr/merci.html", RedirectTargets.swapContextPath("/modules/fr/merci.html", "/modules", "/jahia"));
        assertEquals("/fr/merci.html", RedirectTargets.swapContextPath("/modules/fr/merci.html", "/modules", ""));
        assertEquals("/fr/merci.html", RedirectTargets.swapContextPath("/fr/merci.html", "/modules", "/jahia"));
        assertEquals("/modulesx/a", RedirectTargets.swapContextPath("/modulesx/a", "/modules", ""));
    }
}
