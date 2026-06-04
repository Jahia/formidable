package org.jahia.modules.formidable.engine.actions;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FormDataParserMimeAllowlistTest {

    @Test
    void acceptsPlainTextAsCsvAliasForCsvFilenamesWhenCsvIsAllowed() {
        // Verifies the CSV compatibility rule: content-only MIME detection may classify
        // simple CSV payloads as text/plain even when the field explicitly allows text/csv.
        // Expected outcome: text/plain is accepted as an alias only for *.csv filenames.
        assertTrue(FormDataParser.isMimeAllowed("text/plain", "contacts.csv", Set.of("text/csv")));
    }

    @Test
    void rejectsPlainTextAliasForNonCsvFilenamesEvenWhenCsvIsAllowed() {
        // Verifies the filename guard on the CSV alias: allowing text/csv must not broaden
        // acceptance to arbitrary plain-text uploads such as .txt files.
        // Expected outcome: text/plain remains rejected when the filename is not *.csv.
        assertFalse(FormDataParser.isMimeAllowed("text/plain", "notes.txt", Set.of("text/csv")));
    }

    @Test
    void rejectsPlainTextAliasWhenCsvIsNotAllowed() {
        // Verifies that the alias is conditional on the allowlist itself: if text/csv is not
        // declared on the field, a text/plain CSV-looking upload must still be rejected.
        // Expected outcome: no aliasing occurs when the field does not allow text/csv.
        assertFalse(FormDataParser.isMimeAllowed("text/plain", "contacts.csv", Set.of("application/pdf")));
    }

    @Test
    void stillAcceptsDirectMimeMatchesAndWildcards() {
        // Verifies non-regression on the standard allowlist behavior.
        // Expected outcome: exact MIME matches and top-level wildcards still work unchanged.
        assertTrue(FormDataParser.isMimeAllowed("application/pdf", "report.pdf", Set.of("application/pdf")));
        assertTrue(FormDataParser.isMimeAllowed("image/png", "preview.png", Set.of("image/*")));
    }
}
