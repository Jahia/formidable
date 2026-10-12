package org.jahia.modules.formidable.engine.imports;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The parts of the attachment that need no repository: the request, the mapping checks and their composition. */
class ResultsAttachmentTest {

    @Test
    void aRequestReadsItsJsonAndDropsTheNamesThatDoNotChange() {
        ResultsAttachment.Request request = ResultsAttachment.Request.fromJson(
                "{\"entry\":\"e1\",\"source\":\"s1\",\"form\":\"f1\",\"mapping\":{\"firstname\":\"firstname\",\"lastname\":\"surname\",\"x\":\"\"}}");

        assertEquals("e1", request.entryId());
        assertEquals("s1", request.sourceFormId());
        assertEquals("f1", request.formId());
        assertEquals(Map.of("lastname", "surname"), request.mapping());
        assertEquals(request, ResultsAttachment.Request.fromJson(request.toJson()));
    }

    @Test
    void aRequestNamesTheEntryTheSourceAndTheForm() {
        assertThrows(IllegalArgumentException.class, () -> ResultsAttachment.Request.fromJson("{\"source\":\"s1\",\"form\":\"f1\"}"));
        assertThrows(IllegalArgumentException.class, () -> ResultsAttachment.Request.fromJson("{\"entry\":\"e1\",\"form\":\"f1\"}"));
        assertThrows(IllegalArgumentException.class, () -> ResultsAttachment.Request.fromJson("{\"entry\":\"e1\",\"source\":\"s1\"}"));
    }

    @Test
    void theMappingRefusesTwoFieldsLandingUnderOneName() {
        Set<String> names = Set.of("firstname", "lastname", "email");

        assertDoesNotThrow(() -> ResultsAttachment.checkMapping(Map.of("lastname", "surname"), names));
        ImportJobs.RefusedException twice = assertThrows(ImportJobs.RefusedException.class,
                () -> ResultsAttachment.checkMapping(Map.of("lastname", "name", "email", "name"), names));
        assertTrue(twice.getMessage().contains("name"));
        // a field sent to the name another field keeps collides with it
        ImportJobs.RefusedException kept = assertThrows(ImportJobs.RefusedException.class,
                () -> ResultsAttachment.checkMapping(Map.of("lastname", "firstname"), names));
        assertTrue(kept.getMessage().contains("firstname"));
    }

    @Test
    void theMappingKeptOnTheEntryGoesFromTheGeneratedNamesToTheCurrentOnes() {
        // the first attachment sent your-first-name to firstname; this one sends firstname on to name, and email to mail
        Map<String, String> earlier = new LinkedHashMap<>();
        earlier.put("your-first-name", "firstname");
        earlier.put("your-last-name", "lastname");
        Map<String, String> now = new LinkedHashMap<>();
        now.put("firstname", "name");
        now.put("your-email-address", "mail");

        Map<String, String> composed = ResultsAttachment.compose(earlier, now);

        assertEquals(Map.of("your-first-name", "name", "your-last-name", "lastname", "your-email-address", "mail"), composed);
        // a name sent back to its generated name leaves the mapping
        assertEquals(Map.of(), ResultsAttachment.compose(Map.of("a", "b"), Map.of("b", "a")));
        assertEquals(Map.of("a", "b"), ResultsAttachment.compose(Map.of(), Map.of("a", "b")));
    }
}
