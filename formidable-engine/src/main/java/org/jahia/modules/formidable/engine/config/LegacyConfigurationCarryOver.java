package org.jahia.modules.formidable.engine.config;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Decides what to carry over when the module's deployed configuration file takes over a
 * configuration that was made without it.
 * <p>
 * Until 0.5 the module shipped no configuration file, so administrators configured it through
 * the provisioning API or the Felix console: those values lived in ConfigAdmin only. When the
 * module first starts with its file, Jahia copies the file to karaf/etc and fileinstall loads
 * it, replacing the configuration with the file's defaults. That switch is visible in the
 * configuration properties themselves: a configuration loaded from a file carries
 * {@code felix.fileinstall.filename}, one made without a file does not. The settings that
 * differed between the two are the ones to write back — once, by construction, since from
 * then on every configuration carries the file name.
 */
final class LegacyConfigurationCarryOver {

    static final String FILEINSTALL_FILENAME = "felix.fileinstall.filename";

    /** The module's settings: the attributes of the OCD, nothing else (no service.pid, no fileinstall keys). */
    private static final Set<String> SETTINGS = Arrays.stream(FormidableConfig.class.getDeclaredMethods())
            .map(Method::getName)
            .collect(Collectors.toUnmodifiableSet());

    private LegacyConfigurationCarryOver() {
    }

    /**
     * @param previous the configuration properties this component last received (null on first activation)
     * @param next     the configuration properties it receives now
     * @return the settings of the previous configuration to write into the new one, in a stable
     *         order; empty unless the new configuration comes from a file and the previous one did not
     */
    static Map<String, Object> settingsToCarryOver(Map<String, ?> previous, Map<String, ?> next) {
        Map<String, Object> carried = new TreeMap<>();
        if (previous == null || next == null
                || previous.containsKey(FILEINSTALL_FILENAME) || !next.containsKey(FILEINSTALL_FILENAME)) {
            return carried;
        }
        for (String setting : SETTINGS) {
            Object before = previous.get(setting);
            if (before != null && !String.valueOf(before).equals(String.valueOf(next.get(setting)))) {
                carried.put(setting, before);
            }
        }
        return carried;
    }
}
