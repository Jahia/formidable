package org.jahia.modules.formidable.engine.config.formactions;

import org.jahia.modules.formidable.engine.config.TestConfigs;
import org.jahia.modules.formidable.engine.config.common.ConfigurationValues;
import org.jahia.modules.formidable.engine.config.common.FakeConfigService;
import org.jahia.modules.formidable.engine.migration.v05.FormerListLines;
import org.mockito.ArgumentCaptor;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.time.Duration;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FormActionsConfigServiceTest {

    /** A target file as DS hands it over: its settings, its id among them, and the file fileinstall read it from. */
    private static ForwardTargetComponent targetFile(String id, Map<String, Object> values) {
        Map<String, Object> settings = new HashMap<>(values);
        settings.putIfAbsent("id", id);
        ForwardTargetComponent file = new ForwardTargetComponent();
        file.configure(TestConfigs.of(ForwardTargetConfig.class, settings),
                Map.of("service.pid", ForwardTargetComponent.FACTORY_PID + "~" + id,
                        "felix.fileinstall.filename", "file:/karaf/etc/" + ForwardTargetComponent.fileName(id)));
        return file;
    }

    private static FormActionsConfigService configured(boolean enableDevelopment, ForwardTargetComponent... files) {
        FormActionsConfigService service = new FormActionsConfigService();
        for (ForwardTargetComponent file : files) {
            service.bindTarget(file);
        }
        service.activate(TestConfigs.of(FormActionsConfig.class, Map.of("enableDevForwardTargets", enableDevelopment)));
        return service;
    }

    @Test
    void theShippedFileAgreesWithTheDefinitionsDefaults() throws Exception {
        TestConfigs.assertShippedFileMatchesDefaults(FormActionsConfig.class, FormActionsConfigService.PID);
    }

    @Test
    void aTargetFileIsATargetNamedByItsId() {
        // Verifies the one file = one target rule: the id is the file's id setting, the label falls back to it.
        FormActionsConfigService service = configured(false,
                targetFile("crm", Map.of("label", "CRM", "url", "https://api.example.com/forms")),
                targetFile("plain", Map.of("url", "https://other.example.com/hook")));

        assertEquals("https://api.example.com/forms", service.resolveForwardTarget("crm").orElseThrow().uri().toString());
        assertEquals("CRM", service.resolveForwardTarget("crm").orElseThrow().label());
        assertEquals("plain", service.resolveForwardTarget("plain").orElseThrow().label());
        assertEquals(List.of("crm", "plain"), service.getForwardTargets().stream().map(FormActionsConfigService.ForwardTarget::id).toList());
        assertTrue(service.resolveForwardTarget(null).isEmpty());
    }

    @Test
    void aFileThatDescribesNoUsableTargetContributesNothing() {
        // Verifies the checks of one file: plain HTTP, embedded credentials, a malformed URL, no URL, no id.
        FormActionsConfigService service = configured(false,
                targetFile("http", Map.of("url", "http://api.example.com/forms")),
                targetFile("creds", Map.of("url", "https://user:pass@api.example.com/forms")),
                targetFile("bad", Map.of("url", "ht tp://x")),
                targetFile("nourl", Map.of()),
                targetFile("noid", Map.of("id", "", "url", "https://api.example.com")),
                targetFile("good", Map.of("url", "https://api.example.com/forms")));

        assertEquals(List.of("good"), service.getForwardTargets().stream().map(FormActionsConfigService.ForwardTarget::id).toList());
    }

    @Test
    void aDevelopmentFileCountsBehindTheSwitchOnlyAndNeverShadowsAStandardOne() {
        // Verifies the development targets: HTTP on localhost accepted for a file marked development, only while the
        // switch is on; a development file never shadows a standard id; a remote HTTP development file is refused.
        ForwardTargetComponent standard = targetFile("shared", Map.of("url", "https://api.example.com/forms"));
        ForwardTargetComponent shadow = targetFile("shared", Map.of("url", "http://localhost:8081/hook", "development", true));
        ForwardTargetComponent local = targetFile("local", Map.of("url", "http://localhost:8081/hook", "development", true));
        ForwardTargetComponent remote = targetFile("remote", Map.of("url", "http://example.com/hook", "development", true));

        FormActionsConfigService enabled = configured(true, standard, shadow, local, remote);
        assertEquals("https://api.example.com/forms", enabled.resolveForwardTarget("shared").orElseThrow().uri().toString());
        assertTrue(enabled.resolveForwardTarget("local").orElseThrow().development());
        assertTrue(enabled.resolveForwardTarget("remote").isEmpty());

        FormActionsConfigService disabled = configured(false, standard, shadow, local, remote);
        assertEquals(List.of("shared"), disabled.getForwardTargets().stream().map(FormActionsConfigService.ForwardTarget::id).toList());
        assertFalse(disabled.resolveForwardTarget("shared").orElseThrow().development());
    }

    @Test
    void activateFallsBackToDefaultTimeoutsWhenConfiguredValuesAreInvalidAndExposesValidOnes() {
        // Verifies timeout hardening: zero or negative values fall back to the defaults, explicit ones are exposed as is.
        FormActionsConfigService broken = new FormActionsConfigService();
        broken.activate(TestConfigs.of(FormActionsConfig.class, Map.of("forwardHttpConnectTimeoutSeconds", 0L, "forwardHttpRequestTimeoutSeconds", -1L)));
        assertEquals(Duration.ofSeconds(ConfigurationValues.DEFAULT_HTTP_CONNECT_TIMEOUT_SECONDS), broken.getForwardHttpConnectTimeout());
        assertEquals(Duration.ofSeconds(ConfigurationValues.DEFAULT_HTTP_REQUEST_TIMEOUT_SECONDS), broken.getForwardHttpRequestTimeout());

        FormActionsConfigService configured = new FormActionsConfigService();
        configured.activate(TestConfigs.of(FormActionsConfig.class, Map.of("forwardHttpConnectTimeoutSeconds", 13L, "forwardHttpRequestTimeoutSeconds", 17L)));
        assertEquals(Duration.ofSeconds(13), configured.getForwardHttpConnectTimeout());
        assertEquals(Duration.ofSeconds(17), configured.getForwardHttpRequestTimeout());
    }

    @Test
    void activateBuildsReusableForwardHttpClientAndRefreshesItOnConfigChange() {
        // Verifies HttpClient reuse within one config snapshot and refresh after re-activation.
        FormActionsConfigService service = new FormActionsConfigService();
        service.activate(TestConfigs.of(FormActionsConfig.class));
        HttpClient first = service.getForwardHttpClient();
        HttpClient second = service.getForwardHttpClient();
        service.activate(TestConfigs.of(FormActionsConfig.class, Map.of("forwardHttpConnectTimeoutSeconds", 7L)));

        assertSame(first, second);
        assertNotSame(second, service.getForwardHttpClient());
    }

    @Test
    void theLinesOfEarlierBuildsBecomeFilesThroughJahiasConfigService() throws Exception {
        // Verifies the conversion wired for this list: the lines found in the configuration's own file are stored as
        // one entry each through Jahia's configuration service, then leave the file with the marker.
        FakeConfigService configs = new FakeConfigService(ForwardTargetComponent.FACTORY_PID);
        ConfigurationAdmin admin = mock(ConfigurationAdmin.class);
        Map<String, Object> file = new HashMap<>(Map.of("felix.fileinstall.filename", "file:/karaf/etc/" + FormActionsConfigService.PID + ".cfg",
                "forwardTargets", "crm01|Salesforce|https://crm.example.com/hook", "devForwardTargets", "local|Local|http://localhost:3000/hook"));
        Configuration theme = mock(Configuration.class);
        when(admin.getConfiguration(FormActionsConfigService.PID, "?")).thenReturn(theme);
        when(theme.getProperties()).thenAnswer(invocation -> new Hashtable<>(file));
        FormActionsConfigService service = new FormActionsConfigService();
        service.setConfigurationAdmin(admin);
        service.setConfigService(configs.service);
        service.useForTests(Runnable::run);

        service.configure(TestConfigs.of(FormActionsConfig.class), file);

        assertEquals(Map.of("id", "crm01", "label", "Salesforce", "url", "https://crm.example.com/hook", "development", "false"), configs.entry("crm01"));
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Dictionary<String, Object>> written = ArgumentCaptor.forClass(Dictionary.class);
        // The migration's marker first (a theme with nothing to carry is marked too), then the conversion's write.
        verify(theme, times(2)).update(written.capture());
        assertEquals("true", written.getValue().get(FormerListLines.LINES_CONVERTED));
        assertNull(written.getValue().get("forwardTargets"));
        assertEquals("true", configs.entry("local").get("development"));
    }

    @Test
    void anInstallStillOnTheSinglePidHasItsLinesConvertedFromThere() throws Exception {
        // Verifies the fallback a 0.4 install takes: the theme's file holds no line, the lines are still in the
        // single PID of earlier builds — they are read there, stored as files, and the theme's file gets the marker.
        FakeConfigService configs = new FakeConfigService(ForwardTargetComponent.FACTORY_PID);
        ConfigurationAdmin admin = mock(ConfigurationAdmin.class);
        Configuration legacy = mock(Configuration.class);
        when(legacy.getProperties()).thenReturn(new Hashtable<>(Map.of("service.pid", "org.jahia.modules.formidable",
                "forwardTargets", "crm01|Salesforce|https://crm.example.com/hook")));
        when(admin.listConfigurations(any())).thenReturn(new Configuration[] {legacy});
        Map<String, Object> file = new HashMap<>(Map.of("felix.fileinstall.filename", "file:/karaf/etc/" + FormActionsConfigService.PID + ".cfg"));
        Configuration theme = mock(Configuration.class);
        when(admin.getConfiguration(FormActionsConfigService.PID, "?")).thenReturn(theme);
        when(theme.getProperties()).thenAnswer(invocation -> new Hashtable<>(file));
        FormActionsConfigService service = new FormActionsConfigService();
        service.setConfigurationAdmin(admin);
        service.setConfigService(configs.service);
        service.useForTests(Runnable::run);

        service.configure(TestConfigs.of(FormActionsConfig.class), file);

        assertEquals("https://crm.example.com/hook", configs.entry("crm01").get("url"));
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Dictionary<String, Object>> written = ArgumentCaptor.forClass(Dictionary.class);
        verify(theme, times(2)).update(written.capture());
        assertEquals("true", written.getValue().get(FormerListLines.LINES_CONVERTED));
    }

    @Test
    void aLineWhoseIdCannotNameAFileKeepsTheLinesInPlaceAndTheOthersAreNotWrittenTwice() throws Exception {
        // Verifies nothing is lost to an id that cannot name a file (a dot would collide with another id's file): the
        // valid lines are stored, the lines stay in the theme's file, no marker is written, and the ids already stored
        // are kept next to the lines — so the next run, while the refused id waits to be fixed, leaves alone an entry
        // the administrator deleted meanwhile, instead of writing it back.
        FakeConfigService configs = new FakeConfigService(ForwardTargetComponent.FACTORY_PID);
        ConfigurationAdmin admin = mock(ConfigurationAdmin.class);
        Map<String, Object> file = new HashMap<>(Map.of("felix.fileinstall.filename", "file:/karaf/etc/" + FormActionsConfigService.PID + ".cfg",
                "forwardTargets", "crm01|Salesforce|https://crm.example.com/hook\ncrm.eu|Salesforce EU|https://eu.example.com/hook"));
        // Every setting present, as in the shipped file: the completion of the missing ones has nothing to write.
        file.putAll(Map.of("enableDevForwardTargets", "false", "forwardHttpConnectTimeoutSeconds", "5", "forwardHttpRequestTimeoutSeconds", "10"));
        Configuration theme = mock(Configuration.class);
        when(admin.getConfiguration(FormActionsConfigService.PID, "?")).thenReturn(theme);
        when(theme.getProperties()).thenAnswer(invocation -> new Hashtable<>(file));
        org.mockito.Mockito.doAnswer(invocation -> {
            Dictionary<String, Object> written = invocation.getArgument(0);
            java.util.Collections.list(written.keys()).forEach(key -> file.put(key, written.get(key)));
            return null;
        }).when(theme).update(any());
        FormActionsConfigService service = new FormActionsConfigService();
        service.setConfigurationAdmin(admin);
        service.setConfigService(configs.service);
        service.useForTests(Runnable::run);

        service.configure(TestConfigs.of(FormActionsConfig.class), file);

        assertEquals("https://crm.example.com/hook", configs.entry("crm01").get("url"));
        assertNull(configs.entry("crm.eu"));
        assertEquals("crm01", file.get(FormerListLines.LINES_CONVERTED_IDS));
        assertNull(file.get(FormerListLines.LINES_CONVERTED));

        configs.stored.clear();
        org.mockito.Mockito.clearInvocations(theme);
        service.configure(TestConfigs.of(FormActionsConfig.class), file);

        assertTrue(configs.stored.isEmpty(), "an entry deleted after its conversion is not written back");
        assertNull(file.get(FormerListLines.LINES_CONVERTED));
        // Nothing stored, nothing written: an update would call the theme back and run the conversion again, forever.
        verify(theme, org.mockito.Mockito.never()).update(any());
    }

    @Test
    void anIdAnEntryAlreadyDeclaresIsNotWrittenAgainWhateverItsFileIsCalled() throws Exception {
        // Verifies the lookup among the entries bound: a file named target-crm-prod.cfg that declares id=crm does not
        // let the line crm|… nor a console entry of id crm write target-crm.cfg — two entries would then declare crm.
        FakeConfigService configs = new FakeConfigService(ForwardTargetComponent.FACTORY_PID);
        ConfigurationAdmin admin = mock(ConfigurationAdmin.class);
        Map<String, Object> file = new HashMap<>(Map.of("felix.fileinstall.filename", "file:/karaf/etc/" + FormActionsConfigService.PID + ".cfg",
                "forwardTargets", "crm|Salesforce|https://crm.example.com/hook"));
        Configuration theme = mock(Configuration.class);
        when(admin.getConfiguration(FormActionsConfigService.PID, "?")).thenReturn(theme);
        when(theme.getProperties()).thenAnswer(invocation -> new Hashtable<>(file));
        FormActionsConfigService service = new FormActionsConfigService();
        service.setConfigurationAdmin(admin);
        service.setConfigService(configs.service);
        service.useForTests(Runnable::run);
        ForwardTargetComponent named = new ForwardTargetComponent();
        named.configure(TestConfigs.of(ForwardTargetConfig.class, Map.of("id", "crm", "url", "https://crm.example.com/prod")),
                Map.of("service.pid", ForwardTargetComponent.FACTORY_PID + "~crm-prod",
                        "felix.fileinstall.filename", "file:/karaf/etc/" + ForwardTargetComponent.fileName("crm-prod")));
        service.bindTarget(named);

        service.configure(TestConfigs.of(FormActionsConfig.class), file);
        service.bindTarget(consoleCreated("0008", Map.of("id", "crm", "url", "https://crm.example.com/console")));

        assertTrue(configs.stored.isEmpty(), configs.stored.toString());
        assertTrue(configs.deleted.isEmpty(), "the console's entry is left for the administrator to delete");
    }

    @Test
    void aThemeThatChangedWithoutACallbackIsMergedAgainOnRead() throws Exception {
        // Verifies the rebuild on read: once ConfigurationAdmin arrives, the migration lays the single PID's settings
        // over the theme — here the development switch, its write failing and waiting for a retry — without any
        // callback of this service. The next read must merge again, or the development target stays out of sight.
        Map<String, Object> file = new HashMap<>(Map.of("felix.fileinstall.filename", "file:/karaf/etc/" + FormActionsConfigService.PID + ".cfg"));
        FormActionsConfigService service = new FormActionsConfigService();
        service.configure(TestConfigs.of(FormActionsConfig.class), file);
        service.bindTarget(targetFile("local", Map.of("url", "http://localhost:3000/hook", "development", true)));
        assertTrue(service.resolveForwardTarget("local").isEmpty(), "the switch is off");

        ConfigurationAdmin admin = mock(ConfigurationAdmin.class);
        Configuration legacy = mock(Configuration.class);
        when(legacy.getProperties()).thenReturn(new Hashtable<>(Map.of("service.pid", "org.jahia.modules.formidable", "enableDevForwardTargets", "true")));
        when(admin.listConfigurations(any())).thenReturn(new Configuration[] {legacy});
        Configuration theme = mock(Configuration.class);
        when(admin.getConfiguration(FormActionsConfigService.PID, "?")).thenReturn(theme);
        when(theme.getProperties()).thenAnswer(invocation -> new Hashtable<>(file));
        org.mockito.Mockito.doThrow(new java.io.IOException("read-only")).when(theme).update(any());
        service.setConfigurationAdmin(admin);

        assertTrue(service.resolveForwardTarget("local").isPresent(), "the carried switch is in force");
    }

    @Test
    void anIdThatCannotNameAFileCountsForNothingAndAConflictIsTriedAgain() throws Exception {
        // Verifies the id rule and the retry: an id with a dot is no id (it would collide with another's file), one with
        // an underscore is; a console entry colliding with a configured id is adopted once the administrator removed
        // the file it collided with, at the next callback.
        FakeConfigService configs = new FakeConfigService(ForwardTargetComponent.FACTORY_PID);
        FormActionsConfigService service = new FormActionsConfigService();
        service.setConfigService(configs.service);
        service.useForTests(Runnable::run);
        service.activate(TestConfigs.of(FormActionsConfig.class));

        ForwardTargetComponent dotted = consoleCreated("0005", Map.of("id", "crm.eu", "url", "https://eu.example.com/hook"));
        assertEquals("", dotted.id());
        service.bindTarget(dotted);
        assertTrue(configs.stored.isEmpty());
        assertEquals("crm_eu", consoleCreated("0007", Map.of("id", "crm_eu", "url", "https://eu.example.com/hook")).id());

        configs.stored.put(ForwardTargetComponent.FACTORY_PID + "-crm", Map.of("url", "https://mine.example.com/hook"));
        ForwardTargetComponent colliding = consoleCreated("0006", Map.of("id", "crm", "url", "https://crm.example.com/hook"));
        service.bindTarget(colliding);
        assertTrue(configs.deleted.isEmpty(), "the configured id wins");

        configs.stored.clear();
        service.updatedTarget(colliding);
        assertEquals("https://crm.example.com/hook", configs.entry("crm").get("url"));
        assertEquals(List.of(ForwardTargetComponent.FACTORY_PID + ".0006"), configs.deleted);
    }

    /** A configuration as the Felix console creates it: an unnamed factory configuration, a generated PID, no file. */
    private static ForwardTargetComponent consoleCreated(String suffix, Map<String, Object> values) {
        ForwardTargetComponent file = new ForwardTargetComponent();
        Map<String, Object> properties = new HashMap<>(values);
        properties.put("service.pid", ForwardTargetComponent.FACTORY_PID + "." + suffix);
        file.configure(TestConfigs.of(ForwardTargetConfig.class, values), properties);
        return file;
    }
}
