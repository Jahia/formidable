package org.jahia.modules.formidable.engine.api;

import org.jahia.services.content.JCRNodeWrapper;
import org.junit.jupiter.api.Test;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/** The small value types of the contextual-action SPI. */
class FormActionApiTest {

    @Test
    void aResultIsBuiltFromItsParts() {
        // Verifies the fluent shape and the emptiness test the pipeline relies on.
        assertTrue(FormActionResult.none().isEmpty());
        assertEquals("/a", FormActionResult.redirect("/a").redirect().orElseThrow());
        assertEquals("hi", FormActionResult.message("hi").message());
        FormActionResult both = FormActionResult.redirect("/a").withMessage("hi");
        assertEquals("/a", both.redirectPath());
        assertEquals("hi", both.message());
        assertEquals("/b", both.withRedirect("/b").redirectPath());
        assertFalse(both.isEmpty());
        assertTrue(FormActionResult.none().redirect().isEmpty());
        FormActionResult toUrl = FormActionResult.message("hi").withRedirectToUrl("https://www.example.com/");
        assertEquals("https://www.example.com/", toUrl.redirectUrl());
        assertEquals("https://www.example.com/", toUrl.redirect().orElseThrow());
        assertEquals("hi", toUrl.message());
        assertNull(toUrl.withRedirect("/a").redirectUrl());
        assertEquals("/a", toUrl.withRedirect("/a").redirectPath());
    }

    @Test
    void theContextSnapshotsItsMapsAndReadsTheFirstNonBlankValue() {
        // Verifies the context cannot be changed after the pipeline built it, and the accessors skip blank answers.
        Map<String, List<String>> values = Map.of("email", new ArrayList<>(List.of("", "ada@example.com")));
        FormActionContext context = new FormActionContext(mock(JCRNodeWrapper.class), null, mock(HttpServletRequest.class), null, null,
                Locale.ENGLISH, "mysite", values, Map.of("password", List.of("s3cret")), null);

        assertEquals("ada@example.com", context.parameter("email"));
        assertNull(context.parameter("missing"));
        assertEquals(List.of("s3cret"), context.sensitiveParameters().get("password"));
        assertThrows(UnsupportedOperationException.class, () -> context.parameters().put("x", List.of()));
        assertThrows(UnsupportedOperationException.class, () -> context.parameters().get("email").add("y"));
        assertTrue(context.files().isEmpty());
    }

    @Test
    void theLegacyMethodOfAContextualActionIsNeverUsable() {
        // Verifies a contextual action fails loudly if some caller treats it as a plain action.
        ContextualFormAction action = new ContextualFormAction() {
            @Override public String getNodeType() { return "t:x"; }
            @Override public FormActionResult execute(FormActionContext context) { return FormActionResult.none(); }
        };
        assertThrows(UnsupportedOperationException.class, () -> action.execute(null, null, null, Map.of(), List.of()));
    }
}
