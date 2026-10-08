package org.jahia.modules.formidable.engine.imports;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SystemNamesTest {

    @Test
    void aLabelGivesTheNameTheContentEditorWouldGenerate() {
        assertEquals("your-first-name", SystemNames.generate("Your First name"));
        assertEquals("votre-prenom", SystemNames.generate("Votre prénom"));
        assertEquals("your-enquiry", SystemNames.generate("Your Enquiry"));
        assertEquals("enter-your-email-here", SystemNames.generate("enter your email here"));
    }

    @Test
    void aLongLabelIsCutAtTheLengthTheContentEditorAllows() {
        String name = SystemNames.generate("A label that is far too long to be a system name ".repeat(4));
        assertEquals(128, SystemNames.MAX_LENGTH);
        assertTrue(name.length() <= SystemNames.MAX_LENGTH, name);
        assertFalse(name.endsWith("-"), name);
        assertTrue(name.startsWith("a-label-that-is-far-too-long-to-be-a-system-name-a-label"), name);
    }

    @Test
    void aMappedCharacterIsSpelledOutAsJahiaDoes() {
        assertEquals("strasse", SystemNames.generate("Straße"));
        assertEquals("tom-jerry", SystemNames.generate("Tom & Jerry"));
    }

    @Test
    void aFieldWithoutLabelKeepsItsSourceName() {
        SystemNames names = new SystemNames();
        assertEquals("text-input_0_1", names.of("", "text-input_0_1"));
        assertEquals("text-input_0_2", names.of(null, "text-input_0_2"));
        assertEquals("text-input_0_3", names.of("***", "text-input_0_3"));
    }

    @Test
    void aDuplicateLabelTakesTheNextFreeName() {
        SystemNames names = new SystemNames();
        assertEquals("email", names.of("Email", "x"));
        assertEquals("email-1", names.of("Email", "y"));
        assertEquals("email-2", names.of("email", "z"));
    }

    @Test
    void aNameTheSubmissionPipelineReadsForItselfIsNeverTaken() {
        SystemNames names = new SystemNames();
        assertEquals("lang-1", names.of("Lang", "x"));
        assertEquals("fid-1", names.reserve("fid"));
    }
}
