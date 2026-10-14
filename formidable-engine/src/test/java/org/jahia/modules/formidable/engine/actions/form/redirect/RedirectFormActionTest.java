package org.jahia.modules.formidable.engine.actions.form.redirect;

import org.jahia.modules.formidable.engine.api.FormActionContext;
import org.jahia.modules.formidable.engine.api.FormActionResult;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRPropertyWrapper;
import org.junit.jupiter.api.Test;

import javax.jcr.RepositoryException;
import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RedirectFormActionTest {

    private static JCRPropertyWrapper string(String value) throws Exception {
        JCRPropertyWrapper property = mock(JCRPropertyWrapper.class);
        when(property.getString()).thenReturn(value);
        return property;
    }

    private static JCRNodeWrapper action(String targetType, String path, JCRNodeWrapper page) throws Exception {
        JCRNodeWrapper action = mock(JCRNodeWrapper.class);
        if (targetType != null) {
            JCRPropertyWrapper type = string(targetType);
            when(action.hasProperty("targetType")).thenReturn(true);
            when(action.getProperty("targetType")).thenReturn(type);
        }
        if (path != null) {
            JCRPropertyWrapper value = string(path);
            when(action.hasProperty("path")).thenReturn(true);
            when(action.getProperty("path")).thenReturn(value);
        }
        if (page != null) {
            JCRPropertyWrapper reference = mock(JCRPropertyWrapper.class);
            when(reference.getNode()).thenReturn(page);
            when(action.hasProperty("page")).thenReturn(true);
            when(action.getProperty("page")).thenReturn(reference);
        }
        return action;
    }

    private static FormActionContext context(JCRNodeWrapper action) {
        return new FormActionContext(action, null, mock(HttpServletRequest.class), null, null, Locale.FRENCH, "mysite",
                Map.of(), Map.of(), List.of());
    }

    @Test
    void aPathTargetIsSentWhenItIsASameOriginPath() throws Exception {
        // Verifies the path target: a safe relative path is the redirect, untouched.
        FormActionResult result = new RedirectFormAction().execute(context(action("path", "/fr/merci.html", null)));

        assertEquals("/fr/merci.html", result.redirectPath());
    }

    @Test
    void aPathTargetNamingAnotherSiteIsDroppedAndTheSubmissionStillAccepted() throws Exception {
        // Verifies the open-redirect rule at the configuration side too: a contributor cannot send visitors elsewhere.
        for (String hostile : List.of("https://evil.example/", "//evil.example", "javascript:alert(1)")) {
            FormActionResult result = new RedirectFormAction().execute(context(action("path", hostile, null)));
            assertTrue(result.isEmpty(), hostile);
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
        // Verifies the action fails nothing: no page, no path, an unreadable property and an unresolvable URL all
        // accept the submission without a redirect.
        RedirectFormAction none = new RedirectFormAction((node, req, resp) -> Optional.empty());
        assertTrue(none.execute(context(action("page", null, null))).isEmpty());
        assertTrue(none.execute(context(action("path", null, null))).isEmpty());
        assertTrue(none.execute(context(action("page", null, mock(JCRNodeWrapper.class)))).isEmpty());

        JCRNodeWrapper broken = mock(JCRNodeWrapper.class);
        when(broken.hasProperty("targetType")).thenThrow(new RepositoryException("down"));
        assertTrue(none.execute(context(broken)).isEmpty());
    }

    @Test
    void theActionHandlesItsNodeType() {
        assertEquals("fmdb:redirectAction", new RedirectFormAction().getNodeType());
    }
}
