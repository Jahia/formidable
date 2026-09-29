package org.jahia.modules.formidable.engine.config.choiceoptions;

import org.jahia.modules.formidable.engine.config.TestConfigs;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChoiceOptionsConfigServiceTest {

    private static ChoiceOptionsConfigService activated(Map<String, Object> values) {
        ChoiceOptionsConfigService service = new ChoiceOptionsConfigService();
        service.activate(TestConfigs.of(ChoiceOptionsConfig.class, values));
        return service;
    }

    @Test
    void theShippedFileAgreesWithTheDefinitionsDefaults() throws Exception {
        TestConfigs.assertShippedFileMatchesDefaults(ChoiceOptionsConfig.class, ChoiceOptionsConfigService.PID);
    }

    @Test
    void activateParsesOptionsSourcesWithAndWithoutParam() {
        // Verifies the options source happy path: 3-part and 4-part entries are both accepted, param defaults to empty.
        ChoiceOptionsConfigService service = activated(Map.of(
                "optionsSources", "countries|Countries|country\ntags|Tags|categoryTree|/sites/systemsite/categories",
                "optionsSourcesCacheTtlSeconds", 120L));

        assertEquals(2, service.getOptionsSources().size());
        assertEquals("country", service.resolveOptionsSource("countries").orElseThrow().initializerKey());
        assertEquals("", service.resolveOptionsSource("countries").orElseThrow().param());
        assertEquals("/sites/systemsite/categories", service.resolveOptionsSource("tags").orElseThrow().param());
        assertEquals(120L, service.getOptionsSourcesCacheTtl().toSeconds());
    }

    @Test
    void activateSkipsMalformedAndDuplicateOptionsSources() {
        // Verifies resilience of the options source parsing: bad lines never poison good ones.
        ChoiceOptionsConfigService service = activated(Map.of("optionsSources", """
                only-two-parts|Broken
                |Blank id|country
                good|Good|country
                good|Duplicate|language"""));

        assertEquals(1, service.getOptionsSources().size());
        assertEquals("country", service.resolveOptionsSource("good").orElseThrow().initializerKey());
    }

    @Test
    void activateFallsBackToTheDefaultsWhenTheTtlOrTheCapIsInvalid() {
        // Verifies the two guards: a non-positive TTL and a non-positive cap fall back to the defaults.
        ChoiceOptionsConfigService service = activated(Map.of("optionsSourcesCacheTtlSeconds", 0L, "optionsQueryMaxResults", -3));

        assertEquals(ChoiceOptionsConfig.DEFAULT_OPTIONS_SOURCES_CACHE_TTL_SECONDS, service.getOptionsSourcesCacheTtl().toSeconds());
        assertEquals(ChoiceOptionsConfig.DEFAULT_OPTIONS_QUERY_MAX_RESULTS, service.getOptionsQueryMaxResults());
        assertEquals(7, activated(Map.of("optionsQueryMaxResults", 7)).getOptionsQueryMaxResults());
    }
}
