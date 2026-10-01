package org.jahia.modules.formidable.engine.config;

import org.jahia.modules.formidable.engine.config.LegacyConfigurationMigration.Outcome;
import org.jahia.modules.formidable.engine.config.formactions.FormActionsConfig;
import org.junit.jupiter.api.Test;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ThemeLifecycleTest {

    private static final String PID = "org.jahia.modules.formidable.formActions";
    private static final String LEGACY_TIMEOUT = "30";

    /** A lifecycle whose snapshot is the configuration's request timeout, the easiest setting to watch. */
    private static ThemeLifecycle<FormActionsConfig, Long> lifecycle() {
        return new ThemeLifecycle<>(PID, FormActionsConfig.class, FormActionsConfig::forwardHttpRequestTimeoutSeconds);
    }

    private static Map<String, Object> fromFile() {
        Map<String, Object> properties = new HashMap<>();
        properties.put("felix.fileinstall.filename", "file:/karaf/etc/" + PID + ".cfg");
        properties.put("forwardHttpRequestTimeoutSeconds", "10");
        return properties;
    }

    /** ConfigurationAdmin holding the single PID with a request timeout, and the theme's configuration. */
    private static ConfigurationAdmin adminWithLegacyTimeout(Configuration theme) throws Exception {
        ConfigurationAdmin admin = mock(ConfigurationAdmin.class);
        Configuration legacy = mock(Configuration.class);
        Hashtable<String, Object> legacyProperties = new Hashtable<>(Map.of("service.pid", LegacyConfigurationMigration.LEGACY_PID, "forwardHttpRequestTimeoutSeconds", LEGACY_TIMEOUT));
        when(legacy.getProperties()).thenReturn(legacyProperties);
        when(admin.listConfigurations(any())).thenReturn(new Configuration[] {legacy});
        when(admin.getConfiguration(PID, "?")).thenReturn(theme);
        when(theme.getProperties()).thenAnswer(invocation -> new Hashtable<>(fromFile()));
        return admin;
    }

    @Test
    void aGetterBeforeTheFirstConfigurationIsAWiringMistakeNamed() {
        // Verifies the guard every theme's getters go through: nothing configured yet, the PID in the message.
        IllegalStateException error = assertThrows(IllegalStateException.class, lifecycle()::current);
        assertEquals("The configuration " + PID + " is not initialized.", error.getMessage());
    }

    @Test
    void theSnapshotIsReadFromEachConfigurationReceived() {
        // Verifies the plain path, the tests' one: no file behind the configuration, no migration, the snapshot read.
        ThemeLifecycle<FormActionsConfig, Long> lifecycle = lifecycle();

        assertEquals(Outcome.NOT_DUE, lifecycle.configure(null, TestConfigs.of(FormActionsConfig.class, Map.of("forwardHttpRequestTimeoutSeconds", 7L))));
        assertEquals(7L, lifecycle.current());
    }

    @Test
    void theSettingsToCarryStayInForceWhileTheWriteKeepsFailingAndTheNextAttemptIsScheduled() throws Exception {
        // Verifies the review's regression: a failing write must not leave the theme on its file's defaults, the
        // legacy timeout stays in force, and the next attempts run by themselves, without a configuration change.
        // Once the last one fails, the file rules as it stands.
        Configuration theme = mock(Configuration.class);
        doThrow(new IOException("disk full")).when(theme).update(any());
        ConfigurationAdmin admin = adminWithLegacyTimeout(theme);
        List<Runnable> scheduled = new ArrayList<>();
        ThemeLifecycle<FormActionsConfig, Long> lifecycle = lifecycle();
        lifecycle.retryWith(scheduled::add);
        lifecycle.setConfigurationAdmin(admin);

        assertEquals(Outcome.RETRY, lifecycle.configure(fromFile(), TestConfigs.of(FormActionsConfig.class)));
        assertEquals(30L, lifecycle.current(), "the legacy value, not the file's default");
        assertEquals(1, scheduled.size());

        scheduled.remove(0).run();
        assertEquals(30L, lifecycle.current());
        scheduled.remove(0).run();
        verify(theme, times(3)).update(any());
        assertEquals(10L, lifecycle.current(), "given up: the file's value");
        assertEquals(0, scheduled.size());
    }

    @Test
    void aScheduledAttemptThatSucceedsWritesTheSettingsAndStopsRetrying() throws Exception {
        // Verifies the happy end of a retry: the second attempt writes and nothing more is scheduled — the write's own
        // callback then brings the merged configuration through configure().
        Configuration theme = mock(Configuration.class);
        doThrow(new IOException("disk full")).doNothing().when(theme).update(any());
        ConfigurationAdmin admin = adminWithLegacyTimeout(theme);
        List<Runnable> scheduled = new ArrayList<>();
        ThemeLifecycle<FormActionsConfig, Long> lifecycle = lifecycle();
        lifecycle.retryWith(scheduled::add);
        lifecycle.setConfigurationAdmin(admin);

        lifecycle.configure(fromFile(), TestConfigs.of(FormActionsConfig.class));
        scheduled.remove(0).run();

        verify(theme, times(2)).update(any());
        assertEquals(0, scheduled.size());
    }
}
