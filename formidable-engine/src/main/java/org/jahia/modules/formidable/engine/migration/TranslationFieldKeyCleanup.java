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
import javax.jcr.RepositoryException;
import javax.jcr.query.Query;

import static org.jahia.modules.formidable.engine.util.FormidableJcrConstants.FIELD_KEY_PROPERTY;
import static org.jahia.modules.formidable.engine.util.FormidableJcrConstants.FORM_LOGIC_ELEMENT_MIXIN;

/**
 * One-shot content cleanup: form elements created before the assignment listener
 * filtered translation events carry a stray fieldKey on each of their
 * j:translation_* subnodes (one random key per language, unrelated to the element's
 * own key). The value has no definition on jnt:translation, is never read, and makes
 * content-integrity scans fail on every such element. This removes it.
 *
 * Runs at module activation on BOTH workspaces (default and live): published
 * translations carry the same value. Keyed on CONTENT state, NOT on the previously
 * installed module version. Re-running is a no-op once every translation is clean.
 *
 * <p>Lifecycle: startup migration introduced in 0.4.0 (#215), to be removed in 0.5 — see
 * docs/upgrade-notes.md, "Startup migrations".
 */
@Component(immediate = true)
public class TranslationFieldKeyCleanup {

    private static final Logger log = LoggerFactory.getLogger(TranslationFieldKeyCleanup.class);

    @Activate
    public void activate() {
        for (String workspace : new String[]{"default", "live"}) {
            try {
                JCRTemplate.getInstance().doExecuteWithSystemSessionAsUser(null, workspace, null, session -> {
                    cleanWorkspace(session, workspace);
                    return null;
                });
            } catch (RepositoryException e) {
                log.error("[TranslationFieldKeyCleanup] Cleanup failed in workspace '{}': {}",
                        workspace, e.getMessage(), e);
            }
        }
    }

    private void cleanWorkspace(JCRSessionWrapper session, String workspace) throws RepositoryException {
        // Scoped to editorial content: module-bundled nodes under /modules belong to
        // their module and must not be rewritten from here.
        Query query = session.getWorkspace().getQueryManager()
                .createQuery("SELECT * FROM [" + FORM_LOGIC_ELEMENT_MIXIN + "]"
                        + " WHERE ISDESCENDANTNODE('/sites')", Query.JCR_SQL2);
        JCRNodeIteratorWrapper nodes = (JCRNodeIteratorWrapper) query.execute().getNodes();

        int cleaned = 0;
        while (nodes.hasNext()) {
            JCRNodeWrapper node = (JCRNodeWrapper) nodes.nextNode();
            try {
                int removed = cleanNode(node);
                if (removed > 0) {
                    // One save per cleaned element: a failure must never discard the
                    // translations already cleaned before it.
                    session.save();
                    cleaned += removed;
                }
            } catch (RepositoryException e) {
                log.error("[TranslationFieldKeyCleanup] Could not clean node '{}' in workspace '{}': {}",
                        node.getPath(), workspace, e.getMessage(), e);
                // Drop the half-applied changes, or every later save would re-throw them.
                session.refresh(false);
            }
        }

        if (cleaned > 0) {
            log.info("[TranslationFieldKeyCleanup] Removed {} stray fieldKey(s) from translation nodes in workspace '{}'",
                    cleaned, workspace);
        } else {
            log.debug("[TranslationFieldKeyCleanup] No stray fieldKey found on translation nodes in workspace '{}'",
                    workspace);
        }
    }

    /**
     * @return the number of translation nodes of this element that carried a fieldKey
     */
    static int cleanNode(JCRNodeWrapper node) throws RepositoryException {
        int removed = 0;
        // The translations come back as bare Jackrabbit nodes: a definition-less value
        // is visible there, unlike through the wrapper API.
        NodeIterator translations = node.getI18Ns();
        while (translations.hasNext()) {
            Node translation = translations.nextNode();
            if (translation.hasProperty(FIELD_KEY_PROPERTY)) {
                translation.getProperty(FIELD_KEY_PROPERTY).remove();
                removed++;
            }
        }
        return removed;
    }
}
