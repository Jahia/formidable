package org.jahia.modules.formidable.engine.config.choiceoptions;

import org.jahia.modules.formidable.engine.config.TestConfigs;
import org.jahia.modules.formidable.engine.config.common.FakeConfigService;
import org.jahia.modules.formidable.engine.migration.v05.FormerListLines;
import org.mockito.ArgumentCaptor;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.junit.jupiter.api.Test;

import java.util.Dictionary;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChoiceOptionsConfigServiceTest {

    /** A source file as DS hands it over: its settings, its id among them, and the file fileinstall read it from. */
    private static OptionsSourceComponent sourceFile(String id, Map<String, Object> values) {
        Map<String, Object> settings = new HashMap<>(values);
        settings.putIfAbsent("id", id);
        OptionsSourceComponent file = new OptionsSourceComponent();
        file.configure(TestConfigs.of(OptionsSourceConfig.class, settings),
                Map.of("service.pid", OptionsSourceComponent.FACTORY_PID + "~" + id,
                        "felix.fileinstall.filename", "file:/karaf/etc/" + OptionsSourceComponent.fileName(id)));
        return file;
    }

    private static ChoiceOptionsConfigService configured(Map<String, Object> values, OptionsSourceComponent... files) {
        ChoiceOptionsConfigService service = new ChoiceOptionsConfigService();
        for (OptionsSourceComponent file : files) {
            service.bindSource(file);
        }
        service.activate(TestConfigs.of(ChoiceOptionsConfig.class, values));
        return service;
    }

    @Test
    void theShippedFileAgreesWithTheDefinitionsDefaults() throws Exception {
        TestConfigs.assertShippedFileMatchesDefaults(ChoiceOptionsConfig.class, ChoiceOptionsConfigService.PID);
    }

    @Test
    void aSourceFileIsASourceNamedByItsIdWithOrWithoutParam() {
        // Verifies the one file = one source rule: the id is the file's id setting, the param empty when absent, the
        // label falling back to the id; sources in id order.
        ChoiceOptionsConfigService service = configured(Map.of("optionsSourcesCacheTtlSeconds", 120L),
                sourceFile("tags", Map.of("label", "Tags", "initializerKey", "categoryTree", "param", "/sites/systemsite/categories")),
                sourceFile("countries", Map.of("initializerKey", "country")));

        assertEquals(List.of("countries", "tags"), service.getOptionsSources().stream().map(ChoiceOptionsConfigService.OptionsSource::id).toList());
        assertEquals("country", service.resolveOptionsSource("countries").orElseThrow().initializerKey());
        assertEquals("", service.resolveOptionsSource("countries").orElseThrow().param());
        assertEquals("countries", service.resolveOptionsSource("countries").orElseThrow().label());
        assertEquals("/sites/systemsite/categories", service.resolveOptionsSource("tags").orElseThrow().param());
        assertEquals(120L, service.getOptionsSourcesCacheTtl().toSeconds());
    }

    @Test
    void aFileWithoutAnIdOrAnInitializerContributesNothingAndAFileRemovedIsGone() {
        // Verifies the checks of one file and the dynamic reference: no id, no initializer — skipped; a file removed
        // while running removes its source at once.
        OptionsSourceComponent good = sourceFile("good", Map.of("initializerKey", "country"));
        ChoiceOptionsConfigService service = configured(Map.of(),
                sourceFile("noid", Map.of("id", "", "initializerKey", "country")),
                sourceFile("noinit", Map.of()),
                good);
        assertEquals(List.of("good"), service.getOptionsSources().stream().map(ChoiceOptionsConfigService.OptionsSource::id).toList());

        service.unbindSource(good);
        assertTrue(service.resolveOptionsSource("good").isEmpty());
        assertTrue(service.resolveOptionsSource(null).isEmpty());
    }

    @Test
    void activateFallsBackToTheDefaultsWhenTheTtlOrTheCapIsInvalid() {
        // Verifies the two guards: a non-positive TTL and a non-positive cap fall back to the defaults.
        ChoiceOptionsConfigService service = configured(Map.of("optionsSourcesCacheTtlSeconds", 0L, "optionsQueryMaxResults", -3));

        assertEquals(ChoiceOptionsConfig.DEFAULT_OPTIONS_SOURCES_CACHE_TTL_SECONDS, service.getOptionsSourcesCacheTtl().toSeconds());
        assertEquals(ChoiceOptionsConfig.DEFAULT_OPTIONS_QUERY_MAX_RESULTS, service.getOptionsQueryMaxResults());
        assertEquals(7, configured(Map.of("optionsQueryMaxResults", 7)).getOptionsQueryMaxResults());
    }

    @Test
    void theLinesOfEarlierBuildsBecomeFilesThroughJahiasConfigService() throws Exception {
        // Verifies the conversion wired for this list: the lines found in the configuration's own file are stored as
        // one entry each through Jahia's configuration service, then leave the file with the marker.
        FakeConfigService configs = new FakeConfigService(OptionsSourceComponent.FACTORY_PID);
        ConfigurationAdmin admin = mock(ConfigurationAdmin.class);
        Map<String, Object> file = new HashMap<>(Map.of("felix.fileinstall.filename", "file:/karaf/etc/" + ChoiceOptionsConfigService.PID + ".cfg",
                "optionsSources", "countries|Countries|country"));
        Configuration theme = mock(Configuration.class);
        when(admin.getConfiguration(ChoiceOptionsConfigService.PID, "?")).thenReturn(theme);
        when(theme.getProperties()).thenAnswer(invocation -> new Hashtable<>(file));
        ChoiceOptionsConfigService service = new ChoiceOptionsConfigService();
        service.setConfigurationAdmin(admin);
        service.setConfigService(configs.service);
        service.useForTests(Runnable::run);

        service.configure(TestConfigs.of(ChoiceOptionsConfig.class), file);

        assertEquals(Map.of("id", "countries", "label", "Countries", "initializerKey", "country", "param", ""), configs.entry("countries"));
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Dictionary<String, Object>> written = ArgumentCaptor.forClass(Dictionary.class);
        // The migration's marker first (a theme with nothing to carry is marked too), then the conversion's write.
        verify(theme, times(2)).update(written.capture());
        assertEquals("true", written.getValue().get(FormerListLines.LINES_CONVERTED));
        assertNull(written.getValue().get("optionsSources"));
    }
}
