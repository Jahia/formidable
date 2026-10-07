package org.jahia.modules.formidable.engine.config;

import org.jahia.modules.formidable.engine.config.common.ConfigurationAttributes;
import org.jahia.modules.formidable.engine.config.formactions.FormActionsConfig;
import org.jahia.modules.formidable.engine.config.formactions.FormActionsConfigService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;

import java.io.IOException;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MissingSettingsCompletionTest {

    private static final String PID = FormActionsConfigService.PID;

    /** A theme's file holding the request timeout only, edited off its default, and a key the module does not know. */
    private static Map<String, Object> partialFile() {
        Map<String, Object> properties = new HashMap<>();
        properties.put(ConfigurationAttributes.FILEINSTALL_FILENAME, "file:/karaf/etc/" + PID + ".cfg");
        properties.put("forwardHttpRequestTimeoutSeconds", "42");
        properties.put("someoneElsesKey", "kept");
        return properties;
    }

    /** ConfigurationAdmin holding the theme's configuration with these properties. */
    private static Configuration themeConfiguration(ConfigurationAdmin admin, Map<String, Object> properties) throws IOException {
        Configuration configuration = mock(Configuration.class);
        when(admin.getConfiguration(PID, "?")).thenReturn(configuration);
        when(configuration.getProperties()).thenAnswer(invocation -> new Hashtable<>(properties));
        return configuration;
    }

    private static MissingSettingsCompletion completion() {
        return new MissingSettingsCompletion(PID, FormActionsConfig.class);
    }

    @Test
    void addsTheMissingSettingsAtTheirDefaultsAndLeavesEveryValuePresentAsItIs() throws Exception {
        // Verifies the decision's rule: only what is missing is written, as strings, the edited value and the unknown
        // key untouched.
        ConfigurationAdmin admin = mock(ConfigurationAdmin.class);
        Configuration theme = themeConfiguration(admin, partialFile());

        Map<String, Object> added = completion().run(admin, partialFile());

        assertEquals(Map.of("enableDevForwardTargets", "false", "forwardHttpConnectTimeoutSeconds", "5"), added);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Dictionary<String, Object>> written = ArgumentCaptor.forClass(Dictionary.class);
        verify(theme).update(written.capture());
        assertEquals("false", written.getValue().get("enableDevForwardTargets"));
        assertEquals("5", written.getValue().get("forwardHttpConnectTimeoutSeconds"));
        assertEquals("42", written.getValue().get("forwardHttpRequestTimeoutSeconds"));
        assertEquals("kept", written.getValue().get("someoneElsesKey"));
    }

    @Test
    void theSettingsMissingFromConfigurationAdminAreWrittenThoughTheCallbackHoldsThemAll() throws Exception {
        // Verifies the case measured on a running instance: DS hands over every setting — the definition's defaults
        // are component properties of the generated component description — while the file lacks one; what is
        // missing is read from ConfigurationAdmin's copy.
        ConfigurationAdmin admin = mock(ConfigurationAdmin.class);
        Configuration theme = themeConfiguration(admin, partialFile());
        Map<String, Object> callback = partialFile();
        callback.put("enableDevForwardTargets", false);
        callback.put("forwardHttpConnectTimeoutSeconds", 5L);

        assertEquals(Map.of("enableDevForwardTargets", "false", "forwardHttpConnectTimeoutSeconds", "5"), completion().run(admin, callback));
        verify(theme).update(any());
    }

    @Test
    void aConfigurationWithoutItsFileOrWithoutConfigurationAdminIsLeftAlone() throws Exception {
        // Verifies the preconditions. Fileinstall persists an update only into the file the configuration came from, so
        // a configuration made without one (the Felix console, the tests) is not completed.
        ConfigurationAdmin admin = mock(ConfigurationAdmin.class);
        Map<String, Object> withoutFile = partialFile();
        withoutFile.remove(ConfigurationAttributes.FILEINSTALL_FILENAME);

        assertTrue(completion().run(admin, withoutFile).isEmpty());
        assertTrue(completion().run(admin, null).isEmpty());
        assertTrue(completion().run(null, partialFile()).isEmpty());
        verify(admin, never()).getConfiguration(any(), any());
    }

    @Test
    void aSettingAlreadyInConfigurationAdminIsNotWrittenAgain() throws Exception {
        // Verifies the write reads ConfigurationAdmin's copy, not the properties of the callback: a setting that reached
        // the configuration since (the migration's write) is not overwritten by its default.
        ConfigurationAdmin admin = mock(ConfigurationAdmin.class);
        Map<String, Object> current = partialFile();
        current.put("forwardHttpConnectTimeoutSeconds", "9");
        Configuration theme = themeConfiguration(admin, current);

        assertEquals(Map.of("enableDevForwardTargets", "false"), completion().run(admin, partialFile()));
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Dictionary<String, Object>> written = ArgumentCaptor.forClass(Dictionary.class);
        verify(theme).update(written.capture());
        assertEquals("9", written.getValue().get("forwardHttpConnectTimeoutSeconds"));
    }

    @Test
    void aWriteThatFailsIsNeverFatalAndIsTriedAgainAtTheNextCallback() throws Exception {
        // Verifies a failing write, checked or not, is not fatal (a runtime exception would fail the theme's activation):
        // nothing reported as added, and the next callback tries again since this start's completion is not over.
        ConfigurationAdmin admin = mock(ConfigurationAdmin.class);
        Configuration theme = themeConfiguration(admin, partialFile());
        doThrow(new IOException("disk full")).doThrow(new IllegalStateException("deleted")).doNothing().when(theme).update(any());
        MissingSettingsCompletion completion = completion();

        assertTrue(completion.run(admin, partialFile()).isEmpty());
        assertTrue(completion.run(admin, partialFile()).isEmpty());
        assertEquals(2, completion.run(admin, partialFile()).size());
        verify(theme, times(3)).update(any());
    }

    @Test
    void oneCompletionPerStartSoALineDeletedWhileTheModuleRunsStaysDeleted() throws Exception {
        // Verifies the file is never rewritten under an edit: once this start's completion is over (written here),
        // the callback an administrator's edit brings — a line deleted — writes nothing; the next start would.
        ConfigurationAdmin admin = mock(ConfigurationAdmin.class);
        Configuration theme = themeConfiguration(admin, partialFile());
        MissingSettingsCompletion completion = completion();

        assertEquals(2, completion.run(admin, partialFile()).size());
        assertTrue(completion.run(admin, partialFile()).isEmpty());
        verify(theme, times(1)).update(any());
        assertEquals(2, completion().run(admin, partialFile()).size(), "a new start completes again");
    }

    @Test
    void aFileCompleteAtStartStaysUntouchedWhenALineIsDeletedWhileTheModuleRuns() throws Exception {
        // Verifies the common case of the guarantee above (review of #374): the shipped files hold every setting, so
        // at most starts nothing is written; a line deleted afterwards still stays deleted until the next start.
        ConfigurationAdmin admin = mock(ConfigurationAdmin.class);
        Map<String, Object> file = partialFile();
        file.put("enableDevForwardTargets", "false");
        file.put("forwardHttpConnectTimeoutSeconds", "5");
        Configuration theme = themeConfiguration(admin, file);
        MissingSettingsCompletion completion = completion();

        assertTrue(completion.run(admin, file).isEmpty());
        file.remove("forwardHttpConnectTimeoutSeconds");
        assertTrue(completion.run(admin, file).isEmpty());
        verify(theme, never()).update(any());
    }
}
