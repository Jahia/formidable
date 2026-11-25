package org.jahia.modules.formidable.quarantine.store;

import org.jahia.modules.formidable.engine.api.SubmittedFile;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The text of an intake file, which the log line of each intake prints: the field and the size, never a value. */
class IntakeFileTest {

    @Test
    void theTextNamesTheFieldAndTheSizeButNotTheFileNameNorTheBytes() {
        IntakeFile file = IntakeFile.of(new SubmittedFile("supportingDocument", "Jane_Doe_passport.pdf",
                "application/pdf", "the bytes of the scan".getBytes(StandardCharsets.UTF_8)));

        String text = file.toString();

        assertTrue(text.contains("supportingDocument"), text);
        assertTrue(text.contains("size=21"), text);
        assertFalse(text.contains("Jane_Doe"), text);
        assertFalse(text.contains("the bytes"), text);
    }
}
