package org.jahia.modules.formidable.engine.logic;

import org.jahia.services.content.DefaultEventListener;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRObservationManager;
import org.jahia.services.content.JCRTemplate;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;
import javax.jcr.observation.Event;
import javax.jcr.observation.EventIterator;
import java.util.Set;

/**
 * Cleans up logic dependencies after a subtree duplication (copy/paste, import).
 *
 * Triggered when:
 * - a whole fmdb:form is imported or copied (e.g. site import, form duplication)
 * - a single fmdbmix:formLogicElement is copied from one form to another
 *
 * Delegates to FormLogicSyncService.cleanupAfterDuplication which purges weakrefs
 * pointing outside the form boundary, then re-syncs from sourceFieldName.
 *
 * Counterpart: FormLogicSyncListener handles normal authoring (logics property changes).
 */
@Component(service = DefaultEventListener.class, immediate = true)
public class FormDuplicationCleanupListener extends DefaultEventListener {
    private static final Logger log = LoggerFactory.getLogger(FormDuplicationCleanupListener.class);

    public FormDuplicationCleanupListener() {
        setOperationTypes(Set.of(
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
        return new String[]{"fmdb:form", "fmdbmix:formLogicElement"};
    }

    @Override
    public void onEvent(EventIterator events) {
        while (events.hasNext()) {
            Event event = events.nextEvent();
            try {
                String nodePath = event.getPath();
                JCRTemplate.getInstance().doExecuteWithSystemSessionAsUser(null, workspace, null, systemSession -> {
                    JCRNodeWrapper node = systemSession.getNode(nodePath);
                    JCRNodeWrapper formNode = node.isNodeType("fmdb:form")
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
}
