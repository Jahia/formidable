package org.jahia.modules.formidable.engine.migration.common;

import org.jahia.modules.formidable.engine.migration.RemovedIn;

/**
 * Marks the current thread while a startup migration is writing. JCR observation
 * dispatches synchronously in the saving thread, so a listener reacting to contributor
 * saves ({@code ManualOptionsLanguageSyncListener}) consults {@link #isActive()} to leave
 * a migration's own writes alone: the migrated values are the truth of the previous
 * release (0.3 allowed option values to diverge between languages), and re-aligning them
 * on the default language would blank every non-default label. Set by
 * {@link ElementsRedeployRetriggeredMigration#migrateBothWorkspaces} around every pass,
 * so no migration writing i18n options has to remember it — the default pass keeps
 * observation on by design, and the live pass switches it off ({@link MigrationSessions}).
 *
 * <p>Lifecycle: removed in 0.6 with both waves of startup migrations (0.5.x becomes the minimum upgrade source) —
 * see docs/administration/upgrade-notes.md, "Startup migrations".
 */
@RemovedIn("0.6")
public final class MigrationWrites {

    private static final ThreadLocal<Boolean> ACTIVE = ThreadLocal.withInitial(() -> Boolean.FALSE);

    private MigrationWrites() {
    }

    /** True while a startup migration is writing on the current thread. */
    public static boolean isActive() {
        return ACTIVE.get();
    }

    public static void begin() {
        ACTIVE.set(Boolean.TRUE);
    }

    public static void end() {
        ACTIVE.remove();
    }
}
