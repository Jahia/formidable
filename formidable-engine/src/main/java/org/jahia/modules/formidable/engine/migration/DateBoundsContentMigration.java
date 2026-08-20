package org.jahia.modules.formidable.engine.migration;

import org.jahia.services.content.JCRNodeIteratorWrapper;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRSessionWrapper;
import org.jahia.services.content.JCRTemplate;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;
import javax.jcr.query.Query;

/**
 * One-shot content migration for date and datetime fields: nodes stored before the
 * bound modes existed carry fixed 'min'/'max' values but no fmdb:minBoundMode /
 * fmdb:maxBoundMode. Each such bound is stamped with mode 'date' plus the matching
 * fixed-bound dynamic-fieldset mixin, so the editor reopens it as the fixed-date
 * choice with its calendar — the values themselves stay in place under their
 * historical names.
 *
 * Runs at module activation on BOTH workspaces (default and live) so published
 * forms keep rendering without a republish. Keyed on CONTENT state (a fixed value
 * is present and no mode is), NOT on the previously installed module version.
 * Re-running is a no-op once every bound carries a mode.
 */
@Component(immediate = true)
public class DateBoundsContentMigration {

    private static final Logger log = LoggerFactory.getLogger(DateBoundsContentMigration.class);

    private static final String MODE_FIXED_DATE = "date";
    private static final String MIN_MODE_PROPERTY = "fmdb:minBoundMode";
    private static final String MAX_MODE_PROPERTY = "fmdb:maxBoundMode";
    private static final int SAVE_BATCH_SIZE = 50;

    /** One bounds contract: the mixin carrying the modes, and its two fixed-bound fieldset mixins. */
    private record BoundsContract(String contractMixin, String fixedMinMixin, String fixedMaxMixin) {
    }

    private static final BoundsContract[] CONTRACTS = {
            new BoundsContract("fmdbmix:dateBounds", "fmdbmix:fixedMinDate", "fmdbmix:fixedMaxDate"),
            new BoundsContract("fmdbmix:datetimeBounds", "fmdbmix:fixedMinDatetime", "fmdbmix:fixedMaxDatetime")
    };

    @Activate
    public void activate() {
        for (String workspace : new String[]{"default", "live"}) {
            try {
                JCRTemplate.getInstance().doExecuteWithSystemSessionAsUser(null, workspace, null, session -> {
                    for (BoundsContract contract : CONTRACTS) {
                        migrateWorkspace(session, workspace, contract);
                    }
                    return null;
                });
            } catch (RepositoryException e) {
                log.error("[DateBoundsContentMigration] Migration failed in workspace '{}': {}", workspace, e.getMessage(), e);
            }
        }
    }

    private void migrateWorkspace(JCRSessionWrapper session, String workspace, BoundsContract contract)
            throws RepositoryException {
        Query query = session.getWorkspace().getQueryManager()
                .createQuery("SELECT * FROM [" + contract.contractMixin() + "]", Query.JCR_SQL2);
        JCRNodeIteratorWrapper nodes = (JCRNodeIteratorWrapper) query.execute().getNodes();

        int migrated = 0;
        int pendingSave = 0;
        while (nodes.hasNext()) {
            JCRNodeWrapper node = (JCRNodeWrapper) nodes.nextNode();
            try {
                if (migrateNode(session, node, contract)) {
                    migrated++;
                    pendingSave++;
                    if (pendingSave >= SAVE_BATCH_SIZE) {
                        session.save();
                        pendingSave = 0;
                    }
                }
            } catch (RepositoryException e) {
                log.error("[DateBoundsContentMigration] Could not migrate node '{}' in workspace '{}': {}",
                        node.getPath(), workspace, e.getMessage(), e);
            }
        }
        if (pendingSave > 0) {
            session.save();
        }

        if (migrated > 0) {
            log.info("[DateBoundsContentMigration] Stamped bound modes on {} field(s) of {} in workspace '{}'",
                    migrated, contract.contractMixin(), workspace);
        } else {
            log.debug("[DateBoundsContentMigration] No legacy bound found for {} in workspace '{}'",
                    contract.contractMixin(), workspace);
        }
    }

    /**
     * @return true when the node carried at least one legacy fixed bound and was stamped
     */
    private boolean migrateNode(JCRSessionWrapper session, JCRNodeWrapper node, BoundsContract contract)
            throws RepositoryException {
        // Residual definitions keep the historical 'min'/'max' values readable even
        // though their property definitions moved into the fixed-bound mixins.
        boolean legacyMin = !node.hasProperty(MIN_MODE_PROPERTY) && node.hasProperty("min");
        boolean legacyMax = !node.hasProperty(MAX_MODE_PROPERTY) && node.hasProperty("max");
        if (!legacyMin && !legacyMax) {
            return false;
        }

        session.checkout(node);
        if (legacyMin) {
            node.addMixin(contract.fixedMinMixin());
            node.setProperty(MIN_MODE_PROPERTY, MODE_FIXED_DATE);
        }
        if (legacyMax) {
            node.addMixin(contract.fixedMaxMixin());
            node.setProperty(MAX_MODE_PROPERTY, MODE_FIXED_DATE);
        }
        log.info("[DateBoundsContentMigration] Stamped '{}'", node.getPath());
        return true;
    }
}
