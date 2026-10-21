package org.jahia.modules.formidable.engine.api;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RedirectTargetsTest {

    @Test
    void aPathOfTheSiteIsAccepted() {
        // Verifies the accepted shape: one leading slash, a path, optionally a query and a fragment.
        for (String path : List.of("/", "/home.html", "/sites/mysite/home.html", "/fr/compte?tab=2#top", "/a%20b/c")) {
            assertEquals(Optional.of(path), RedirectTargets.sameOriginPath(path), path);
        }
    }

    @Test
    void anythingThatCouldLeaveTheSiteIsRefused() {
        // Verifies the open-redirect rules: a scheme, a protocol-relative URL, a backslash form some browsers read as a
        // host, control characters (header injection), whitespace and a bare relative path all give no target.
        for (String candidate : List.of(
                "https://evil.example/", "//evil.example/x", "/\\evil.example", "\\\\evil.example", "javascript:alert(1)",
                "data:text/html,x", "home.html", "", "   ", "/ok\nSet-Cookie: a=b", "/a b", "/\tevil", "//", "/\\", "mailto:a@b.c")) {
            assertTrue(RedirectTargets.sameOriginPath(candidate).isEmpty(), candidate);
        }
    }

    @Test
    void nullAndOversizedAreRefused() {
        assertTrue(RedirectTargets.sameOriginPath(null).isEmpty());
        assertTrue(RedirectTargets.sameOriginPath("/" + "a".repeat(RedirectTargets.MAX_LENGTH)).isEmpty());
    }
}
