package org.jahia.modules.formidable.content.messages;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VisitorTextTest {

    /** European Portuguese: a site in pt and one in pt_PT read the same _pt bundle. */
    private static final Locale PORTUGUESE = Locale.forLanguageTag("pt");
    private static final Locale PORTUGAL = Locale.forLanguageTag("pt-PT");

    @Test
    void everySentenceIsInTheBundleAndTheEnglishMatchesTheLastResort() {
        // the bundle and the enum agree: no key is missing, the base bundle says what the code falls back on, and
        // French differs from English, so no sentence reaches a French visitor untranslated
        for (VisitorText text : VisitorText.values()) {
            assertEquals(text.english(), text.in(Locale.ENGLISH), text.key());
            assertNotEquals(text.english(), text.in(Locale.FRENCH), text.key() + " is not translated");
            assertFalse(text.in(Locale.FRENCH).isBlank());
        }
    }

    @Test
    void everySentenceIsTranslatedIntoPortugueseForPtAndPtPt() {
        for (VisitorText text : VisitorText.values()) {
            String portuguese = text.in(PORTUGUESE);
            assertFalse(portuguese.isBlank(), text.key());
            assertNotEquals(text.english(), portuguese, text.key() + " is not translated into Portuguese");
            assertNotEquals(text.in(Locale.FRENCH), portuguese, text.key());
            assertEquals(portuguese, text.in(PORTUGAL), text.key() + " in pt-PT");
        }
    }

    @Test
    void anUnknownLocaleReadsEnglishNotTheServersLanguage() {
        assertEquals(VisitorText.NO_FOLDER.english(), VisitorText.NO_FOLDER.in(Locale.GERMAN));
        assertEquals(VisitorText.NO_FOLDER.english(), VisitorText.NO_FOLDER.in(null));
    }

    @Test
    void theKeysAreDistinct() {
        Set<String> keys = new HashSet<>();
        for (VisitorText text : VisitorText.values()) {
            assertTrue(keys.add(text.key()), text.key());
        }
    }

    @Test
    void noSentenceNamesAFolderAGroupOrASetting() {
        // what the visitor reads says what to do, never what is behind it
        for (VisitorText text : VisitorText.values()) {
            for (Locale locale : new Locale[]{Locale.ENGLISH, Locale.FRENCH, PORTUGUESE}) {
                String sentence = text.in(locale).toLowerCase(Locale.ROOT);
                assertFalse(sentence.contains("group") || sentence.contains("groupe") || sentence.contains("grupo") || sentence.contains("/"),
                        text.key() + " in " + locale);
            }
        }
    }
}
