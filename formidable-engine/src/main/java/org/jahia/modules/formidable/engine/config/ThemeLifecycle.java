package org.jahia.modules.formidable.engine.config;

import org.osgi.service.cm.ConfigurationAdmin;

import java.lang.annotation.Annotation;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

/**
 * What the configuration themes share, held by each theme's service rather than inherited — DS reads the
 * lifecycle annotations of the service class itself: the snapshot every getter serves, replaced whole on
 * each callback so that a reader never sees half a configuration, and the one-time
 * {@link LegacyConfigurationMigration} of the configuration made before the themes existed, run as soon as
 * the theme's configuration comes from its own file and ConfigurationAdmin is bound — whichever comes last.
 * <p>
 * While the migration's write keeps failing, the settings it has to carry stay in force: the snapshot is read
 * from the theme's configuration with them laid over it, so a CAPTCHA key or a forward target of the single file
 * never lapses to the new file's default. The next attempts are scheduled here — nothing else would call the
 * migration again until the file changed or the node restarted — and once the last one fails the snapshot is read
 * from the file as it stands.
 * <p>
 * Once the migration is over, the first configuration received from the file at each start is completed with the
 * settings it does not hold, at their defaults ({@link MissingSettingsCompletion}) — never before: the default written for a setting the
 * migration still has to carry would read, to the migration, as a value the administrator chose.
 *
 * @param <C> the theme's definition
 * @param <S> the theme's snapshot, an immutable record of what its getters serve
 */
public final class ThemeLifecycle<C extends Annotation, S> {

    /** How long a failed write waits before it is tried again. */
    static final long RETRY_DELAY_SECONDS = 30;

    private final String pid;
    private final Class<C> definition;
    private final Function<C, S> reader;
    private final AtomicReference<S> snapshot = new AtomicReference<>();
    private final AtomicReference<ConfigurationAdmin> configurationAdmin = new AtomicReference<>();
    /** The raw properties and the configuration last received, for a migration tried again later. */
    private final AtomicReference<Map<String, Object>> lastProperties = new AtomicReference<>();
    private final AtomicReference<C> lastConfig = new AtomicReference<>();
    private final LegacyConfigurationMigration migration;
    private final MissingSettingsCompletion completion;
    private Executor retries = CompletableFuture.delayedExecutor(RETRY_DELAY_SECONDS, TimeUnit.SECONDS);

    public ThemeLifecycle(String pid, Class<C> definition, Function<C, S> reader) {
        this.pid = pid;
        this.definition = definition;
        this.reader = reader;
        this.migration = new LegacyConfigurationMigration(pid, definition);
        this.completion = new MissingSettingsCompletion(pid, definition);
    }

    /** The tests' seam: the next attempt of a failed write runs when the executor says. */
    void retryWith(Executor executor) {
        retries = executor;
    }

    /** ConfigurationAdmin bound, possibly after the first configuration: a migration that waited for it runs now. */
    public LegacyConfigurationMigration.Outcome setConfigurationAdmin(ConfigurationAdmin admin) {
        configurationAdmin.set(admin);
        return runMigration();
    }

    public void unsetConfigurationAdmin(ConfigurationAdmin admin) {
        configurationAdmin.compareAndSet(admin, null);
    }

    /**
     * A configuration received — the activation, or a change: the migration runs when it is due, and the snapshot
     * read from the configuration — with the settings of a pending migration laid over it — is in force from now on.
     * A migration that writes is followed by a callback of its own, which brings the merged configuration through
     * here again.
     *
     * @param properties the raw properties DS handed over, null when the service is driven without a file (the tests)
     * @param config     the configuration, as the theme's definition
     */
    public LegacyConfigurationMigration.Outcome configure(Map<String, Object> properties, C config) {
        lastProperties.set(properties == null ? null : new HashMap<>(properties));
        lastConfig.set(config);
        return runMigration();
    }

    private LegacyConfigurationMigration.Outcome runMigration() {
        LegacyConfigurationMigration.Outcome outcome = migration.run(configurationAdmin.get(), lastProperties.get());
        C config = lastConfig.get();
        if (config != null) {
            snapshot.set(reader.apply(withPending(config, migration.pending())));
        }
        if (outcome == LegacyConfigurationMigration.Outcome.RETRY) {
            retries.execute(this::runMigration);
        } else if (outcome == LegacyConfigurationMigration.Outcome.NOT_DUE && migration.settled()) {
            // Not right after the migration's own run: one that wrote is followed by a callback of its own, which the
            // completion reads, and one that gave up has just seen its writes fail — the next callback tries again.
            completion.run(configurationAdmin.get(), lastProperties.get());
        }
        return outcome;
    }

    /** The configuration with the pending settings laid over it, each read as the attribute's type. */
    @SuppressWarnings("unchecked")
    private C withPending(C config, Map<String, Object> pending) {
        if (pending.isEmpty()) {
            return config;
        }
        return (C) Proxy.newProxyInstance(definition.getClassLoader(), new Class<?>[] {definition}, (proxy, method, args) -> {
            Object value = method.getParameterCount() == 0 ? pending.get(LegacyConfigurationMigration.attributeId(method.getName())) : null;
            if (value == null) {
                return method.invoke(config, args);
            }
            String text = String.valueOf(value);
            Class<?> type = method.getReturnType();
            if (type == long.class) {
                return Long.parseLong(text.trim());
            }
            if (type == int.class) {
                return Integer.parseInt(text.trim());
            }
            if (type == boolean.class) {
                return Boolean.parseBoolean(text.trim());
            }
            return text;
        });
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
