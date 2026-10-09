package org.jahia.modules.formidable.engine.imports;

import org.jahia.services.content.JCRNodeWrapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/** What the start of an import refuses in the choices of the review. */
class ChoicesCheckTest {

    private static final String FORM = "f0000000-0000-0000-0000-000000000001";
    private static final String REPORT = "{\"forms\":[{\"sourceName\":\"survey\",\"fields\":[{\"name\":\"your-name\"},"
            + "{\"name\":\"email\"},{\"name\":\"your-email\"}]}]}";
    private static final ChoicesCheck.FormLookup SITE_FORMS = id -> FORM.equals(id) ? mock(JCRNodeWrapper.class) : null;

    @Test
    void anAttachmentToAFormOfTheSiteWithAMappingOfItsFieldsPasses() {
        Map<String, FormChoice> choices = Map.of("survey", FormChoice.attachTo(FORM, Map.of("your-name", "name", "email", "mail", "your-email", "email")));

        assertDoesNotThrow(() -> ChoicesCheck.check(choices, REPORT, SITE_FORMS));
        assertDoesNotThrow(() -> ChoicesCheck.check(Map.of("survey", FormChoice.CREATE), null, id -> null));
    }

    @Test
    void anAttachmentToAnUnknownFormIsRefused() {
        Map<String, FormChoice> choices = Map.of("survey", FormChoice.attachTo("gone", Map.of()));

        ImportJobs.RefusedException refused = assertThrows(ImportJobs.RefusedException.class, () -> ChoicesCheck.check(choices, REPORT, SITE_FORMS));

        assertTrue(refused.getMessage().contains("survey"));
    }

    @Test
    void aMappingOfAFieldTheSourceDoesNotHoldIsRefused() {
        Map<String, FormChoice> choices = Map.of("survey", FormChoice.attachTo(FORM, Map.of("your-phone", "phone")));

        ImportJobs.RefusedException refused = assertThrows(ImportJobs.RefusedException.class, () -> ChoicesCheck.check(choices, REPORT, SITE_FORMS));

        assertTrue(refused.getMessage().contains("your-phone"));
    }

    @Test
    void aFieldSentToTheNameAnotherFieldKeepsIsRefused() {
        Map<String, FormChoice> choices = Map.of("survey", FormChoice.attachTo(FORM, Map.of("your-email", "email")));

        ImportJobs.RefusedException refused = assertThrows(ImportJobs.RefusedException.class, () -> ChoicesCheck.check(choices, REPORT, SITE_FORMS));

        assertTrue(refused.getMessage().contains("email"));
    }
}
