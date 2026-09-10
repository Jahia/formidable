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
 * options-source and date-bounds mixins lose their {@code fmdb:} prefix (#310). They were
 * the only prefixed ones — every other mixin property ({@code fieldKey}, {@code logics},
 * {@code msg*}, {@code min}, {@code max}) never had one. Each prefixed property still
 * present is copied under its unprefixed name and removed; {@code fmdb:options} is i18n
 * and lives on the {@code j:translation_*} subnodes, where it moves the same way.
 *
 * <p>The deprecated definitions stay in the CND for this one release, hidden, so that a
 * 0.4 export imported into 0.5 is still accepted and lands here; they leave with this
 * class in 0.6.
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

    /** The mixins whose nodes may carry a prefixed property. */
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
    /** The i18n property, stored on the translation subnodes. */
    static final String TRANSLATED_PROPERTY = "fmdb:options";
    static final String TRANSLATED_PROPERTY_RENAMED = "options";
    private static final String TRANSLATION_NODES_PATTERN = "j:translation_*";

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
                try {
                    if (migrateNode(session, node)) {
                        // One save per migrated node: a failure must never discard the
                        // nodes already migrated before it, nor poison the later saves.
                        session.save();
                        migrated++;
                    }
                } catch (RepositoryException e) {
                    log.error("[MixinPropertyNamesMigration] Could not migrate node '{}' in workspace '{}': {}",
                            node.getPath(), workspace, e.getMessage(), e);
                    session.refresh(false);
                }
            }
        }

        if (migrated > 0) {
            log.info("[MixinPropertyNamesMigration] Renamed the prefixed mixin properties of {} field(s) in workspace '{}'",
                    migrated, workspace);
        } else {
            log.debug("[MixinPropertyNamesMigration] No prefixed mixin property found in workspace '{}'", workspace);
        }
        return migrated;
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
                moveProperty(node.getProperty(rename.getKey()), node, rename.getValue());
            }
        }

        NodeIterator translations = node.getNodes(TRANSLATION_NODES_PATTERN);
        while (translations.hasNext()) {
            Node translation = translations.nextNode();
            if (translation.hasProperty(TRANSLATED_PROPERTY)) {
                if (!touched) {
                    session.checkout(node);
                    touched = true;
                }
                moveProperty(translation.getProperty(TRANSLATED_PROPERTY), translation, TRANSLATED_PROPERTY_RENAMED);
            }
        }

        if (touched) {
            log.info("[MixinPropertyNamesMigration] Renamed the prefixed properties of '{}'", node.getPath());
        }
        return touched;
    }

    /** Copies the value(s) under the new name, type preserved, and removes the old property. */
    private static void moveProperty(Property old, Node owner, String newName) throws RepositoryException {
        if (old.isMultiple()) {
            owner.setProperty(newName, old.getValues());
        } else {
            owner.setProperty(newName, old.getValue());
        }
        old.remove();
    }
}
