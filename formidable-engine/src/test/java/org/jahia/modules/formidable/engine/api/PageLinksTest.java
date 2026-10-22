package org.jahia.modules.formidable.engine.api;

import org.jahia.services.content.JCRNodeWrapper;
import org.junit.jupiter.api.Test;

import javax.servlet.http.HttpServletRequest;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
    void theRewrittenUrlLosesTheServletContextPathAndKeepsThePlatformsOne() {
        // Verifies the OSGi servlet trap: /modules is the servlet's context path, never part of a page link.
        Optional<String> path = PageLinks.pathOf(page(), request(), null,
                (url, req, resp) -> "/modules/sites/mysite/home.html", "");

        assertEquals(Optional.of("/sites/mysite/home.html"), path);
    }

    @Test
    void anAbsoluteVanityUrlOnThisServerIsReducedToItsPath() {
        // Verifies a site with its own server name: the rewriter answers an absolute URL of this host, kept as a path.
        Optional<String> path = PageLinks.pathOf(page(), request(), null,
                (url, req, resp) -> "https://www.example.com/fr/accueil", "");

        assertEquals(Optional.of("/fr/accueil"), path);
    }

    @Test
    void aUrlOfAnotherHostOrAFailingRewriterGivesNoTarget() {
        assertTrue(PageLinks.pathOf(page(), request(), null, (url, req, resp) -> "https://evil.example/x", "").isEmpty());
        assertTrue(PageLinks.pathOf(page(), request(), null, (url, req, resp) -> { throw new IllegalStateException("no service"); }, "").isEmpty());
    }
}
