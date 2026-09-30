package org.jahia.modules.formidable.engine.config;

import org.jahia.modules.formidable.engine.config.LegacyConfigurationMigration.Outcome;
import org.jahia.modules.formidable.engine.config.formactions.FormActionsConfig;
import org.jahia.modules.formidable.engine.config.formactions.FormActionsConfigService;
import org.jahia.modules.formidable.engine.config.uploads.UploadsConfig;
import org.jahia.modules.formidable.engine.config.uploads.UploadsConfigService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LegacyConfigurationMigrationTest {

    private static final String PID = FormActionsConfigService.PID;
    private static final String LEGACY_TARGETS = "crm01|CRM|https://crm.example.com/forms";

    /** The theme's own properties, as fileinstall hands them over from the copied file: every setting at its default. */
    private static Map<String, Object> themeFromFile() {
        Map<String, Object> properties = new HashMap<>();
        properties.put(LegacyConfigurationMigration.FILEINSTALL_FILENAME, "file:/karaf/etc/" + PID + ".cfg");
        properties.put("forwardTargets", "");
        properties.put("enableDevForwardTargets", "false");
        properties.put("devForwardTargets", "");
        properties.put("forwardHttpConnectTimeoutSeconds", "5");
        properties.put("forwardHttpRequestTimeoutSeconds", "10");
        return properties;
    }

    /** ConfigurationAdmin holding the legacy PID with these properties (null: no legacy configuration at all). */
    private static ConfigurationAdmin adminWithLegacy(Dictionary<String, Object> legacy) throws Exception {
        ConfigurationAdmin admin = mock(ConfigurationAdmin.class);
        if (legacy != null) {
            Configuration legacyConfiguration = mock(Configuration.class);
            when(legacyConfiguration.getProperties()).thenReturn(legacy);
            when(admin.listConfigurations("(service.pid=" + LegacyConfigurationMigration.LEGACY_PID + ")"))
                    .thenReturn(new Configuration[] {legacyConfiguration});
        }
        return admin;
    }

    /** The theme's configuration in ConfigAdmin, its dictionary the one the migration writes into. */
    private static Configuration themeConfiguration(ConfigurationAdmin admin, Map<String, Object> properties) throws IOException {
        Configuration configuration = mock(Configuration.class);
        when(admin.getConfiguration(PID, "?")).thenReturn(configuration);
        when(configuration.getProperties()).thenAnswer(invocation -> properties == null ? null : new Hashtable<>(properties));
        return configuration;
    }

    private static Dictionary<String, Object> legacy(Map<String, Object> settings) {
        Hashtable<String, Object> legacy = new Hashtable<>(settings);
        legacy.put("service.pid", LegacyConfigurationMigration.LEGACY_PID);
        return legacy;
    }

    @Test
    void isNotDueWithoutTheThemesFileNorWithTheMarkerNorWithoutConfigurationAdmin() throws Exception {
        // Verifies the three preconditions: a configuration without a file behind it (the tests, or a value
        // written before fileinstall loads the copied file) is left alone, a marked one is done for good, and a
        // missing ConfigurationAdmin waits — none of them reads or writes anything.
        LegacyConfigurationMigration migration = new LegacyConfigurationMigration(PID, FormActionsConfig.class);
        ConfigurationAdmin admin = adminWithLegacy(legacy(Map.of("forwardTargets", LEGACY_TARGETS)));

        assertEquals(Outcome.NOT_DUE, migration.run(admin, null));
        assertEquals(Outcome.NOT_DUE, migration.run(admin, Map.of("forwardTargets", "")));
        Map<String, Object> marked = themeFromFile();
        marked.put(LegacyConfigurationMigration.MARKER, LegacyConfigurationMigration.LEGACY_PID);
        assertEquals(Outcome.NOT_DUE, migration.run(admin, marked));
        assertEquals(Outcome.NOT_DUE, migration.run(null, themeFromFile()));
        verify(admin, never()).listConfigurations(any());
        verify(admin, never()).getConfiguration(any(), any());
    }

    @Test
    void aFreshInstallIsMarkedOnceSoALegacyConfigurationCreatedLaterIsNeverRead() throws Exception {
        // Verifies "once" means once: no legacy configuration in ConfigAdmin, nothing to carry — the theme's file still
        // gets the marker, so that a single-PID configuration created later (a provisioning script nobody updated)
        // is not carried at the next restart, which a new object would otherwise do.
        ConfigurationAdmin admin = adminWithLegacy(null);
        Configuration theme = themeConfiguration(admin, themeFromFile());

        assertEquals(Outcome.NOTHING, new LegacyConfigurationMigration(PID, FormActionsConfig.class).run(admin, themeFromFile()));
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Dictionary<String, Object>> written = ArgumentCaptor.forClass(Dictionary.class);
        verify(theme).update(written.capture());
        assertEquals(LegacyConfigurationMigration.LEGACY_PID, written.getValue().get(LegacyConfigurationMigration.MARKER));

        Map<String, Object> marked = themeFromFile();
        marked.put(LegacyConfigurationMigration.MARKER, LegacyConfigurationMigration.LEGACY_PID);
        ConfigurationAdmin later = adminWithLegacy(legacy(Map.of("forwardTargets", LEGACY_TARGETS)));
        assertEquals(Outcome.NOT_DUE, new LegacyConfigurationMigration(PID, FormActionsConfig.class).run(later, marked), "after a restart");
        verify(later, never()).listConfigurations(any());
    }

    @Test
    void carriesTheSettingsHeldOffTheirDefaultAsStringsWithTheMarkerAndNotesTheLegacyFile() throws Exception {
        // Verifies the migration proper: a target and a typed timeout (a Long, as the Felix console stores them)
        // are written as strings — fileinstall would otherwise persist L"7" into the file — with the marker; a
        // setting the legacy configuration holds at its default travels nowhere; the legacy file gets its first
        // line, once; and the migration is done for good.
        Path legacyFile = Files.createTempFile("org.jahia.modules.formidable", ".cfg");
        try {
            Files.writeString(legacyFile, "forwardTargets=" + LEGACY_TARGETS + "\n", StandardCharsets.UTF_8);
            Map<String, Object> settings = new HashMap<>();
            settings.put("forwardTargets", LEGACY_TARGETS);
            settings.put("forwardHttpConnectTimeoutSeconds", 7L);
            settings.put("enableDevForwardTargets", "false");
            settings.put("uploadMaxFileCount", 3L);
            settings.put(LegacyConfigurationMigration.FILEINSTALL_FILENAME, legacyFile.toUri().toString());
            ConfigurationAdmin admin = adminWithLegacy(legacy(settings));
            Configuration theme = themeConfiguration(admin, themeFromFile());
            LegacyConfigurationMigration migration = new LegacyConfigurationMigration(PID, FormActionsConfig.class);

            assertEquals(Outcome.WRITTEN, migration.run(admin, themeFromFile()));

            @SuppressWarnings("unchecked")
            ArgumentCaptor<Dictionary<String, Object>> written = ArgumentCaptor.forClass(Dictionary.class);
            verify(theme).update(written.capture());
            assertEquals(LEGACY_TARGETS, written.getValue().get("forwardTargets"));
            assertEquals("7", written.getValue().get("forwardHttpConnectTimeoutSeconds"));
            assertEquals("false", written.getValue().get("enableDevForwardTargets"), "the file's own value, untouched");
            assertNull(written.getValue().get("uploadMaxFileCount"), "another theme's setting never travels here");
            assertEquals(LegacyConfigurationMigration.LEGACY_PID, written.getValue().get(LegacyConfigurationMigration.MARKER));
            String noted = Files.readString(legacyFile, StandardCharsets.UTF_8);
            assertTrue(noted.startsWith(LegacyConfigurationMigration.LEGACY_FILE_NOTICE_PREFIX), noted);
            assertTrue(noted.endsWith("forwardTargets=" + LEGACY_TARGETS + "\n"), "the administrator's content is kept");

            assertEquals(Outcome.NOT_DUE, migration.run(admin, themeFromFile()));
            new LegacyConfigurationMigration(PID, FormActionsConfig.class).run(admin, themeFromFile());
            assertEquals(1, Files.readString(legacyFile, StandardCharsets.UTF_8).split(LegacyConfigurationMigration.LEGACY_FILE_NOTICE_PREFIX, -1).length - 1,
                    "one notice, whichever theme runs first");
        } finally {
            Files.deleteIfExists(legacyFile);
        }
    }

    @Test
    void theThemesFileWinsWhereTheAdministratorAlreadySetASetting() throws Exception {
        // Verifies the conflict rule: a setting already changed in the theme's file is kept as the file says, and
        // when nothing else differs there is nothing to write — the theme's file rules from now on.
        Map<String, Object> edited = themeFromFile();
        edited.put("forwardTargets", "other|Other|https://other.example.com/forms");
        ConfigurationAdmin admin = adminWithLegacy(legacy(Map.of("forwardTargets", LEGACY_TARGETS)));
        Configuration theme = themeConfiguration(admin, edited);

        assertEquals(Outcome.NOTHING, new LegacyConfigurationMigration(PID, FormActionsConfig.class).run(admin, edited));
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Dictionary<String, Object>> written = ArgumentCaptor.forClass(Dictionary.class);
        verify(theme).update(written.capture());
        assertEquals("other|Other|https://other.example.com/forms", written.getValue().get("forwardTargets"), "the file's value is kept, only the marker is added");
        assertEquals(LegacyConfigurationMigration.LEGACY_PID, written.getValue().get(LegacyConfigurationMigration.MARKER));
    }

    @Test
    void retriesAWriteThatFailedThenGivesUpAndLetsTheThemesFileRule() throws Exception {
        // Verifies the retry: a failed write, or a configuration holding no properties yet, is tried again on the
        // next callback, three times, after which the migration is over and the theme's file rules as it stands.
        ConfigurationAdmin admin = adminWithLegacy(legacy(Map.of("forwardTargets", LEGACY_TARGETS)));
        Configuration theme = themeConfiguration(admin, themeFromFile());
        doThrow(new IOException("disk full")).when(theme).update(any());
        LegacyConfigurationMigration migration = new LegacyConfigurationMigration(PID, FormActionsConfig.class);

        assertEquals(Outcome.RETRY, migration.run(admin, themeFromFile()));
        assertEquals(Outcome.RETRY, migration.run(admin, themeFromFile()));
        assertEquals(Outcome.GIVEN_UP, migration.run(admin, themeFromFile()));
        assertEquals(Outcome.NOT_DUE, migration.run(admin, themeFromFile()));
        verify(theme, times(3)).update(any());

        Configuration empty = themeConfiguration(adminWithLegacy(legacy(Map.of("forwardTargets", LEGACY_TARGETS))), null);
        assertEquals(Outcome.RETRY, new LegacyConfigurationMigration(PID, FormActionsConfig.class)
                .run(adminWithLegacyAndTheme(empty), themeFromFile()));
    }

    private static ConfigurationAdmin adminWithLegacyAndTheme(Configuration theme) throws Exception {
        ConfigurationAdmin admin = adminWithLegacy(legacy(Map.of("forwardTargets", LEGACY_TARGETS)));
        when(admin.getConfiguration(PID, "?")).thenReturn(theme);
        return admin;
    }

    @Test
    void aWriteThatSucceedsAfterAFailureEndsTheMigration() throws Exception {
        // Verifies that a failure does not consume the migration: the retry writes, and the marker goes with it.
        ConfigurationAdmin admin = adminWithLegacy(legacy(Map.of("forwardTargets", LEGACY_TARGETS)));
        Configuration theme = themeConfiguration(admin, themeFromFile());
        doThrow(new IOException("disk full")).doNothing().when(theme).update(any());
        LegacyConfigurationMigration migration = new LegacyConfigurationMigration(PID, FormActionsConfig.class);

        assertEquals(Outcome.RETRY, migration.run(admin, themeFromFile()));
        assertEquals(Outcome.WRITTEN, migration.run(admin, themeFromFile()));
        assertEquals(Outcome.NOT_DUE, migration.run(admin, themeFromFile()));
        verify(theme, times(2)).update(any());
    }

    @Test
    void theDefaultsOfADefinitionAreItsAttributesAsText() {
        // Verifies what the migration compares against: every attribute of the definition, by its metatype id, at
        // the annotation's default rendered as the .cfg file renders it.
        Map<String, String> defaults = LegacyConfigurationMigration.defaultsOf(FormActionsConfig.class);

        assertEquals(Map.of(
                "forwardTargets", "",
                "enableDevForwardTargets", "false",
                "devForwardTargets", "",
                "forwardHttpConnectTimeoutSeconds", "5",
                "forwardHttpRequestTimeoutSeconds", "10"), defaults);
    }

    @Test
    void attributeIdsFollowTheMetatypeNameMangling() {
        assertEquals("forwardTargets", LegacyConfigurationMigration.attributeId("forwardTargets"));
        assertEquals("my.setting", LegacyConfigurationMigration.attributeId("my_setting"));
        assertEquals("my_setting", LegacyConfigurationMigration.attributeId("my__setting"));
        assertEquals("my-setting", LegacyConfigurationMigration.attributeId("my$_$setting"));
        assertEquals("my$setting", LegacyConfigurationMigration.attributeId("my$$setting"));
        assertEquals("mysetting", LegacyConfigurationMigration.attributeId("my$setting"));
    }

    @Test
    void multiValuedSettingsCompareAndWriteByTheirElements() {
        assertEquals(LegacyConfigurationMigration.asText(new String[] {"a", "b"}), LegacyConfigurationMigration.asText(new String[] {"a", "b"}));
        assertNotEquals(LegacyConfigurationMigration.asText(new String[] {"a"}), LegacyConfigurationMigration.asText(new String[] {"b"}));
        assertArrayEquals(new String[] {"1", "2"}, (String[]) LegacyConfigurationMigration.asStrings(new Object[] {1L, 2L}));
        assertEquals("7", LegacyConfigurationMigration.asStrings(7L));
        assertFalse(LegacyConfigurationMigration.asText(7L).isEmpty());
    }

    /** The uploads theme as its file hands it over, every setting at its default, and its configuration in ConfigAdmin. */
    private static Map<String, Object> uploadsFromFile() {
        Map<String, Object> properties = new HashMap<>();
        properties.put(LegacyConfigurationMigration.FILEINSTALL_FILENAME, "file:/karaf/etc/" + UploadsConfigService.PID + ".cfg");
        LegacyConfigurationMigration.defaultsOf(UploadsConfig.class).forEach(properties::put);
        return properties;
    }

    @Test
    void aRenamedSettingIsReadUnderItsFormerName() throws Exception {
        // Verifies the rename of uploadAllowedMimeTypes: the value a 0.4 configuration holds under the former name
        // lands under the new one — the new name read in the legacy configuration would find nothing.
        ConfigurationAdmin admin = adminWithLegacy(legacy(Map.of("uploadAllowedMimeTypes", "application/pdf,image/png")));
        Configuration theme = mock(Configuration.class);
        when(admin.getConfiguration(UploadsConfigService.PID, "?")).thenReturn(theme);
        when(theme.getProperties()).thenAnswer(invocation -> new Hashtable<>(uploadsFromFile()));

        assertEquals(Outcome.WRITTEN, new LegacyConfigurationMigration(UploadsConfigService.PID, UploadsConfig.class).run(admin, uploadsFromFile()));
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Dictionary<String, Object>> written = ArgumentCaptor.forClass(Dictionary.class);
        verify(theme).update(written.capture());
        assertEquals("application/pdf,image/png", written.getValue().get("uploadAllowedTypes"));
        assertNull(written.getValue().get("uploadAllowedMimeTypes"));
    }

    @Test
    void aRenamedSettingAtItsFormerDefaultIsNotCarried() throws Exception {
        // Verifies that 0.4's default list, the same types the new default writes as extensions, does not replace the
        // new default in the theme's file: nothing to carry.
        String formerDefault = LegacyConfigurationMigration.RENAMED.get("uploadAllowedTypes").defaultText();
        ConfigurationAdmin admin = adminWithLegacy(legacy(Map.of("uploadAllowedMimeTypes", formerDefault)));
        Configuration theme = mock(Configuration.class);
        when(admin.getConfiguration(UploadsConfigService.PID, "?")).thenReturn(theme);
        when(theme.getProperties()).thenAnswer(invocation -> new Hashtable<>(uploadsFromFile()));

        assertEquals(Outcome.NOTHING, new LegacyConfigurationMigration(UploadsConfigService.PID, UploadsConfig.class).run(admin, uploadsFromFile()));
        assertTrue(LegacyConfigurationMigration.defaultsOf(UploadsConfig.class).keySet().containsAll(LegacyConfigurationMigration.RENAMED.keySet()),
                "every renamed setting names a setting of a theme");
    }

    @Test
    void anEmptyListOfEarlierBuildsIsCarriedAsAnyFile() throws Exception {
        // Verifies the upgrade keeps what an empty list meant: until 0.5 it let every file through (a field's own
        // types still applied), where an empty uploadAllowedTypes refuses every file. It is carried as */*, which says
        // the same — a field without types accepts anything, a field with types keeps its own.
        ConfigurationAdmin admin = adminWithLegacy(legacy(Map.of("uploadAllowedMimeTypes", "")));
        Configuration theme = mock(Configuration.class);
        when(admin.getConfiguration(UploadsConfigService.PID, "?")).thenReturn(theme);
        when(theme.getProperties()).thenAnswer(invocation -> new Hashtable<>(uploadsFromFile()));

        assertEquals(Outcome.WRITTEN, new LegacyConfigurationMigration(UploadsConfigService.PID, UploadsConfig.class).run(admin, uploadsFromFile()));
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Dictionary<String, Object>> written = ArgumentCaptor.forClass(Dictionary.class);
        verify(theme).update(written.capture());
        assertEquals("*/*", written.getValue().get("uploadAllowedTypes"));
    }
}
