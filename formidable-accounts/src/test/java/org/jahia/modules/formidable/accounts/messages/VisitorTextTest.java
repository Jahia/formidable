package org.jahia.modules.formidable.accounts.messages;

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
    void everySentenceIsInTheBundleInEnglishAndFrenchAndTheEnglishMatchesTheLastResort() {
        // Verifies the bundle and the enum agree: no key is missing, the base bundle says what the code falls back on,
        // and French differs from English, so no sentence reaches a French visitor untranslated.
        for (VisitorText text : VisitorText.values()) {
            assertEquals(text.english(), text.in(Locale.ENGLISH), text.key());
            assertNotEquals(text.english(), text.in(Locale.FRENCH), text.key() + " is not translated");
            assertFalse(text.in(Locale.FRENCH).isBlank());
        }
    }

    @Test
    void everySentenceIsTranslatedIntoEuropeanPortugueseForPtAndPtPt() {
        // Verifies the Portuguese bundle carries every key: a Portuguese visitor, whatever the site's region, reads
        // neither the English last resort nor the French text.
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
        // Verifies the no-fallback rule shared with the rest of Formidable.
        assertEquals(VisitorText.SIGN_IN_FAILED.english(), VisitorText.SIGN_IN_FAILED.in(Locale.GERMAN));
        assertEquals(VisitorText.SIGN_IN_FAILED.english(), VisitorText.SIGN_IN_FAILED.in(null));
    }

    @Test
    void theKeysAreDistinctAndTheMailPlaceholdersSurviveTranslation() {
        // Verifies two properties a translator could break: the keys, and the ${link} and ${hours} of the body.
        Set<String> keys = new HashSet<>();
        for (VisitorText text : VisitorText.values()) {
            assertTrue(keys.add(text.key()), text.key());
        }
        for (Locale locale : new Locale[]{Locale.ENGLISH, Locale.FRENCH, PORTUGUESE}) {
            String body = VisitorText.ACTIVATION_BODY.in(locale);
            assertTrue(body.contains("${link}") && body.contains("${hours}"), locale.toString());
        }
    }
}
