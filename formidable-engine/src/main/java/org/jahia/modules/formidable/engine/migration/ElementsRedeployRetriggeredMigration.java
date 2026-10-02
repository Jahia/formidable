package org.jahia.modules.formidable.engine.migration;

import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRSessionWrapper;
import org.jahia.services.observation.JahiaEventListener;
import org.jahia.services.templates.JahiaTemplateManagerService.TemplatePackageRedeployedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;
import java.util.EventObject;

/**
 * Base for the startup content migrations that must also re-run when the
 * formidable-elements module is (re)deployed — or another module a migration
 * names through {@link #retriggeredBy}. The engine usually starts (and is
 * upgraded) before the elements, and a migration that writes properties or mixins
 * resolved through the element types can only succeed once that module has
 * registered its upgraded definitions: on the engine-first upgrade path the
 * elements-redeploy run is the one that does the work, the engine-activation run
 * having failed against the previous definitions.
 *
 * <p>Concrete migrations keep their own {@code @Component} declaration and must
 * expose {@link JahiaEventListener} as a service interface to receive the event.
 *
 * <p>Lifecycle: to be removed with the last migration it retriggers (0.6, with
 * {@link MixinPropertyNamesMigration}) — see docs/administration/upgrade-notes.md,
 * "Startup migrations".
 */
abstract class ElementsRedeployRetriggeredMigration implements JahiaEventListener<EventObject> {

    /** One workspace pass of a migration; returns the number of migrated nodes. */
    @FunctionalInterface
    interface WorkspacePass {
        int migrate(JCRSessionWrapper session, String workspace) throws RepositoryException;
    }

    static final String ELEMENTS_MODULE_ID = "formidable-elements";

    @SuppressWarnings("unchecked")
    private static final Class<EventObject>[] ACCEPTED_EVENT_TYPES = new Class[]{TemplatePackageRedeployedEvent.class};

    private final Logger log = LoggerFactory.getLogger(getClass());

    /** The redeploy event carries the module id as its source. */
    @Override
    public void onEvent(EventObject event) {
        if (event instanceof TemplatePackageRedeployedEvent && event.getSource() instanceof String moduleId && retriggeredBy(moduleId)) {
            log.info("[{}] {} (re)deployed, re-running the migration", getClass().getSimpleName(), moduleId);
            run();
        }
    }

    /**
     * Whether the (re)deploy of this module re-runs the migration: formidable-elements, whose types the
     * migrations write through. A migration whose carriers may be declared by any module widens it.
     */
    boolean retriggeredBy(String moduleId) {
        return ELEMENTS_MODULE_ID.equals(moduleId);
    }

    @Override
    public Class<EventObject>[] getEventTypes() {
        return ACCEPTED_EVENT_TYPES;
    }

    /** Runs the whole migration; keyed on content state, so re-running is a no-op. */
    abstract void run();

    /** What became of one carrier node — the tally a workspace pass reports. */
    enum Outcome { MIGRATED, DEFERRED, UNTOUCHED, FAILED }

    /** The outcomes of one workspace pass, counted. */
    static final class Tally {
        private final int[] counts = new int[Outcome.values().length];

        void add(Outcome outcome) {
            counts[outcome.ordinal()]++;
        }

        int of(Outcome outcome) {
            return counts[outcome.ordinal()];
        }
    }

    /** Drops the half-applied changes of a failed node, or every later save would re-throw them. */
    void refreshQuietly(JCRSessionWrapper session) {
        try {
            session.refresh(false);
        } catch (RepositoryException e) {
            log.warn("[{}] Could not discard the pending changes: {}", getClass().getSimpleName(), e.getMessage(), e);
        }
    }

    /** One carrier node's rewrite: judges the node and writes, answers its outcome, saves nothing. */
    @FunctionalInterface
    interface NodePass {
        Outcome rewrite(JCRSessionWrapper session, JCRNodeWrapper node) throws RepositoryException;
    }

    /**
     * Runs one node's pass and saves it when it migrated: one save per migrated node, so a
     * failure never discards the nodes migrated before it nor poisons the later saves. A failure
     * is logged with its half-applied changes dropped, and counts as FAILED. The MIGRATED line —
     * {@code migratedMessage} followed by the node path — is what the upgrade note tells the
     * administrator to look for, reported once the save went through.
     */
    Outcome migrateOne(JCRSessionWrapper session, JCRNodeWrapper node, String workspace, String migratedMessage, NodePass pass) {
        try {
            Outcome outcome = pass.rewrite(session, node);
            if (outcome == Outcome.MIGRATED) {
                session.save();
                log.info("[{}] {} '{}'", getClass().getSimpleName(), migratedMessage, node.getPath());
            }
            return outcome;
        } catch (RepositoryException e) {
            log.error("[{}] Could not migrate node '{}' in workspace '{}': {}",
                    getClass().getSimpleName(), node.getPath(), workspace, e.getMessage(), e);
            refreshQuietly(session);
            return Outcome.FAILED;
        }
    }

    /**
     * Ends a workspace pass with its tally: the migrated count, the deferred count with the way
     * out (the elements redeploy), the failed count with the retry (next start or redeploy), or a
     * debug line when nothing was found. The templates take the count and the workspace, the
     * {@code none} one the workspace alone; each starts with the migration's own tag.
     */
    void logSummary(String workspace, Tally tally, String migrated, String deferred, String failed, String none) {
        int migratedCount = tally.of(Outcome.MIGRATED);
        int deferredCount = tally.of(Outcome.DEFERRED);
        int failedCount = tally.of(Outcome.FAILED);
        if (migratedCount > 0) {
            log.info(migrated, migratedCount, workspace);
        }
        if (deferredCount > 0) {
            log.info(deferred, deferredCount, workspace);
        }
        if (failedCount > 0) {
            log.warn(failed, failedCount, workspace);
        } else if (migratedCount == 0 && deferredCount == 0) {
            log.debug(none, workspace);
        }
    }

    /**
     * Runs one pass per workspace, default then live, each in the session
     * {@link MigrationSessions} provides (the live one a system session, so that Jahia does
     * not mistake the rewrite for user-generated content). A failure in one workspace is
     * logged and never blocks the other. The thread is marked as a migration write
     * throughout ({@link MigrationWrites}), so the listeners reacting to contributor saves
     * leave these writes alone — the default pass keeps JCR observation on.
     */
    void migrateBothWorkspaces(WorkspacePass pass) {
        MigrationWrites.begin();
        try {
            for (String workspace : new String[]{"default", "live"}) {
                try {
                    MigrationSessions.execute(workspace, session -> pass.migrate(session, workspace));
                } catch (RepositoryException e) {
                    log.error("[{}] Migration failed in workspace '{}': {}", getClass().getSimpleName(), workspace, e.getMessage(), e);
                }
            }
        } finally {
            MigrationWrites.end();
        }
    }
}
