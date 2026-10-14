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
        for (Locale locale : new Locale[]{Locale.ENGLISH, Locale.FRENCH}) {
            String body = VisitorText.ACTIVATION_BODY.in(locale);
            assertTrue(body.contains("${link}") && body.contains("${hours}"), locale.toString());
        }
    }
}
