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
        // Verifies the server and the island agree: a byte-order mark, a zero-width space, the non-breaking spaces, a
        // C1 control and a tag character outside the BMP (U+E0001, two surrogates in a Java string) would pass a check
        // of ASCII whitespace alone, and the island refuses them, so a path the server sent would be silently ignored.
        for (String candidate : List.of("/a\uFEFFb", "/a\u200Bb", "/a\u00A0b", "/a\u2007b", "/a\u202Fb", "/a\u2028b", "/a\u0085b",
                "/a\uDB40\uDC01b")) {
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
        // A non-ASCII host and a host with an underscore are hosts to the browser, though java.net.URI reads neither as one.
        for (String url : List.of("https://www.example.com", "http://www.example.com/merci.html", "HTTPS://WWW.EXAMPLE.COM/a?b=c#d",
                "https://www.example.com:8443/fr/merci", "https://b\u00FCcher.example/", "https://b\u00FCcher.example:8443/a",
                "https://my_host.example.com/")) {
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
                "https://www.example.com/\\evil", "https://user@b\u00FCcher.example/", "https://user@my_host.example.com/", "", "   ")) {
            assertTrue(RedirectTargets.httpUrl(candidate).isEmpty(), candidate);
        }
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
