package org.jahia.modules.formidable.engine.logic;

import org.jahia.services.content.DefaultEventListener;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRObservationManager;
import org.jahia.services.content.JCRTemplate;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import javax.jcr.observation.Event;
import javax.jcr.observation.EventIterator;
import java.util.Set;

import static org.jahia.modules.formidable.engine.util.FormidableJcrConstants.FORM_LOGIC_ELEMENT_MIXIN;
import static org.jahia.modules.formidable.engine.util.FormidableJcrConstants.FORM_NODE_TYPE;
import static org.jahia.modules.formidable.engine.util.FormidableJcrConstants.LOGICS_PROPERTY;
import static org.jahia.modules.formidable.engine.util.FormidableJcrConstants.LOGICS_SRC_NODE;

/**
 * Cleans up logic dependencies after a subtree duplication (copy/paste, import).
 *
 * Triggered when:
 * - a whole fmdb:form is imported or copied (e.g. site import, form duplication)
 * - a single fmdbmix:formLogicElement is copied from one form to another
 * - a copy is persisted through a regular session save path (for example GraphQL copyNode)
 *
 * Delegates to FormLogicSyncService.cleanupAfterDuplication which purges weakrefs
 * pointing outside the form boundary, preserves the JSON rules, then attempts
 * to rebuild weakrefs from sourceNodeId, an in-scope weakref, or sourceFieldName.
 *
 * Counterpart: FormLogicSyncListener handles normal authoring (logics property changes).
 */
@Component(service = DefaultEventListener.class, immediate = true)
public class FormDuplicationCleanupListener extends DefaultEventListener {
    private static final Logger log = LoggerFactory.getLogger(FormDuplicationCleanupListener.class);

    public FormDuplicationCleanupListener() {
        setOperationTypes(Set.of(
                Integer.valueOf(JCRObservationManager.SESSION_SAVE),
                Integer.valueOf(JCRObservationManager.IMPORT),
                Integer.valueOf(JCRObservationManager.WORKSPACE_COPY)
        ));
    }

    @Override
    public int getEventTypes() {
        return Event.NODE_ADDED;
    }

    @Override
    public String[] getNodeTypes() {
        return new String[]{FORM_NODE_TYPE, FORM_LOGIC_ELEMENT_MIXIN};
    }

    @Override
    public void onEvent(EventIterator events) {
        while (events.hasNext()) {
            Event event = events.nextEvent();
            try {
                String nodePath = event.getPath();
                JCRTemplate.getInstance().doExecuteWithSystemSessionAsUser(null, workspace, null, systemSession -> {
                    JCRNodeWrapper node = systemSession.getNode(nodePath);
                    if (!shouldProcessNode(node)) {
                        return null;
                    }

                    JCRNodeWrapper formNode = node.isNodeType(FORM_NODE_TYPE)
                            ? node
                            : FormLogicSyncService.findFormAncestor(node);

                    if (formNode != null && FormLogicSyncService.cleanupAfterDuplication(formNode)) {
                        systemSession.save();
                        log.info("[FormDuplicationCleanup] Cleaned up logic dependencies on '{}'", formNode.getPath());
                    }

                    return null;
                });
            } catch (RepositoryException e) {
                log.warn("[FormDuplicationCleanup] Cleanup failed: {}", e.getMessage());
            }
        }
    }

    static boolean shouldProcessNode(JCRNodeWrapper node) throws RepositoryException {
        if (node.isNodeType(FORM_LOGIC_ELEMENT_MIXIN)) {
            return hasLogicContent(node);
        }

        return node.isNodeType(FORM_NODE_TYPE) && containsLogicContent(node);
    }

    private static boolean containsLogicContent(JCRNodeWrapper node) throws RepositoryException {
        if (hasLogicContent(node)) {
            return true;
        }

        NodeIterator children = node.getNodes();
        while (children.hasNext()) {
            if (containsLogicContent((JCRNodeWrapper) children.nextNode())) {
                return true;
            }
        }

        return false;
    }

    private static boolean hasLogicContent(JCRNodeWrapper node) throws RepositoryException {
        return node.hasProperty(LOGICS_PROPERTY) || node.hasNode(LOGICS_SRC_NODE);
    }
}
