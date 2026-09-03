package org.jahia.modules.formidable.engine.migration;

import org.jahia.services.cache.CacheHelper;
import org.jahia.services.content.JCRCallback;
import org.jahia.services.content.JCRObservationManager;
import org.jahia.services.content.JCRTemplate;

import javax.jcr.RepositoryException;

import static org.jahia.modules.formidable.engine.util.FormidableJcrConstants.WORKSPACE_LIVE;

/**
 * Runs one workspace pass of a startup content migration in a system session.
 *
 * <p>The live pass is a SILENT rewrite: JCR observation is switched off while the callback
 * runs. Jahia's UGCListener treats every direct live write on a node published from default
 * as user-generated content — it stamps jmix:liveProperties and lists the written
 * properties in j:liveProperties, and the publication's ConflictResolver then skips those
 * properties for good, so a migrated field would never take a later publication into
 * account (#281). Disabling the listeners around the write is the core's own recipe for
 * system rewrites in live (jExperience's Migrator, the 7.3 rating patch).
 *
 * <p>The default pass keeps observation on: the runtime listeners maintain the editorial
 * invariants there, and the one listener a migration must dodge already checks
 * {@link ChoiceOptionsContentMigration#isMigrationWrite()}.
 *
 * <p>With observation off, the output-cache invalidation does not see the live changes
 * either: a live pass that rewrote anything flushes the output caches itself, so the
 * published forms render the migrated content without a republish.
 */
final class MigrationSessions {

    private MigrationSessions() {
    }

    /**
     * @param workspace "default" or "live"
     * @param pass      the migration pass; returns how many nodes it rewrote
     * @return the number of rewritten nodes
     */
    static int execute(String workspace, JCRCallback<Integer> pass) throws RepositoryException {
        return execute(JCRTemplate.getInstance(), CacheHelper::flushOutputCaches, workspace, pass);
    }

    /** Seams for the unit test: the template opening the system session, and the output-cache flush. */
    static int execute(JCRTemplate template, Runnable outputCacheFlush, String workspace, JCRCallback<Integer> pass)
            throws RepositoryException {
        boolean live = WORKSPACE_LIVE.equals(workspace);
        Integer rewritten = template.doExecuteWithSystemSessionAsUser(null, workspace, null, session -> {
            if (!live) {
                return pass.doInJCR(session);
            }
            JCRObservationManager.setAllEventListenersDisabled(Boolean.TRUE);
            try {
                return pass.doInJCR(session);
            } finally {
                JCRObservationManager.setAllEventListenersDisabled(Boolean.FALSE);
            }
        });
        int count = rewritten == null ? 0 : rewritten;
        if (live && count > 0) {
            outputCacheFlush.run();
        }
        return count;
    }
}
