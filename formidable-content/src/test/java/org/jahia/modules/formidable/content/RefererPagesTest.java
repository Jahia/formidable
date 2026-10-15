package org.jahia.modules.formidable.content;

import org.jahia.modules.formidable.content.page.RefererPages;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RefererPagesTest {

    private static final String HOST = "www.example.com";
    private static final String SITE = "orvane";

    private static List<String> from(String referer) {
        return RefererPages.candidatePaths(referer, HOST, SITE);
    }

    @Test
    void aBriefPageOfTheSite() {
        assertEquals(List.of("/sites/orvane/contents/briefs/summer-launch"),
                from("https://www.example.com/sites/orvane/contents/briefs/summer-launch.html"));
    }

    @Test
    void aLocalizedUrlKeepsTheSameNode() {
        assertEquals(List.of("/sites/orvane/contents/briefs/summer-launch"),
                from("https://www.example.com/fr/sites/orvane/contents/briefs/summer-launch.html"));
        assertEquals(List.of("/sites/orvane/home/about"), from("https://www.example.com/pt_PT/sites/orvane/home/about.html"));
        assertEquals(List.of("/sites/orvane/home/about"), from("https://www.example.com/en-GB/sites/orvane/home/about.html"));
    }

    @Test
    void theRenderPrefixOfJahiaIsDropped() {
        assertEquals(List.of("/sites/orvane/home/about"), from("https://www.example.com/cms/render/live/en/sites/orvane/home/about.html"));
        assertEquals(List.of("/sites/orvane/home/about"), from("https://www.example.com/cms/render/default/en/sites/orvane/home/about.html"));
    }

    @Test
    void theQueryAndTheFragmentAreDropped() {
        assertEquals(List.of("/sites/orvane/home/about"), from("https://www.example.com/sites/orvane/home/about.html?x=1&y=/sites/other/home#top"));
        assertEquals(List.of("/sites/orvane/home/about"), from("https://www.example.com/sites/orvane/home/about.html#/sites/other/home"));
    }

    @Test
    void aViewNameBeforeTheExtensionGivesASecondCandidate() {
        assertEquals(List.of("/sites/orvane/home/about.print", "/sites/orvane/home/about"),
                from("https://www.example.com/sites/orvane/home/about.print.html"));
    }

    @Test
    void aNameWithoutExtensionIsTakenAsIs() {
        assertEquals(List.of("/sites/orvane/home/about"), from("https://www.example.com/sites/orvane/home/about"));
    }

    @Test
    void percentEncodedNamesAreDecoded() {
        assertEquals(List.of("/sites/orvane/contents/briefs/caf\u00e9 plan"),
                from("https://www.example.com/sites/orvane/contents/briefs/caf%C3%A9%20plan.html"));
        assertEquals(List.of("/sites/orvane/contents/a+b"), from("https://www.example.com/sites/orvane/contents/a+b.html"));
    }

    @Test
    void anotherSiteIsRefused() {
        assertTrue(from("https://www.example.com/sites/rival/home/about.html").isEmpty());
        assertTrue(from("https://www.example.com/fr/sites/rival/home/about.html").isEmpty());
        assertTrue(from("https://www.example.com/sites/orvane2/home/about.html").isEmpty());
    }

    @Test
    void anotherHostIsRefused() {
        assertTrue(from("https://evil.example.org/sites/orvane/home/about.html").isEmpty());
        assertTrue(from("https://www.example.com.evil.org/sites/orvane/home/about.html").isEmpty());
    }

    @Test
    void nothingOutsideTheSiteSubtreeIsProposed() {
        for (String path : List.of("/", "/home.html", "/users/root", "/sites/orvane", "/sites/orvane/", "/sites/orvane/home",
                "/modules/graphql", "/jahia/jcontent/orvane/en/pages/home", "/cms/edit/default/en/sites/orvane/home/about.html",
                "/cms/render/live/en/users/root.html", "/files/live/sites/orvane/files/x.pdf")) {
            String url = "https://www.example.com" + path;
            if (path.equals("/sites/orvane/home")) {
                assertEquals(List.of("/sites/orvane/home"), from(url), "a page without extension is fine");
            } else {
                assertTrue(from(url).isEmpty(), path);
            }
        }
    }

    @Test
    void aVanityUrlIsNotGuessed() {
        assertTrue(from("https://www.example.com/summer-launch").isEmpty());
        assertTrue(from("https://www.example.com/fr/summer-launch.html").isEmpty());
    }

    @Test
    void traversalAndEncodedSeparatorsAreRefused() {
        for (String path : List.of("/sites/orvane/../rival/home.html", "/sites/orvane/home/%2e%2e/x.html", "/sites/orvane/home/..%2Fx.html",
                "/sites/orvane/home/a%2Fb.html", "/sites/orvane//home.html", "/sites/orvane/home/./x.html",
                "/sites/orvane/home/a%3Ab.html", "/sites/orvane/home/a%00b.html", "/sites/orvane/home/%ZZ.html", "/sites/orvane/home/a%5Cb.html")) {
            assertTrue(from("https://www.example.com" + path).isEmpty(), path);
        }
    }

    @Test
    void aHeaderThatIsNotAnUrlProposesNothing() {
        assertTrue(from(null).isEmpty());
        assertTrue(from("").isEmpty());
        assertTrue(from("not a url").isEmpty());
        assertTrue(from("javascript:alert(1)").isEmpty());
        assertTrue(RefererPages.candidatePaths("https://www.example.com/sites/orvane/home/a.html", HOST, null).isEmpty());
        assertTrue(RefererPages.candidatePaths("https://www.example.com/sites/orvane/home/a.html", HOST, " ").isEmpty());
    }

    @Test
    void aSegmentThatLooksLikeALanguageButIsNotOneIsNotAPrefix() {
        assertTrue(from("https://www.example.com/english/sites/orvane/home/a.html").isEmpty());
        assertTrue(from("https://www.example.com/FR/sites/orvane/home/a.html").isEmpty());
    }
}
