package org.jahia.modules.formidable.engine.actions.form.redirect;

import org.jahia.modules.formidable.engine.api.FormActionContext;
import org.jahia.modules.formidable.engine.api.FormActionResult;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRPropertyWrapper;
import org.jahia.services.content.decorator.JCRSiteNode;
import org.junit.jupiter.api.Test;

import javax.jcr.ItemNotFoundException;
import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RedirectFormActionTest {

    private static JCRPropertyWrapper string(String value) throws Exception {
        JCRPropertyWrapper property = mock(JCRPropertyWrapper.class);
        when(property.getString()).thenReturn(value);
        return property;
    }

    private static JCRNodeWrapper action(String targetType, String url, JCRNodeWrapper page) throws Exception {
        JCRNodeWrapper action = mock(JCRNodeWrapper.class);
        when(action.getPath()).thenReturn("/sites/mysite/contents/form/actions/redirect");
        NodeIterator noTranslation = translations();
        when(action.getI18Ns()).thenReturn(noTranslation);
        if (targetType != null) {
            JCRPropertyWrapper type = string(targetType);
            when(action.hasProperty("targetType")).thenReturn(true);
            when(action.getProperty("targetType")).thenReturn(type);
        }
        if (url != null) {
            JCRPropertyWrapper value = string(url);
            when(action.hasProperty("url")).thenReturn(true);
            when(action.getProperty("url")).thenReturn(value);
        }
        if (page != null) {
            JCRPropertyWrapper reference = mock(JCRPropertyWrapper.class);
            when(reference.getNode()).thenReturn(page);
            when(action.hasProperty("page")).thenReturn(true);
            when(action.getProperty("page")).thenReturn(reference);
        }
        return action;
    }

    /** The translation nodes of an action, in order, each holding its url when given one. */
    private static NodeIterator translations(String... urls) throws Exception {
        List<Node> nodes = new java.util.ArrayList<>();
        for (String url : urls) {
            nodes.add(translation(url));
        }
        java.util.Iterator<Node> iterator = nodes.iterator();
        NodeIterator translations = mock(NodeIterator.class);
        when(translations.hasNext()).thenAnswer(invocation -> iterator.hasNext());
        when(translations.nextNode()).thenAnswer(invocation -> iterator.next());
        return translations;
    }

    private static Node translation(String url) throws Exception {
        Node translation = mock(Node.class);
        if (url != null) {
            JCRPropertyWrapper value = string(url);
            when(translation.hasProperty("url")).thenReturn(true);
            when(translation.getProperty("url")).thenReturn(value);
        }
        return translation;
    }

    private static FormActionContext context(JCRNodeWrapper action) {
        return new FormActionContext(action, null, mock(HttpServletRequest.class), null, null, Locale.FRENCH, "mysite",
                Map.of(), Map.of(), List.of());
    }

    @Test
    void aUrlTargetIsARedirectToAUrlReadFromTheConfiguration() throws Exception {
        // Verifies the url target, trimmed: an http(s) URL is a redirect of the URL kind.
        for (String url : List.of("https://www.example.com/merci", "http://example.com")) {
            FormActionResult result = new RedirectFormAction().execute(context(action("url", " " + url + " ", null)));
            assertEquals(url, result.redirectUrl(), url);
            assertNull(result.redirectPath(), url);
        }
    }

    @Test
    void aPathOfTheSiteIsNoUrlTarget() throws Exception {
        // Verifies that a page of the site goes through the page target: a typed path, which would miss a move, the
        // visitor's language and the context path, is no redirect.
        for (String path : List.of("/fr/merci.html", "/sites/mysite/home.html", "/")) {
            assertTrue(new RedirectFormAction().execute(context(action("url", path, null))).isEmpty(), path);
        }
    }

    @Test
    void aPageOfASiteWithAnotherServerNameIsAUrlRedirect() throws Exception {
        // Verifies the page target when the platform prints an absolute URL: the redirect goes to that URL.
        JCRNodeWrapper page = mock(JCRNodeWrapper.class);
        RedirectFormAction action = new RedirectFormAction((node, req, resp) -> Optional.of("https://other.example.com/fr/merci"));

        FormActionResult result = action.execute(context(action("page", null, page)));

        assertEquals("https://other.example.com/fr/merci", result.redirectUrl());
    }

    @Test
    void aUrlTheBrowserMustNotFollowIsDroppedAndTheSubmissionStillAccepted() throws Exception {
        // Verifies the configuration side of the rule: no other scheme, no protocol-relative URL, no value without scheme.
        for (String unusable : List.of("javascript:alert(1)", "//evil.example", "www.example.com", "ftp://example.com/")) {
            FormActionResult result = new RedirectFormAction().execute(context(action("url", unusable, null)));
            assertTrue(result.isEmpty(), unusable);
        }
    }

    @Test
    void aPageTargetIsResolvedThroughThePageUrlRewriting() throws Exception {
        // Verifies the page target: the weak reference's node is handed to the URL resolver and its path is the redirect.
        JCRNodeWrapper page = mock(JCRNodeWrapper.class);
        RedirectFormAction action = new RedirectFormAction((node, req, resp) ->
                node == page ? Optional.of("/sites/mysite/merci.html") : Optional.empty());

        FormActionResult result = action.execute(context(action("page", null, page)));

        assertEquals("/sites/mysite/merci.html", result.redirectPath());
    }

    @Test
    void thePageIsTheDefaultTargetType() throws Exception {
        // Verifies the default of the CND: an action without targetType reads the page.
        JCRNodeWrapper page = mock(JCRNodeWrapper.class);
        RedirectFormAction action = new RedirectFormAction((node, req, resp) -> Optional.of("/p.html"));

        assertEquals("/p.html", action.execute(context(action(null, null, page))).redirectPath());
    }

    @Test
    void aMissingTargetOrAFailingReadIsNoRedirectAndNeverAFailure() throws Exception {
        // Verifies the action fails nothing: no page, no URL, an unreadable property and an unresolvable page all
        // accept the submission without a redirect.
        RedirectFormAction none = new RedirectFormAction((node, req, resp) -> Optional.empty());
        assertTrue(none.execute(context(action("page", null, null))).isEmpty());
        assertTrue(none.execute(context(action("url", null, null))).isEmpty());
        assertTrue(none.execute(context(action("page", null, mock(JCRNodeWrapper.class)))).isEmpty());

        JCRNodeWrapper broken = mock(JCRNodeWrapper.class);
        when(broken.hasProperty("targetType")).thenThrow(new RepositoryException("down"));
        assertTrue(none.execute(context(broken)).isEmpty());
    }

    @Test
    void aLanguageWithoutUrlFallsBackToTheSiteDefaultLanguage() throws Exception {
        // Verifies the fallback: the submission's language has no URL, the site's default language has one.
        JCRNodeWrapper action = action("url", null, null);
        JCRSiteNode site = mock(JCRSiteNode.class);
        when(site.getDefaultLanguage()).thenReturn("en");
        when(action.getResolveSite()).thenReturn(site);
        when(action.hasI18N(Locale.ENGLISH, false)).thenReturn(true);
        Node english = translation("https://www.example.com/thanks");
        when(action.getI18N(Locale.ENGLISH, false)).thenReturn(english);

        assertEquals("https://www.example.com/thanks", new RedirectFormAction().execute(context(action)).redirectUrl());
    }

    @Test
    void withoutUrlInTheDefaultLanguageTheFirstLanguageThatHasOneIsUsed() throws Exception {
        // Verifies the last step of the fallback, as Jahia Forms read its first translation: blank values are skipped.
        JCRNodeWrapper action = action("url", null, null);
        NodeIterator others = translations(null, "  ", "https://www.example.de/danke");
        when(action.getI18Ns()).thenReturn(others);

        assertEquals("https://www.example.de/danke", new RedirectFormAction().execute(context(action)).redirectUrl());
    }

    @Test
    void anUnpublishedOrDeletedPageIsNoRedirect() throws Exception {
        // Verifies the weak reference that no longer resolves in live: no redirect, and no failure.
        JCRNodeWrapper action = action("page", null, null);
        JCRPropertyWrapper reference = mock(JCRPropertyWrapper.class);
        when(reference.getNode()).thenThrow(new ItemNotFoundException("gone"));
        when(action.hasProperty("page")).thenReturn(true);
        when(action.getProperty("page")).thenReturn(reference);

        assertTrue(new RedirectFormAction((node, req, resp) -> Optional.of("/never.html")).execute(context(action)).isEmpty());
    }

    @Test
    void theActionHandlesItsNodeType() {
        assertEquals("fmdb:redirectAction", new RedirectFormAction().getNodeType());
    }
}
