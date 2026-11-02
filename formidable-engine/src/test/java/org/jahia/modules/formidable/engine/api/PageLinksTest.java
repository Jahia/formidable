package org.jahia.modules.formidable.engine.api;

import org.jahia.services.content.JCRNodeWrapper;
import org.junit.jupiter.api.Test;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PageLinksTest {

    private static JCRNodeWrapper page() {
        JCRNodeWrapper page = mock(JCRNodeWrapper.class);
        when(page.getUrl()).thenReturn("/cms/render/live/fr/sites/mysite/home.html");
        when(page.getPath()).thenReturn("/sites/mysite/home");
        return page;
    }

    private static HttpServletRequest request() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getContextPath()).thenReturn("/modules");
        when(request.getServerName()).thenReturn("www.example.com");
        return request;
    }

    @Test
    void theRewritingSeesThePlatformsContextPathNotTheServlets() {
        // Verifies the OSGi servlet trap: the platform's rewriting starts the URL with the request's context path and
        // looks a vanity URL up only when it equals the link's, so it must never read the servlet's /modules.
        AtomicReference<String> seen = new AtomicReference<>();
        Optional<String> url = PageLinks.urlOf(page(), request(), null,
                (link, req, resp) -> {
                    seen.set(req.getContextPath());
                    return req.getContextPath() + "/thanks";
                }, "/jahia");

        assertEquals("/jahia", seen.get());
        assertEquals(Optional.of("/jahia/thanks"), url);
    }

    @Test
    void aPageOfAnotherServerNameCarriesThePlatformsContextPathAfterTheHost() {
        // Verifies the rule that puts another site's server name in front of the rewritten URL: the context path that
        // follows the host is the platform's, never /modules.
        Optional<String> url = PageLinks.urlOf(page(), request(), null,
                (link, req, resp) -> "http://serverb:8080" + req.getContextPath() + "/home/thank-you.html", "");

        assertEquals(Optional.of("http://serverb:8080/home/thank-you.html"), url);
    }

    @Test
    void anAbsoluteVanityUrlOnThisServerIsReducedToItsPath() {
        // Verifies a site with its own server name: the rewriter answers an absolute URL of this host, kept as a path.
        Optional<String> url = PageLinks.urlOf(page(), request(), null,
                (link, req, resp) -> "https://www.example.com/fr/accueil", "");

        assertEquals(Optional.of("/fr/accueil"), url);
    }

    @Test
    void aPageOfASiteWithAnotherServerNameKeepsItsAbsoluteUrl() {
        // Verifies a page picked on another site of the platform: the platform prints its absolute URL, which is kept.
        Optional<String> url = PageLinks.urlOf(page(), request(), null,
                (link, req, resp) -> "https://other.example.com/fr/accueil", "");

        assertEquals(Optional.of("https://other.example.com/fr/accueil"), url);
    }

    @Test
    void aPageOfASiteWithANonAsciiServerNameKeepsItsAbsoluteUrl() {
        // Verifies the page of a site whose server name is not ASCII, which the platform may print as typed.
        Optional<String> url = PageLinks.urlOf(page(), request(), null,
                (link, req, resp) -> "https://b\u00fccher.example/fr/accueil", "");

        assertEquals(Optional.of("https://b\u00fccher.example/fr/accueil"), url);
    }

    @Test
    void theRewritingAddsNoSessionIdToTheUrl() {
        // Verifies the response the rewriting sees: a container that tracks sessions in URLs adds ;jsessionid=... in
        // encodeURL, and the redirect must not carry it.
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(response.encodeURL(anyString())).thenAnswer(invocation -> invocation.getArgument(0) + ";jsessionid=ABC");

        Optional<String> url = PageLinks.urlOf(page(), request(), response,
                (link, req, resp) -> resp.encodeURL("/sites/mysite/home.html"), "");

        assertEquals(Optional.of("/sites/mysite/home.html"), url);
    }

    @Test
    void anUnusableUrlOrAFailingRewriterGivesNoTarget() {
        assertTrue(PageLinks.urlOf(page(), request(), null, (link, req, resp) -> "javascript:alert(1)", "").isEmpty());
        assertTrue(PageLinks.urlOf(page(), request(), null, (link, req, resp) -> { throw new IllegalStateException("no service"); }, "").isEmpty());
    }
}
