package org.jahia.modules.formidable.engine.migration.v05;

import org.jahia.modules.formidable.engine.api.FmdbMixin;
import org.jahia.modules.formidable.engine.migration.RemovedIn;
import org.jahia.modules.formidable.engine.migration.common.ElementsRedeployRetriggeredMigration;
import org.jahia.modules.formidable.engine.migration.common.MigrationSessions;
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
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One-shot content migration for the nodes saved before 0.5.0 that still list, in {@code jcr:mixinTypes},
 * a mixin their type has since taken as a supertype. Two families: the text, textarea, number and range
 * fields with their "advanced settings" mixin (#359, #361 — mask, pattern, title, read-only, autofocus,
 * disabled, spell check, wrap and the hidden form/dirname/size/cols, once behind a switch of the editor,
 * {@code fmdbmix:advanced<Type>Settings}), and the field actions with {@code fmdbmix:fieldActionFeedback}
 * (#365 — the visitor's message, trigger, severity and outage rule, once attached to the marker with
 * {@code extends} and kept always activated). Since 0.5.0 each mixin is a supertype of the field type, or
 * of the marker every field-action type takes: every node has the settings without carrying the mixin. A
 * node saved earlier still lists it — harmless, Jackrabbit accepts a mixin the primary type already
 * includes — and this migration drops that redundant entry. The values stay: the resulting effective type
 * still defines every property.
 *
 * <p>Why the properties could not simply move to the types: a node whose primary type and mixin declare
 * the same property has no effective node type for Jackrabbit ("ambiguous property definition"), and
 * every write to it fails, this very removal included — verified on 8.2.4. The supertype, name kept, is
 * what keeps the nodes saved by earlier versions writable.
 *
 * <p>Runs at module activation on BOTH workspaces (default and live, the live pass through
 * {@link MigrationSessions}) and again on a module redeploy: on the engine-first upgrade path a node's
 * type does not include its mixin until the module declaring the type is redeployed, and removing it
 * before would drop the values with it, so such a node waits for the redeploy rerun. For a field that
 * module is formidable-elements; for a field action it is whichever module declares the action's type —
 * the marker is the engine's own, but a type registered by another module keeps the supertypes it
 * resolved then, whatever the engine declares afterwards (measured on 8.2.4: after the engine redeploy,
 * {@code fmdbsample:blockedWordsAction} still reported no feedback supertype until the samples module was
 * redeployed), hence the rerun on any module's redeploy. Keyed on content state: re-running is a no-op
 * once no node lists a retired mixin. The carriers are found by their {@code jcr:mixinTypes} value, not
 * by the mixin as a query type, so the lookup asks nothing of the registry about the mixin. It does need
 * the node type it selects from: a type the registry does not know yet — {@code fmdb:inputNumber} while
 * the formidable-elements of 0.3 still runs, on the direct 0.3 upgrade path — holds no node, and is
 * skipped instead of failing the whole workspace.
 *
 * <p>No Cypress spec exercises this one, unlike its siblings: the legacy state — a node listing a mixin its
 * type includes — cannot be produced on a fresh instance (Jackrabbit silently ignores {@code addMixin} of
 * a type the primary type already includes, and an import adds mixins the same way). It was run by hand on
 * an upgraded instance holding such fields (PR #359: values kept, translated title included, nothing left
 * live-owned); the helpers are unit-tested.
 *
 * <p>Lifecycle: introduced in 0.5.0 (#359, #361, #368), removed in 0.6 with the 0.5.0 wave of startup migrations
 * — see docs/administration/upgrade-notes.md, "Startup migrations".
 */
@Component(service = {RedundantMixinMigration.class, JahiaEventListener.class}, immediate = true)
@RemovedIn("0.6")
public class RedundantMixinMigration extends ElementsRedeployRetriggeredMigration {

    private static final Logger log = LoggerFactory.getLogger(RedundantMixinMigration.class);

    /**
     * The type whose nodes may still list the mixin, to the mixin it includes as a supertype since 0.5.0 —
     * a field type to its advanced-settings mixin, the field-action marker to the feedback settings.
     */
    static final Map<String, String> RETIRED_MIXINS = Map.of(
            "fmdb:inputText", "fmdbmix:advancedInputTextSettings",
            "fmdb:textarea", "fmdbmix:advancedTextareaSettings",
            "fmdb:inputNumber", "fmdbmix:advancedInputNumberSettings",
            "fmdb:inputRange", "fmdbmix:advancedInputRangeSettings",
            FmdbMixin.FIELD_ACTION, FmdbMixin.FIELD_ACTION_FEEDBACK);

    @Activate
    public void activate() {
        run();
    }

    @Override
    protected void run() {
        migrateBothWorkspaces(this::migrateWorkspace);
    }

    /**
     * Any module: a field-action type may be declared by any of them, and its redeploy is what makes the
     * type include the feedback settings (see the class comment). The run is two queries per workspace
     * when nothing is left to do.
     */
    @Override
    protected boolean retriggeredBy(String moduleId) {
        return true;
    }

    /** @return the number of migrated nodes */
    private int migrateWorkspace(JCRSessionWrapper session, String workspace) throws RepositoryException {
        Tally tally = new Tally();
        for (Map.Entry<String, String> retired : retiredMixinsOfRegisteredTypes(session, workspace).entrySet()) {
            JCRNodeIteratorWrapper nodes = carriersOf(session, retired.getKey(), retired.getValue());
            while (nodes.hasNext()) {
                tally.add(migrateOne(session, (JCRNodeWrapper) nodes.nextNode(), retired.getValue(), workspace));
            }
        }
        logSummary(workspace, tally);
        return tally.of(Outcome.MIGRATED);
    }

    /**
     * The entries of {@link #RETIRED_MIXINS} whose type the registry knows. Querying an unregistered type
     * throws, and no node can be of a type that is not registered: an older formidable-elements still
     * running (no {@code fmdb:inputNumber} in 0.3) leaves that type to the rerun its redeploy fires.
     */
    static Map<String, String> retiredMixinsOfRegisteredTypes(JCRSessionWrapper session, String workspace)
            throws RepositoryException {
        Map<String, String> registered = new LinkedHashMap<>();
        for (Map.Entry<String, String> retired : RETIRED_MIXINS.entrySet()) {
            if (session.getWorkspace().getNodeTypeManager().hasNodeType(retired.getKey())) {
                registered.put(retired.getKey(), retired.getValue());
            } else {
                log.debug("[RedundantMixinMigration] Type {} is not registered, nothing to migrate for it in workspace '{}'",
                        retired.getKey(), workspace);
            }
        }
        return registered;
    }

    /**
     * The nodes of the type still listing the mixin, scoped to editorial content (module-bundled nodes
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
        return migrateOne(session, node, workspace, "Dropped the redundant " + mixin + " from", (s, n) -> {
            if (!includedBySupertype(n.getPrimaryNodeType(), mixin)) {
                return Outcome.DEFERRED;
            }
            dropMixin(s, n, mixin);
            return Outcome.MIGRATED;
        });
    }

    /**
     * Whether the node's own type already includes the mixin as a supertype — the 0.5.0 formidable-elements
     * for a field, a module redeployed after the engine for a field action. Before that redeploy the mixin
     * is the only type defining the settings: removing it would drop their values, so the node is left,
     * quietly, to the redeploy rerun.
     */
    static boolean includedBySupertype(NodeType primaryType, String mixin) {
        return primaryType.isNodeType(mixin);
    }

    /**
     * Removes the mixin from the node's own list; the resulting effective type, the node's type
     * included, still defines every property, so Jahia keeps the values (it drops only the
     * properties no remaining type defines).
     */
    static void dropMixin(JCRSessionWrapper session, JCRNodeWrapper node, String mixin) throws RepositoryException {
        session.checkout(node);
        node.removeMixin(mixin);
    }

    private void logSummary(String workspace, Tally tally) {
        logSummary(workspace, tally,
                "[RedundantMixinMigration] Dropped a redundant mixin from {} node(s) in workspace '{}'",
                "[RedundantMixinMigration] {} node(s) in workspace '{}' wait for the (re)deploy of the module declaring their type"
                        + " (formidable-elements for a field, the module of a field-action type): the type does not include the settings"
                        + " yet (engine upgraded first); that redeploy or the next engine start drops the mixin",
                "[RedundantMixinMigration] {} node(s) still list a mixin their type includes in workspace '{}' after the errors above;"
                        + " the next engine start or elements redeploy retries them",
                "[RedundantMixinMigration] No node lists a mixin its type includes in workspace '{}'");
    }
}
