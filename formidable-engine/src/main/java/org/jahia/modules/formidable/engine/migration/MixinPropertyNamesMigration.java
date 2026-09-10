package org.jahia.modules.formidable.engine.migration;

import org.jahia.services.content.JCRNodeIteratorWrapper;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRSessionWrapper;
import org.jahia.services.observation.JahiaEventListener;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.Property;
import javax.jcr.RepositoryException;
import javax.jcr.query.Query;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * One-shot content migration for fields stored by 0.4.0: the properties carried by the
 * options-source and date-bounds mixins, and the select's empty-option label, lose their
 * {@code fmdb:} prefix (#310). They were the only prefixed properties of the model — every
 * other one ({@code fieldKey}, {@code logics}, {@code msg*}, {@code min}, {@code max}) never
 * had one. Each prefixed property still present is copied under its unprefixed name and
 * removed; the i18n ones ({@code fmdb:options}, {@code fmdb:optionsEmptyLabel}) live on the
 * translation subnodes, where they move the same way. Where the unprefixed name already
 * holds a value — a 0.4 export imported, then edited in the editor, before this ran — the
 * current value wins and the prefixed one is dropped: a migration never moves content
 * backwards.
 *
 * <p>The deprecated definitions stay in the CND for this one release, hidden, so that a
 * 0.4 export imported into 0.5 is still accepted and lands here; for the same reason the
 * JCR-level {@code mandatory} of the renamed properties is lifted for this release (the
 * editor keeps requiring them through its fieldset overrides) — a mandatory property added to
 * a deployed type is also a change Jahia's definitions checker refuses to deploy. Both come
 * back to normal in 0.6, when this class leaves.
 *
 * <p>Runs at module activation on BOTH workspaces (default and live, the live pass through
 * {@link MigrationSessions}) and again on an elements redeploy. Keyed on content state:
 * re-running is a no-op once no prefixed property remains. Order-independent with
 * {@link ChoiceOptionsContentMigration}: a 0.3 node carries no prefixed property, a 0.4
 * node already carries the manual-options mixin.
 *
 * <p>Lifecycle: startup migration introduced in 0.5.0, to be removed in 0.6 — see
 * docs/administration/upgrade-notes.md, "Startup migrations".
 */
@Component(service = {MixinPropertyNamesMigration.class, JahiaEventListener.class}, immediate = true)
public class MixinPropertyNamesMigration extends ElementsRedeployRetriggeredMigration {

    private static final Logger log = LoggerFactory.getLogger(MixinPropertyNamesMigration.class);

    /** The mixins whose nodes may carry a prefixed property (fmdb:select carries the first through fmdbmix:choiceField). */
    private static final String[] CARRIER_MIXINS = {"fmdbmix:optionsSource", "fmdbmix:dateBounds", "fmdbmix:datetimeBounds"};
    /** Node-level properties, prefixed name to unprefixed name. */
    static final Map<String, String> NODE_PROPERTIES = Map.ofEntries(
            Map.entry("fmdb:optionsMode", "optionsMode"),
            Map.entry("fmdb:optionsSourceKey", "optionsSourceKey"),
            Map.entry("fmdb:optionsRootCategory", "optionsRootCategory"),
            Map.entry("fmdb:optionsRootNode", "optionsRootNode"),
            Map.entry("fmdb:optionsNodeType", "optionsNodeType"),
            Map.entry("fmdb:minBoundMode", "minBoundMode"),
            Map.entry("fmdb:maxBoundMode", "maxBoundMode"),
            Map.entry("fmdb:minRelativeAmount", "minRelativeAmount"),
            Map.entry("fmdb:minRelativeUnit", "minRelativeUnit"),
            Map.entry("fmdb:maxRelativeAmount", "maxRelativeAmount"),
            Map.entry("fmdb:maxRelativeUnit", "maxRelativeUnit"));
    /** The i18n properties, stored on the translation subnodes, prefixed name to unprefixed name. */
    static final Map<String, String> TRANSLATED_PROPERTIES = Map.of(
            "fmdb:options", "options",
            "fmdb:optionsEmptyLabel", "optionsEmptyLabel");

    @Activate
    public void activate() {
        run();
    }

    @Override
    void run() {
        migrateBothWorkspaces(this::migrateWorkspace);
    }

    /** @return the number of migrated fields */
    private int migrateWorkspace(JCRSessionWrapper session, String workspace) throws RepositoryException {
        int migrated = 0;
        int failed = 0;
        int deferred = 0;
        Set<String> visited = new HashSet<>();
        for (String mixin : CARRIER_MIXINS) {
            // Scoped to editorial content: module-bundled nodes under /modules belong to
            // their module and must not be rewritten from here.
            Query query = session.getWorkspace().getQueryManager()
                    .createQuery("SELECT * FROM [" + mixin + "] WHERE ISDESCENDANTNODE('/sites')", Query.JCR_SQL2);
            JCRNodeIteratorWrapper nodes = (JCRNodeIteratorWrapper) query.execute().getNodes();
            while (nodes.hasNext()) {
                JCRNodeWrapper node = (JCRNodeWrapper) nodes.nextNode();
                if (!visited.add(node.getIdentifier())) {
                    continue;
                }
                if (!definitionsReady(node)) {
                    deferred++;
                    continue;
                }
                try {
                    if (migrateNode(session, node)) {
                        // One save per migrated node: a failure must never discard the
                        // nodes already migrated before it, nor poison the later saves.
                        session.save();
                        migrated++;
                        // Reported once the save went through: this line is what the upgrade
                        // note tells the administrator to look for.
                        log.info("[MixinPropertyNamesMigration] Renamed the prefixed properties of '{}'", node.getPath());
                    }
                } catch (RepositoryException e) {
                    failed++;
                    log.error("[MixinPropertyNamesMigration] Could not migrate node '{}' in workspace '{}': {}",
                            node.getPath(), workspace, e.getMessage(), e);
                    session.refresh(false);
                }
            }
        }

        if (migrated > 0) {
            log.info("[MixinPropertyNamesMigration] Renamed the prefixed mixin properties of {} field(s) in workspace '{}'",
                    migrated, workspace);
        }
        if (deferred > 0) {
            log.info("[MixinPropertyNamesMigration] {} field(s) in workspace '{}' wait for the formidable-elements (re)deploy:"
                    + " their types do not know the unprefixed names yet (engine upgraded first); the redeploy rerun renames them",
                    deferred, workspace);
        }
        if (failed > 0) {
            log.warn("[MixinPropertyNamesMigration] {} field(s) still carry prefixed properties in workspace '{}' after the errors above;"
                    + " the next engine start or elements redeploy retries them", failed, workspace);
        } else if (migrated == 0 && deferred == 0) {
            log.debug("[MixinPropertyNamesMigration] No prefixed mixin property found in workspace '{}'", workspace);
        }
        return migrated;
    }

    /**
     * On the engine-first upgrade path the element types keep their cached definitions until
     * formidable-elements is redeployed, and a write under a name they do not know yet fails
     * ("Couldn't find definition for property"): such a node is left, quietly, to the redeploy
     * rerun. The translated properties live on jnt:translation, whose residual definitions
     * accept any name, so only the node-level targets are checked.
     *
     * @return true when every unprefixed name this node needs is defined on it
     */
    boolean definitionsReady(JCRNodeWrapper node) throws RepositoryException {
        for (Map.Entry<String, String> rename : NODE_PROPERTIES.entrySet()) {
            if (node.hasProperty(rename.getKey()) && node.getApplicablePropertyDefinition(rename.getValue()) == null) {
                return false;
            }
        }
        return true;
    }

    /**
     * @return true when the node carried at least one prefixed property and was migrated
     */
    boolean migrateNode(JCRSessionWrapper session, JCRNodeWrapper node) throws RepositoryException {
        boolean touched = false;

        for (Map.Entry<String, String> rename : NODE_PROPERTIES.entrySet()) {
            if (node.hasProperty(rename.getKey())) {
                if (!touched) {
                    session.checkout(node);
                    touched = true;
                }
                moveProperty(node, rename.getKey(), rename.getValue());
            }
        }

        // getI18Ns answers whether or not the session is bound to a locale, unlike a
        // getNodes("j:translation_*") walk (see ManualOptionsLanguageSync).
        NodeIterator translations = node.getI18Ns();
        while (translations.hasNext()) {
            Node translation = translations.nextNode();
            for (Map.Entry<String, String> rename : TRANSLATED_PROPERTIES.entrySet()) {
                if (translation.hasProperty(rename.getKey())) {
                    if (!touched) {
                        session.checkout(node);
                        touched = true;
                    }
                    moveProperty(translation, rename.getKey(), rename.getValue());
                }
            }
        }

        return touched;
    }

    /**
     * Copies the value(s) under the new name, type preserved, and removes the old property —
     * unless the new name already holds a value, which is then the more recent one (written
     * by the editor after a 0.4 import) and wins: only the prefixed property goes.
     */
    private static void moveProperty(Node owner, String oldName, String newName) throws RepositoryException {
        Property old = owner.getProperty(oldName);
        if (owner.hasProperty(newName)) {
            log.info("[MixinPropertyNamesMigration] '{}' already carries {}: its 0.4 value under {} is dropped, the current one kept",
                    owner.getPath(), newName, oldName);
        } else if (old.isMultiple()) {
            owner.setProperty(newName, old.getValues());
        } else {
            owner.setProperty(newName, old.getValue());
        }
        old.remove();
    }
}
