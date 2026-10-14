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
    void aRefusalCarriesTheVisitorMessageAndTheStatusAndAnOrdinaryFailureCarriesNone() {
        // Verifies the two failure shapes: refusal() is for the visitor, every other constructor stays opaque.
        FormActionException refusal = FormActionException.refusal(422, "no account created", "Impossible de creer le compte.");
        assertEquals(422, refusal.getHttpStatus());
        assertEquals("Impossible de creer le compte.", refusal.getVisitorMessage());
        assertTrue(refusal.hasVisitorMessage());
        assertEquals("no account created", refusal.getMessage());

        assertFalse(FormActionException.serverError("boom").hasVisitorMessage());
        assertNull(FormActionException.badRequest("bad").getVisitorMessage());
        assertFalse(FormActionException.refusal(422, "x", "  ").hasVisitorMessage());
        assertEquals("cause", FormActionException.refusal(400, "x", "y", new RuntimeException("cause")).getCause().getMessage());
    }

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
    }

    @Test
    void theContextSnapshotsItsMapsAndReadsTheFirstNonBlankValue() {
        // Verifies the context cannot be changed after the pipeline built it, and the accessors skip blank answers.
        Map<String, List<String>> values = Map.of("email", new ArrayList<>(List.of("", "ada@example.com")));
        FormActionContext context = new FormActionContext(mock(JCRNodeWrapper.class), null, mock(HttpServletRequest.class), null, null,
                Locale.ENGLISH, "mysite", values, Map.of("password", List.of("s3cret")), null);

        assertEquals("ada@example.com", context.parameter("email"));
        assertNull(context.parameter("missing"));
        assertEquals("s3cret", context.sensitiveParameter("password"));
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
        assertFalse(action.consumesSensitiveValues());
        assertThrows(UnsupportedOperationException.class, () -> action.execute(null, null, null, Map.of(), List.of()));
    }

    @Test
    void visitorMessagesAreResolvedPerLocaleWithoutServerFallback() {
        // Verifies the translation helper: the locale's bundle wins, the base bundle answers an unknown locale (never
        // the server's own), and a missing key or bundle falls back to the supplied text.
        ClassLoader loader = FormActionApiTest.class.getClassLoader();
        assertEquals("Hello", VisitorMessages.resolve(loader, "visitor-messages-test", "greeting", Locale.ENGLISH, "?"));
        assertEquals("Bonjour", VisitorMessages.resolve(loader, "visitor-messages-test", "greeting", Locale.FRENCH, "?"));
        assertEquals("Hello", VisitorMessages.resolve(loader, "visitor-messages-test", "greeting", Locale.GERMAN, "?"));
        assertEquals("Hello", VisitorMessages.resolve(loader, "visitor-messages-test", "greeting", null, "?"));
        assertEquals("fallback", VisitorMessages.resolve(loader, "visitor-messages-test", "absent", Locale.ENGLISH, "fallback"));
        assertEquals("fallback", VisitorMessages.resolve(loader, "no-such-bundle", "greeting", Locale.ENGLISH, "fallback"));
    }
}
