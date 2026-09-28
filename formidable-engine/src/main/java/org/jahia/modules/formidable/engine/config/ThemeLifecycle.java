package org.jahia.modules.formidable.engine.config;

import org.osgi.service.cm.ConfigurationAdmin;

import java.lang.annotation.Annotation;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * What the configuration themes share, held by each theme's service rather than inherited — DS reads the
 * lifecycle annotations of the service class itself: the snapshot every getter serves, replaced whole on
 * each callback so that a reader never sees half a configuration, and the one-time
 * {@link LegacyConfigurationMigration} of the configuration made before the themes existed, run as soon as
 * the theme's configuration comes from its own file and ConfigurationAdmin is bound — whichever comes last.
 *
 * @param <S> the theme's snapshot, an immutable record of what its getters serve
 */
public final class ThemeLifecycle<S> {

    private final String pid;
    private final AtomicReference<S> snapshot = new AtomicReference<>();
    private final AtomicReference<ConfigurationAdmin> configurationAdmin = new AtomicReference<>();
    /** The raw properties last received, for a migration that waits for ConfigurationAdmin. */
    private final AtomicReference<Map<String, Object>> lastProperties = new AtomicReference<>();
    private final LegacyConfigurationMigration migration;

    public ThemeLifecycle(String pid, Class<? extends Annotation> definition) {
        this.pid = pid;
        this.migration = new LegacyConfigurationMigration(pid, definition);
    }

    /** ConfigurationAdmin bound, possibly after the first configuration: a migration that waited for it runs now. */
    public LegacyConfigurationMigration.Outcome setConfigurationAdmin(ConfigurationAdmin admin) {
        configurationAdmin.set(admin);
        return migration.run(admin, lastProperties.get());
    }

    public void unsetConfigurationAdmin(ConfigurationAdmin admin) {
        configurationAdmin.compareAndSet(admin, null);
    }

    /**
     * A configuration received — the activation, or a change: the snapshot read from it is in force from now
     * on, and the migration runs when it is due. A migration that writes is followed by a callback of its own,
     * which brings the merged configuration through here again.
     *
     * @param properties the raw properties DS handed over, null when the service is driven without a file (the tests)
     * @param read       the snapshot read from the configuration
     */
    public LegacyConfigurationMigration.Outcome configure(Map<String, Object> properties, S read) {
        snapshot.set(read);
        lastProperties.set(properties == null ? null : new HashMap<>(properties));
        return migration.run(configurationAdmin.get(), properties);
    }

    /** The snapshot in force; a getter called before the first configuration is a wiring mistake, named. */
    public S current() {
        S current = snapshot.get();
        if (current == null) {
            throw new IllegalStateException("The configuration " + pid + " is not initialized.");
        }
        return current;
    }
}
