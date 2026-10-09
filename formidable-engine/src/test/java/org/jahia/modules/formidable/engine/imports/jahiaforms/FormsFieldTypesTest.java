package org.jahia.modules.formidable.engine.imports.jahiaforms;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class FormsFieldTypesTest {

    private static FormsField of(String kind) {
        return new FormsField("f", "uuid", "fcnt:" + kind + "Definition", Map.of(), null, Map.of(), List.of(), false, false);
    }

    @Test
    void thePlainTypesMapToTheirEquivalent() {
        assertEquals(FormsFieldTypes.INPUT_TEXT, FormsFieldTypes.of(of("input"), t -> true).nodeType());
        assertEquals(FormsFieldTypes.INPUT_EMAIL, FormsFieldTypes.of(of("email"), t -> true).nodeType());
        assertEquals(FormsFieldTypes.TEXTAREA, FormsFieldTypes.of(of("textArea"), t -> true).nodeType());
        assertEquals(FormsFieldTypes.SELECT, FormsFieldTypes.of(of("selectMultiple"), t -> true).nodeType());
        assertEquals(FormsFieldTypes.CHECKBOX, FormsFieldTypes.of(of("multipleCheckBoxesInline"), t -> true).nodeType());
        assertEquals(FormsFieldTypes.INPUT_DATE, FormsFieldTypes.of(of("simpleDate"), t -> true).nodeType());
        assertNull(FormsFieldTypes.of(of("input"), t -> true).note());
    }

    @Test
    void theExtendedTypesAreUsedWhenRegisteredElseTheElementsFallbackIsNoted() {
        Set<String> registered = Set.of(FormsFieldTypes.SWITCH, FormsFieldTypes.RATING, FormsFieldTypes.CONSENT);
        assertEquals(FormsFieldTypes.SWITCH, FormsFieldTypes.of(of("switch"), registered::contains).nodeType());
        assertEquals(FormsFieldTypes.CONSENT, FormsFieldTypes.of(of("acceptTermCheckbox"), registered::contains).nodeType());

        FormsFieldTypes.Mapping fallback = FormsFieldTypes.of(of("rating"), t -> false);
        assertEquals(FormsFieldTypes.INPUT_NUMBER, fallback.nodeType());
        assertNotNull(fallback.note());
    }

    @Test
    void aKindWithoutEquivalentGetsTheNearestTypeAndANote() {
        FormsFieldTypes.Mapping phone = FormsFieldTypes.of(of("phone"), t -> true);
        assertEquals(FormsFieldTypes.INPUT_TEXT, phone.nodeType());
        assertNotNull(phone.note());
        assertEquals(FormsFieldTypes.TEXTAREA, FormsFieldTypes.of(of("matrixRadios"), t -> true).nodeType());
        assertEquals(FormsFieldTypes.INPUT_TEXT, FormsFieldTypes.of(of("somethingNew"), t -> true).nodeType());
    }

    @Test
    void passwordsContentDisplaysButtonsAndFieldsetMarksAreNoFields() {
        assertNull(FormsFieldTypes.of(of("password"), t -> true));
        assertNull(FormsFieldTypes.of(of("contentDisplay"), t -> true));
        assertNull(FormsFieldTypes.of(of("buttonTriple"), t -> true));
        assertNull(FormsFieldTypes.of(of("fieldsetStart"), t -> true));
    }
}
