package org.jahia.modules.formidable.engine.migration;

import org.jahia.services.content.JCRNodeIteratorWrapper;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRSessionWrapper;
import org.jahia.services.observation.JahiaEventListener;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;
import javax.jcr.nodetype.NodeType;
import javax.jcr.query.Query;
import java.util.Map;

/**
 * One-shot content migration for the text and textarea fields saved before 0.5.0 (#359). Their
 * "advanced settings" — mask, pattern, title, read-only, autofocus, disabled, spell check, wrap,
 * and the hidden form/dirname/size/cols — were carried by a mixin the editor added behind a
 * switch ({@code fmdbmix:advancedInputTextSettings}, {@code fmdbmix:advancedTextareaSettings}).
 * Since 0.5.0 each mixin is a supertype of its field type: every field has the settings without
 * carrying the mixin. A field saved earlier still lists it in {@code jcr:mixinTypes} — harmless,
 * Jackrabbit accepts a mixin the primary type already includes — and this migration drops that
 * redundant entry. The values stay: the resulting effective type still defines every property.
 *
 * <p>Why the properties could not simply move to the field types: a node whose primary type and
 * mixin declare the same property has no effective node type for Jackrabbit ("ambiguous property
 * definition"), and every write to it fails, this very removal included — verified on 8.2.4. The
 * supertype, name kept, is what keeps the fields saved by earlier versions writable.
 *
 * <p>Runs at module activation on BOTH workspaces (default and live, the live pass through
 * {@link MigrationSessions}) and again on an elements redeploy: on the engine-first upgrade path
 * the field type does not include the mixin until formidable-elements is redeployed, and removing
 * it before would drop the values with it, so such a node waits for the redeploy rerun. Keyed on
 * content state: re-running is a no-op once no field lists the mixin. The carriers are found by
 * their {@code jcr:mixinTypes} value, not by the mixin as a query type, so the lookup asks nothing
 * of the type registry.
 *
 * <p>No Cypress spec exercises this one, unlike its siblings: the legacy state — a field listing a
 * mixin its type includes — cannot be produced on a fresh instance (Jackrabbit silently ignores
 * {@code addMixin} of a type the primary type already includes, and an import adds mixins the same
 * way). It was run by hand on an upgraded instance holding such fields (PR #359: values kept,
 * translated title included, nothing left live-owned); the helpers are unit-tested.
 *
 * <p>Lifecycle: startup migration introduced in 0.5.0, to be removed in 0.6 — see
 * docs/administration/upgrade-notes.md, "Startup migrations".
 */
@Component(service = {AdvancedSettingsMixinMigration.class, JahiaEventListener.class}, immediate = true)
public class AdvancedSettingsMixinMigration extends ElementsRedeployRetriggeredMigration {

    private static final Logger log = LoggerFactory.getLogger(AdvancedSettingsMixinMigration.class);

    /** Field type to the mixin it includes as a supertype since 0.5.0, and that its older fields may still list. */
    static final Map<String, String> RETIRED_MIXINS = Map.of(
            "fmdb:inputText", "fmdbmix:advancedInputTextSettings",
            "fmdb:textarea", "fmdbmix:advancedTextareaSettings");

    @Activate
    public void activate() {
        run();
    }

    @Override
    void run() {
        migrateBothWorkspaces(this::migrateWorkspace);
    }

    /** What became of one carrier node. */
    private enum Outcome { MIGRATED, DEFERRED, FAILED }

    /** @return the number of migrated fields */
    private int migrateWorkspace(JCRSessionWrapper session, String workspace) throws RepositoryException {
        int[] counts = new int[Outcome.values().length];
        for (Map.Entry<String, String> retired : RETIRED_MIXINS.entrySet()) {
            JCRNodeIteratorWrapper nodes = carriersOf(session, retired.getKey(), retired.getValue());
            while (nodes.hasNext()) {
                counts[migrateOne(session, (JCRNodeWrapper) nodes.nextNode(), retired.getValue(), workspace).ordinal()]++;
            }
        }
        logSummary(workspace, counts[Outcome.MIGRATED.ordinal()], counts[Outcome.DEFERRED.ordinal()], counts[Outcome.FAILED.ordinal()]);
        return counts[Outcome.MIGRATED.ordinal()];
    }

