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

    @Test
    void thePathOfTheRefererIsKeptWhenThePageIsOnThisServer() {
        // Verifies the "page the visitor came from" source: only path and query are kept, never scheme nor host.
        assertEquals(Optional.of("/fr/connexion?redirect=/compte"),
                RedirectTargets.pathOfReferer("https://www.example.com/fr/connexion?redirect=/compte", "www.example.com"));
        assertEquals(Optional.of("/"), RedirectTargets.pathOfReferer("http://WWW.example.com", "www.example.com"));
    }

    @Test
    void aRefererOfAnotherHostOrSchemeGivesNothing() {
        assertTrue(RedirectTargets.pathOfReferer("https://evil.example/home", "www.example.com").isEmpty());
        assertTrue(RedirectTargets.pathOfReferer("ftp://www.example.com/home", "www.example.com").isEmpty());
        assertTrue(RedirectTargets.pathOfReferer("not a url", "www.example.com").isEmpty());
        assertTrue(RedirectTargets.pathOfReferer(null, "www.example.com").isEmpty());
        assertTrue(RedirectTargets.pathOfReferer("https://www.example.com/", null).isEmpty());
        // userinfo trick: the host is evil.example, whatever the text before the @ says
        assertTrue(RedirectTargets.pathOfReferer("https://www.example.com@evil.example/x", "www.example.com").isEmpty());
    }

    @Test
    void theReturnParameterOfThePageIsReadAndValidated() {
        // Verifies /login?redirect=/account: the value is decoded, then held to the same rule as any target.
        String page = "https://www.example.com/login?lang=fr&redirect=%2Faccount%2Fprofile";
        assertEquals(Optional.of("/account/profile"), RedirectTargets.returnPath(page, "www.example.com", "redirect"));
        assertTrue(RedirectTargets.returnPath(page, "www.example.com", "next").isEmpty());
    }

    @Test
    void aReturnParameterNamingAnotherSiteIsRefused() {
        assertTrue(RedirectTargets.returnPath("https://www.example.com/login?redirect=https%3A%2F%2Fevil.example", "www.example.com", "redirect").isEmpty());
        assertTrue(RedirectTargets.returnPath("https://www.example.com/login?redirect=%2F%2Fevil.example", "www.example.com", "redirect").isEmpty());
        assertTrue(RedirectTargets.returnPath("https://www.example.com/login?redirect=%2F%5Cevil.example", "www.example.com", "redirect").isEmpty());
        assertTrue(RedirectTargets.returnPath("https://www.example.com/login", "www.example.com", "redirect").isEmpty());
        assertTrue(RedirectTargets.returnPath("https://www.example.com/login?redirect=/x", "www.example.com", " ").isEmpty());
        assertTrue(RedirectTargets.returnPath("https://www.example.com/login?redirect", "www.example.com", "redirect").isEmpty());
    }

    @Test
    void theServletContextPathIsSwappedForThePlatformsOne() {
        // Verifies the OSGi servlet trap: the rewriter prefixes /modules, which a page link must not carry.
        assertEquals("/sites/x/home.html", RedirectTargets.swapContextPath("/modules/sites/x/home.html", "/modules", ""));
        assertEquals("/jahia/sites/x/home.html", RedirectTargets.swapContextPath("/modules/sites/x/home.html", "/modules", "/jahia"));
        assertEquals("/sites/x/home.html", RedirectTargets.swapContextPath("/sites/x/home.html", "/modules", ""));
        assertEquals("/a", RedirectTargets.swapContextPath("/a", "", ""));
        assertEquals(null, RedirectTargets.swapContextPath(null, "/modules", ""));
    }
}
