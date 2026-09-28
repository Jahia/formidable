package org.jahia.modules.formidable.engine.config;

import org.jahia.modules.formidable.engine.config.LegacyConfigurationMigration.Outcome;
import org.jahia.modules.formidable.engine.config.uploads.UploadsConfig;
import org.junit.jupiter.api.Test;
import org.osgi.service.cm.ConfigurationAdmin;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class ThemeLifecycleTest {

    private static final String PID = "org.jahia.modules.formidable.uploads";

    @Test
    void aGetterBeforeTheFirstConfigurationIsAWiringMistakeNamed() {
        // Verifies the guard every theme's getters go through: nothing configured yet, the PID in the message.
        ThemeLifecycle<String> lifecycle = new ThemeLifecycle<>(PID, UploadsConfig.class);

        IllegalStateException error = assertThrows(IllegalStateException.class, lifecycle::current);
        assertEquals("The configuration " + PID + " is not initialized.", error.getMessage());
    }

    @Test
    void theSnapshotIsReplacedWholeOnEachConfigurationAndTheMigrationWaitsForTheFile() {
        // Verifies the two things a configure does: the snapshot in force from then on, and a migration that is
        // not due without a file behind the configuration — the way the tests drive a service.
        ThemeLifecycle<String> lifecycle = new ThemeLifecycle<>(PID, UploadsConfig.class);

        assertEquals(Outcome.NOT_DUE, lifecycle.configure(null, "first"));
        assertEquals("first", lifecycle.current());
        assertEquals(Outcome.NOT_DUE, lifecycle.configure(Map.of("uploadMaxFileCount", "10"), "second"));
        assertEquals("second", lifecycle.current());
    }

    @Test
    void aConfigurationAdminBoundAfterAFileBackedConfigurationRunsTheMigrationThen() throws Exception {
        // Verifies the late binding: the theme's file arrived before ConfigurationAdmin — the migration waited, and
        // runs on the bind with the properties last received (here: no legacy configuration, so nothing to do).
        ThemeLifecycle<String> lifecycle = new ThemeLifecycle<>(PID, UploadsConfig.class);
        ConfigurationAdmin admin = mock(ConfigurationAdmin.class);
        Map<String, Object> fromFile = Map.of(LegacyConfigurationMigration.FILEINSTALL_FILENAME, "file:/karaf/etc/" + PID + ".cfg");

        assertEquals(Outcome.NOT_DUE, lifecycle.configure(fromFile, "snapshot"));
        verify(admin, never()).listConfigurations(any());
        assertEquals(Outcome.NOTHING, lifecycle.setConfigurationAdmin(admin));
        verify(admin, times(1)).listConfigurations(any());
        assertEquals("snapshot", lifecycle.current());

        lifecycle.unsetConfigurationAdmin(admin);
        assertEquals(Outcome.NOT_DUE, lifecycle.configure(fromFile, "again"), "done once: never asked again");
    }
}
