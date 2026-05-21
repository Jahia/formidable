package org.jahia.modules.formidable.engine.permissions;

import org.jahia.services.content.DefaultEventListener;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRTemplate;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;
import javax.jcr.observation.Event;
import javax.jcr.observation.EventIterator;
import java.util.HashSet;
import java.util.Set;

/**
 * Detects publication of fmdb:form nodes and ACL changes in the live workspace,
 * then triggers ACL synchronisation toward the corresponding fmdb:formResults.
 *
 * Triggers:
 * - NODE_ADDED on fmdb:form → first publication of a form
 * - NODE_ADDED on jnt:ace  → ACL entry added/republished under a form
 * - PROPERTY_CHANGED on jnt:ace → ACL entry modified
 * - NODE_REMOVED on jnt:ace → ACL entry removed
 */
@Component(service = DefaultEventListener.class, immediate = true)
public class FormPublicationAclSyncListener extends DefaultEventListener {

    private static final Logger log = LoggerFactory.getLogger(FormPublicationAclSyncListener.class);

    public FormPublicationAclSyncListener() {
        workspace = "live";
    }

    @Override
    public int getEventTypes() {
        return Event.NODE_ADDED | Event.NODE_REMOVED | Event.PROPERTY_ADDED | Event.PROPERTY_CHANGED;
    }

    @Override
    public String[] getNodeTypes() {
        return new String[]{"fmdb:form", "jnt:ace"};
    }

    @Override
    public void onEvent(EventIterator events) {
        Set<String> processedFormIdentifiers = new HashSet<>();

        while (events.hasNext()) {
            Event event = events.nextEvent();
            try {
                String path = event.getPath();

                // For property events, strip the property name to get the node path
                if (event.getType() == Event.PROPERTY_ADDED || event.getType() == Event.PROPERTY_CHANGED) {
                    path = path.substring(0, path.lastIndexOf('/'));
                }

                // For NODE_REMOVED, the node no longer exists; we must derive the form path from the event path
                if (event.getType() == Event.NODE_REMOVED) {
                    String formPath = resolveFormPathFromAcePath(path);
                    if (formPath == null) {
                        continue;
                    }

                    syncFormByPath(formPath, processedFormIdentifiers);
                    continue;
                }

                final String nodePath = path;
                JCRTemplate.getInstance().doExecuteWithSystemSessionAsUser(null, "live", null, session -> {
                    if (!session.nodeExists(nodePath)) {
                        return null;
                    }

                    JCRNodeWrapper node = session.getNode(nodePath);
                    JCRNodeWrapper formNode = resolveFormNode(node);
                    if (formNode == null) {
                        return null;
                    }

                    String formId = formNode.getIdentifier();
                    if (processedFormIdentifiers.add(formId)) {
                        FormResultsAclSyncService.syncAcl(formNode, session);
                        session.save();
                        log.debug("[AclSyncListener] Synced ACL for form '{}'", formNode.getPath());
                    }

                    return null;
                });
            } catch (RepositoryException e) {
                log.warn("[AclSyncListener] Failed to process event: {}", e.getMessage());
            }
        }
    }

    private static JCRNodeWrapper resolveFormNode(JCRNodeWrapper node) throws RepositoryException {
        if (node.isNodeType("fmdb:form")) {
            return node;
        }

        // jnt:ace → j:acl → fmdb:form
        if (node.isNodeType("jnt:ace")) {
            JCRNodeWrapper parent = node.getParent();
            if (parent != null) {
                JCRNodeWrapper grandParent = parent.getParent();
                if (grandParent != null && grandParent.isNodeType("fmdb:form")) {
                    return grandParent;
                }
            }
        }

        return null;
    }

    private void syncFormByPath(String formPath, Set<String> processedFormIdentifiers) {
        try {
            JCRTemplate.getInstance().doExecuteWithSystemSessionAsUser(null, "live", null, session -> {
                if (!session.nodeExists(formPath)) {
                    return null;
                }

                JCRNodeWrapper formNode = session.getNode(formPath);
                if (!formNode.isNodeType("fmdb:form")) {
                    return null;
                }

                String formId = formNode.getIdentifier();
                if (processedFormIdentifiers.add(formId)) {
                    FormResultsAclSyncService.syncAcl(formNode, session);
                    session.save();
                    log.debug("[AclSyncListener] Synced ACL for form '{}' (after ACE removal)", formPath);
                }

                return null;
            });
        } catch (RepositoryException e) {
            log.warn("[AclSyncListener] Failed to sync after ACE removal at '{}': {}", formPath, e.getMessage());
        }
    }

    /**
     * Derives the form node path from an ACE path.
     * Expected structure: .../fmdb:form/j:acl/aceNodeName
     */
    private static String resolveFormPathFromAcePath(String acePath) {
        int aclIdx = acePath.lastIndexOf("/j:acl/");
        if (aclIdx < 0) {
            return null;
        }

        return acePath.substring(0, aclIdx);
    }
}

