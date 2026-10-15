package org.jahia.modules.formidable.content;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The editor sees raw technical names when a label is missing: every type and every property of the CND has a label and
 * a tooltip in English, French and Portuguese, and the French and Portuguese texts are not the English ones.
 */
class ResourceBundlesTest {

    private static final String TYPE = "fmdbcnt_createContentAction";
    private static final Pattern PROPERTY = Pattern.compile("^ - ([A-Za-z:]+) \\(", Pattern.MULTILINE);
    private static final Locale PORTUGUESE = Locale.forLanguageTag("pt");

    private static String cnd() throws IOException {
        try (InputStream in = ResourceBundlesTest.class.getResourceAsStream("/META-INF/definitions.cnd")) {
            assertNotNull(in, "definitions.cnd is in the bundle");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static List<String> keys() throws IOException {
        List<String> keys = new ArrayList<>(List.of(TYPE, TYPE + ".ui.tooltip"));
        Matcher matcher = PROPERTY.matcher(cnd());
        while (matcher.find()) {
            String name = matcher.group(1);
            if (name.equals("jcr:title")) {
                continue;
            }
            keys.add(TYPE + "." + name);
            keys.add(TYPE + "." + name + ".ui.tooltip");
        }
        return keys;
    }

    private static ResourceBundle bundle(Locale locale) {
        return ResourceBundle.getBundle("resources.formidable-content", locale, ResourceBundlesTest.class.getClassLoader(),
                ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_PROPERTIES));
    }

    @Test
    void theCndHasTheSeventeenProperties() throws IOException {
        // 16 settings + the autocreated title: a property added to the CND without a label shows up here
        long settings = PROPERTY.matcher(cnd()).results().count();
        org.junit.jupiter.api.Assertions.assertEquals(17L, settings, "properties declared by the CND");
    }

    @Test
    void everyTypeAndPropertyHasALabelAndATooltipInEveryLanguage() throws IOException {
        for (Locale locale : new Locale[]{Locale.ENGLISH, Locale.FRENCH, PORTUGUESE}) {
            ResourceBundle bundle = bundle(locale);
            for (String key : keys()) {
                assertTrue(bundle.containsKey(key), key + " is missing in " + locale);
                assertFalse(bundle.getString(key).isBlank(), key + " is blank in " + locale);
            }
        }
    }

    @Test
    void frenchAndPortugueseAreTranslatedNotCopied() throws IOException {
        ResourceBundle english = bundle(Locale.ENGLISH);
        for (Locale locale : new Locale[]{Locale.FRENCH, PORTUGUESE}) {
            ResourceBundle bundle = bundle(locale);
            for (String key : keys()) {
                assertNotEquals(english.getString(key), bundle.getString(key), key + " is not translated in " + locale);
            }
        }
    }

    @Test
    void theFilesAreAsciiWithEscapes() throws IOException {
        for (String name : List.of("formidable-content.properties", "formidable-content_fr.properties", "formidable-content_pt.properties")) {
            try (InputStream in = getClass().getResourceAsStream("/resources/" + name)) {
                assertNotNull(in, name);
                String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                assertTrue(text.chars().allMatch(c -> c < 128), name + " is ASCII");
            }
        }
    }
}
