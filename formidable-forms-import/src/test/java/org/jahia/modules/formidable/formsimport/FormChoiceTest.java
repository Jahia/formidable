package org.jahia.modules.formidable.formsimport;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The choices of a review as the dialog sends them and the job keeps them, and what is refused. */
class FormChoiceTest {

    @Test
    void theChoicesSurviveTheRoundTripThroughJson() {
        Map<String, FormChoice> choices = Map.of(
                "contact-us", FormChoice.CREATE,
                "survey", FormChoice.attachTo("f0000000-0000-0000-0000-000000000001", Map.of("your-name", "name")),
                "news", FormChoice.RESULTS_ONLY);

        Map<String, FormChoice> read = FormChoice.fromJson(FormChoice.toJson(choices));

        assertEquals(choices, read);
        assertEquals("name", read.get("survey").nameOf("your-name"));
        assertEquals("your-email", read.get("survey").nameOf("your-email"));
    }

    @Test
    void aPlainNameOrAnObjectNamesTheChoice() {
        Map<String, FormChoice> read = FormChoice.fromJson("{\"a\":\"resultsOnly\",\"b\":{\"choice\":\"create\"},"
                + "\"c\":{\"choice\":\"attach\",\"form\":\"x\",\"mapping\":{\"k\":\"\",\"m\":\"n\"}}}");

        assertEquals(FormChoice.RESULTS_ONLY, read.get("a"));
        assertEquals(FormChoice.CREATE, read.get("b"));
        // an empty target keeps the name: it is no mapping
        assertEquals(FormChoice.attachTo("x", Map.of("m", "n")), read.get("c"));
        assertTrue(FormChoice.fromJson(null).isEmpty());
        assertTrue(FormChoice.fromJson(" ").isEmpty());
    }

    @Test
    void anAttachmentNamesItsFormAndSendsTwoFieldsToDifferentOnes() {
        assertThrows(IllegalArgumentException.class, () -> FormChoice.fromJson("{\"a\":\"attach\"}"));
        assertThrows(IllegalArgumentException.class, () -> FormChoice.fromJson("{\"a\":{\"choice\":\"attach\"}}"));
        Map<String, String> twice = Map.of("a", "name", "b", "name");
        assertThrows(IllegalArgumentException.class, () -> FormChoice.attachTo("x", twice));
        assertThrows(IllegalArgumentException.class, () -> FormChoice.fromJson("{\"a\":\"keep\"}"));
        assertThrows(IllegalArgumentException.class, () -> FormChoice.fromJson("{\"a\":3}"));
    }

    @Test
    void skipNamesAFormLeftOutOfTheRun() {
        Map<String, FormChoice> read = FormChoice.fromJson("{\"a\":\"skip\",\"b\":{\"choice\":\"skip\"}}");

        assertEquals(FormChoice.SKIP, read.get("a"));
        assertEquals(FormChoice.SKIP, read.get("b"));
        assertEquals(read, FormChoice.fromJson(FormChoice.toJson(read)));
    }
}
