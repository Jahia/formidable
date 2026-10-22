package org.jahia.modules.formidable.engine.api;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RedirectTargetsTest {

    @Test
    void aPathOfThisServerIsAccepted() {
        // Verifies the accepted shape: one leading slash, a path, optionally a query and a fragment.
        for (String path : List.of("/", "/home.html", "/sites/mysite/home.html", "/fr/compte?tab=2#top", "/a%20b/c")) {
            assertEquals(Optional.of(path), RedirectTargets.sameOriginPath(path), path);
        }
    }

    @Test
    void anythingThatCouldLeaveTheServerIsRefused() {
        // Verifies the open-redirect rules: a scheme, a protocol-relative URL, a backslash form some browsers read as a
        // host, control characters (header injection), whitespace and a bare relative path all give no target.
        for (String candidate : List.of(
                "https://evil.example/", "//evil.example/x", "/\\evil.example", "\\\\evil.example", "javascript:alert(1)",
                "data:text/html,x", "home.html", "", "   ", "/ok\nSet-Cookie: a=b", "/a b", "/\tevil", "//", "/\\", "mailto:a@b.c")) {
            assertTrue(RedirectTargets.sameOriginPath(candidate).isEmpty(), candidate);
        }
    }

    @Test
    void theCharactersTheBrowserRefusesAreRefusedHereToo() {
        // Verifies the server and the island agree: a byte-order mark, a zero-width space, the non-breaking spaces and a
        // C1 control would pass a check of ASCII whitespace alone, and the island refuses them, so a path the server
        // sent would be silently ignored.
        for (String candidate : List.of("/a﻿b", "/a​b", "/a b", "/a b", "/a b", "/a b", "/a\u0085b")) {
            assertTrue(RedirectTargets.sameOriginPath(candidate).isEmpty(), candidate);
            assertTrue(RedirectTargets.httpUrl("https://www.example.com" + candidate).isEmpty(), candidate);
        }
    }

    @Test
    void nullAndOversizedAreRefused() {
        assertTrue(RedirectTargets.sameOriginPath(null).isEmpty());
        assertTrue(RedirectTargets.sameOriginPath("/" + "a".repeat(RedirectTargets.MAX_LENGTH)).isEmpty());
        assertTrue(RedirectTargets.httpUrl(null).isEmpty());
        assertTrue(RedirectTargets.httpUrl("https://www.example.com/" + "a".repeat(RedirectTargets.MAX_LENGTH)).isEmpty());
    }

    @Test
    void anAbsoluteHttpOrHttpsUrlIsAccepted() {
        // Verifies the second rule, for a URL read from the configuration: http or https, a host, any path and query.
        for (String url : List.of("https://www.example.com", "http://www.example.com/merci.html", "HTTPS://WWW.EXAMPLE.COM/a?b=c#d",
                "https://www.example.com:8443/fr/merci")) {
            assertEquals(Optional.of(url), RedirectTargets.httpUrl(url), url);
        }
    }

    @Test
    void aUrlThatIsNotAnAbsoluteHttpUrlIsRefused() {
        // Verifies the second rule refuses another scheme, a URL with no host, a protocol-relative URL, a relative path,
        // and user info, which a visitor reads as the host (https://www.example.com@evil.example goes to evil.example).
        for (String candidate : List.of(
                "javascript:alert(1)", "data:text/html,x", "ftp://www.example.com/", "mailto:a@b.c", "https://", "https:///path",
                "//evil.example/x", "/sites/mysite/home.html", "home.html", "https://user@evil.example/",
                "https://www.example.com@evil.example/", "https://user:pass@www.example.com/", "https://www.example.com/a b",
                "https://www.example.com/\\evil", "", "   ")) {
            assertTrue(RedirectTargets.httpUrl(candidate).isEmpty(), candidate);
        }
    }
}
