package org.jahia.modules.formidable.jexperience.engine;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The messages the Content Editor hands to i18next as if they were keys: an entry's
 * {@code description} goes through {@code t()}, whose default separators are {@code :} for the
 * namespace and {@code .} for the key path — on a miss, i18next returns what follows them. The
 * sentences must carry neither, in every language.
 */
class ResourceBundlesTest {

    private static Properties bundle(String name) throws IOException {
        try (InputStream in = ResourceBundlesTest.class.getResourceAsStream("/resources/" + name)) {
            assertNotNull(in, name + " is on the classpath");
            Properties properties = new Properties();
            properties.load(in);
            return properties;
        }
    }

    @Test
    void theDescriptionShownUnderTheKeptEntryCarriesNoI18nextSeparator() throws Exception {
        // Verifies, for EN and FR, that the kept-entry description has no colon and no dot, so i18next
        // renders the whole sentence instead of the part after a separator it did not find as a key.
        for (String name : List.of("formidable-jexperience-engine.properties", "formidable-jexperience-engine_fr.properties")) {
            String message = bundle(name).getProperty(ProfilePropertiesChoiceListInitializer.KEPT_KEY);
            assertNotNull(message, name);
            assertFalse(message.contains(":"), name + ": " + message);
            assertFalse(message.contains("."), name + ": " + message);
            assertTrue(message.length() > 20, name);
        }
    }
}