    /**
     * The fields of the type still listing the mixin, scoped to editorial content (module-bundled nodes
     * under /modules belong to their module). Matched on the jcr:mixinTypes value: a query on the mixin
     * as a type would depend on its registration, which the migration must not assume.
     */
    private static JCRNodeIteratorWrapper carriersOf(JCRSessionWrapper session, String type, String mixin) throws RepositoryException {
        Query query = session.getWorkspace().getQueryManager().createQuery(
                "SELECT * FROM [" + type + "] AS f WHERE f.[jcr:mixinTypes] = '" + mixin + "' AND ISDESCENDANTNODE(f, '/sites')",
                Query.JCR_SQL2);
        return (JCRNodeIteratorWrapper) query.execute().getNodes();
    }

    private Outcome migrateOne(JCRSessionWrapper session, JCRNodeWrapper node, String mixin, String workspace) {
        try {
            if (!includedBySupertype(node.getPrimaryNodeType(), mixin)) {
                return Outcome.DEFERRED;
            }
            dropMixin(session, node, mixin);
            // One save per migrated node: a failure must never discard the nodes already
            // migrated before it, nor poison the later saves.
            session.save();
            // Reported once the save went through: this line is what the upgrade note tells
            // the administrator to look for.
            log.info("[AdvancedSettingsMixinMigration] Dropped the redundant {} from '{}'", mixin, node.getPath());
            return Outcome.MIGRATED;
        } catch (RepositoryException e) {
            log.error("[AdvancedSettingsMixinMigration] Could not migrate node '{}' in workspace '{}': {}",
                    node.getPath(), workspace, e.getMessage(), e);
            refreshQuietly(session);
            return Outcome.FAILED;
        }
    }

    /**
     * Whether the field's own type already includes the mixin as a supertype — the 0.5.0
     * formidable-elements. Before that redeploy the mixin is the only type defining the settings:
     * removing it would drop their values, so the node is left, quietly, to the redeploy rerun.
     */
    static boolean includedBySupertype(NodeType primaryType, String mixin) {
        return primaryType.isNodeType(mixin);
    }

    /**
     * Removes the mixin from the node's own list; the resulting effective type, the field type
     * included, still defines every property, so Jahia keeps the values (it drops only the
     * properties no remaining type defines).
     */
    static void dropMixin(JCRSessionWrapper session, JCRNodeWrapper node, String mixin) throws RepositoryException {
        session.checkout(node);
        node.removeMixin(mixin);
    }

    /** Drops the half-applied changes, or every later save would re-throw them. */
    private static void refreshQuietly(JCRSessionWrapper session) {
        try {
            session.refresh(false);
        } catch (RepositoryException e) {
            log.warn("[AdvancedSettingsMixinMigration] Could not discard the pending changes: {}", e.getMessage(), e);
        }
    }

    private static void logSummary(String workspace, int migrated, int deferred, int failed) {
        if (migrated > 0) {
            log.info("[AdvancedSettingsMixinMigration] Dropped the redundant advanced-settings mixin from {} field(s) in workspace '{}'",
                    migrated, workspace);
        }
        if (deferred > 0) {
            log.info("[AdvancedSettingsMixinMigration] {} field(s) in workspace '{}' wait for the formidable-elements (re)deploy:"
                    + " their type does not include the settings yet (engine upgraded first); the redeploy rerun drops the mixin",
                    deferred, workspace);
        }
        if (failed > 0) {
            log.warn("[AdvancedSettingsMixinMigration] {} field(s) still list the advanced-settings mixin in workspace '{}' after the errors above;"
                    + " the next engine start or elements redeploy retries them", failed, workspace);
        } else if (migrated == 0 && deferred == 0) {
            log.debug("[AdvancedSettingsMixinMigration] No field lists an advanced-settings mixin in workspace '{}'", workspace);
        }
    }
}
