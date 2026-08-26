package org.jahia.modules.formidable.engine.migration;

import org.jahia.services.content.JCRNodeIteratorWrapper;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRSessionWrapper;
import org.jahia.services.content.JCRTemplate;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.Property;
import javax.jcr.RepositoryException;
import javax.jcr.Value;
import javax.jcr.query.Query;

/**
 * One-shot content migration for choice fields (fmdbmix:choiceField): moves the
 * legacy per-type option properties ('options' on fmdb:select, 'choices' on
 * fmdb:radio / fmdb:checkbox) to the unified 'fmdb:options' property declared by
 * fmdbmix:manualOptions, and stamps the node with that mixin plus
 * fmdb:optionsMode='manual' so existing forms keep their exact behavior.
 *
 * Runs at module activation on BOTH workspaces (default and live) so published
 * forms keep rendering without a republish. Keyed on CONTENT state (a legacy
 * property is present), NOT on the previously installed module version: the
 * elements <=0.3 to 0.4 upgrade goes through an uninstall/reinstall (groupId
 * change), so no version information survives it. Re-running is a no-op once no
 * legacy property remains.
 */
@Component(immediate = true)
public class ChoiceOptionsContentMigration {

    private static final Logger log = LoggerFactory.getLogger(ChoiceOptionsContentMigration.class);

    private static final String CHOICE_FIELD_MIXIN = "fmdbmix:choiceField";
    private static final String MANUAL_OPTIONS_MIXIN = "fmdbmix:manualOptions";
    private static final String OPTIONS_MODE_PROPERTY = "fmdb:optionsMode";
    private static final String OPTIONS_MODE_MANUAL = "manual";
    private static final String UNIFIED_OPTIONS_PROPERTY = "fmdb:options";
    private static final String[] LEGACY_PROPERTIES = {"choices", "options"};
    private static final String TRANSLATION_NODES_PATTERN = "j:translation_*";

    @Activate
    public void activate() {
        for (String workspace : new String[]{"default", "live"}) {
            try {
                JCRTemplate.getInstance().doExecuteWithSystemSessionAsUser(null, workspace, null, session -> {
                    migrateWorkspace(session, workspace);
                    return null;
                });
            } catch (RepositoryException e) {
                log.error("[ChoiceOptionsContentMigration] Migration failed in workspace '{}': {}", workspace, e.getMessage(), e);
            }
        }
    }

    private void migrateWorkspace(JCRSessionWrapper session, String workspace) throws RepositoryException {
        // Scoped to editorial content: module-bundled nodes under /modules belong to
        // their module and must not be rewritten from here.
        Query query = session.getWorkspace().getQueryManager()
                .createQuery("SELECT * FROM [" + CHOICE_FIELD_MIXIN + "]"
                        + " WHERE ISDESCENDANTNODE('/sites')", Query.JCR_SQL2);
        JCRNodeIteratorWrapper nodes = (JCRNodeIteratorWrapper) query.execute().getNodes();

        int migrated = 0;
        while (nodes.hasNext()) {
            JCRNodeWrapper node = (JCRNodeWrapper) nodes.nextNode();
            try {
                if (migrateNode(session, node)) {
                    // One save per migrated node: a failure must never discard the
                    // nodes already migrated before it, nor poison the later saves.
                    session.save();
                    migrated++;
                }
            } catch (RepositoryException e) {
                log.error("[ChoiceOptionsContentMigration] Could not migrate node '{}' in workspace '{}': {}",
                        node.getPath(), workspace, e.getMessage(), e);
                // Drop the half-applied changes, or every later save would re-throw them.
                session.refresh(false);
            }
        }

        if (migrated > 0) {
            log.info("[ChoiceOptionsContentMigration] Migrated {} choice field(s) to {} in workspace '{}'",
                    migrated, MANUAL_OPTIONS_MIXIN, workspace);
        } else {
            log.debug("[ChoiceOptionsContentMigration] No legacy choice field found in workspace '{}'", workspace);
        }
    }

    /**
     * @return true when the node carried a legacy property and was migrated
     */
    private boolean migrateNode(JCRSessionWrapper session, JCRNodeWrapper node) throws RepositoryException {
        boolean touched = false;

        // Legacy properties were i18n: their values live on the j:translation_* subnodes,
        // where residual definitions keep them readable even after the CND removal.
        NodeIterator translations = node.getNodes(TRANSLATION_NODES_PATTERN);
        while (translations.hasNext()) {
            Node translation = translations.nextNode();
            for (String legacyProperty : LEGACY_PROPERTIES) {
                if (translation.hasProperty(legacyProperty)) {
                    if (!touched) {
                        session.checkout(node);
                        touched = true;
                    }
                    moveProperty(translation.getProperty(legacyProperty), translation);
                }
            }
        }

        if (touched) {
            node.addMixin(MANUAL_OPTIONS_MIXIN);
            node.setProperty(OPTIONS_MODE_PROPERTY, OPTIONS_MODE_MANUAL);
            log.info("[ChoiceOptionsContentMigration] Migrated '{}'", node.getPath());
        }

        return touched;
    }

    private void moveProperty(Property legacy, Node translation) throws RepositoryException {
        if (legacy.isMultiple()) {
            Value[] values = legacy.getValues();
            translation.setProperty(UNIFIED_OPTIONS_PROPERTY, values);
        } else {
            translation.setProperty(UNIFIED_OPTIONS_PROPERTY, new Value[]{legacy.getValue()});
        }
        legacy.remove();
    }
}
