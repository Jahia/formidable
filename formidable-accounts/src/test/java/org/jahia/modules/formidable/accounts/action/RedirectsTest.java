package org.jahia.modules.formidable.accounts.action;

import org.jahia.modules.formidable.accounts.TestSupport;
import org.jahia.modules.formidable.engine.api.FormActionContext;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRPropertyWrapper;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RedirectsTest {

    private static final String PAGE_PATH = "/sites/mysite/members.html";

    private static JCRNodeWrapper action(Map<String, String> strings, boolean withPage) throws Exception {
        JCRNodeWrapper action = TestSupport.actionNode(strings);
        if (withPage) {
            JCRPropertyWrapper reference = mock(JCRPropertyWrapper.class);
            when(reference.getNode()).thenReturn(mock(JCRNodeWrapper.class));
            when(action.hasProperty("redirectPage")).thenReturn(true);
            when(action.getProperty("redirectPage")).thenReturn(reference);
        }
        return action;
    }

    private static FormActionContext context(JCRNodeWrapper action, String referer) {
        return TestSupport.context(action, TestSupport.form(true, true), TestSupport.request("203.0.113.7", referer), Map.of(), Map.of());
    }

    @Test
    void theReturnParameterOfThePageWinsOverTheChosenPageForASignedInVisitor() throws Exception {
        // Verifies the order: where the visitor was going, then the contributor's page; both same-origin paths.
        Redirects redirects = new Redirects((page, req, resp) -> Optional.of(PAGE_PATH));
        FormActionContext context = context(action(Map.of("redirectParameter", "redirect"), true), "https://www.example.com/login?redirect=%2Faccount%2Forders");

        assertEquals(Optional.of("/account/orders"), redirects.resolve(context, true));
    }

    @Test
    void aReturnParameterNamingAnotherSiteIsIgnoredAndTheChosenPageIsUsed() throws Exception {
        // Verifies the open-redirect rule: the visitor controls the parameter, never the destination's host.
        Redirects redirects = new Redirects((page, req, resp) -> Optional.of(PAGE_PATH));
        for (String hostile : new String[]{"https%3A%2F%2Fevil.example", "%2F%2Fevil.example", "%5C%5Cevil.example"}) {
            FormActionContext context = context(action(Map.of("redirectParameter", "redirect"), true), "https://www.example.com/login?redirect=" + hostile);
            assertEquals(Optional.of(PAGE_PATH), redirects.resolve(context, true), hostile);
        }
    }

    @Test
    void aRefererOfAnotherHostGivesNoReturnPath() throws Exception {
        // Verifies the Referer is held to this server: a cross-site Referer is not a source of destinations.
        Redirects redirects = new Redirects((page, req, resp) -> Optional.empty());
        FormActionContext context = context(action(Map.of("redirectParameter", "redirect"), false), "https://evil.example/login?redirect=/account");

        assertTrue(redirects.resolve(context, true).isEmpty());
    }

    @Test
    void theReturnParameterIsNotUsedWhenTheVisitorIsNotSignedIn() throws Exception {
        // Verifies a visitor awaiting activation is not sent to a place that needs a session.
        Redirects redirects = new Redirects((page, req, resp) -> Optional.of(PAGE_PATH));
        FormActionContext context = context(action(Map.of("redirectParameter", "redirect"), true), "https://www.example.com/login?redirect=/account");

        assertEquals(Optional.of(PAGE_PATH), redirects.resolve(context, false));
    }

    @Test
    void withNeitherPageNorParameterThereIsNoRedirectAndAFailingPageIsNoRedirectEither() throws Exception {
        // Verifies the degradations: unset, unresolvable page, unreadable property.
        Redirects redirects = new Redirects((page, req, resp) -> Optional.empty());

        assertTrue(redirects.resolve(context(action(Map.of(), false), null), true).isEmpty());
        assertTrue(redirects.resolve(context(action(Map.of(), true), null), true).isEmpty());

        JCRNodeWrapper broken = mock(JCRNodeWrapper.class);
        when(broken.hasProperty("redirectPage")).thenThrow(new IllegalStateException("down"));
        assertTrue(redirects.page(context(action(Map.of(), false), null), broken, "redirectPage").isEmpty());
    }
}
