package org.jahia.modules.formidable.engine.logic;

import org.jahia.services.content.JCRNodeWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import java.util.HashSet;
import java.util.Set;

import static org.jahia.modules.formidable.engine.util.FormidableJcrConstants.LOGIC_NODE_SOURCE_PROPERTY;
import static org.jahia.modules.formidable.engine.util.FormidableJcrConstants.LOGIC_LIST_NODE_TYPE;
import static org.jahia.modules.formidable.engine.util.FormidableJcrConstants.LOGIC_SRC_NODE_TYPE;
import static org.jahia.modules.formidable.engine.util.FormidableJcrConstants.LOGICS_SRC_NODE;

final class FormLogicReferenceStore {

    private static final Logger log = LoggerFactory.getLogger(FormLogicReferenceStore.class);

    private FormLogicReferenceStore() {}

    static boolean ensureLogicSrcNode(JCRNodeWrapper targetNode, String logicId, JCRNodeWrapper sourceFieldNode)
            throws RepositoryException {
        JCRNodeWrapper logicsSrc = targetNode.hasNode(LOGICS_SRC_NODE)
                ? targetNode.getNode(LOGICS_SRC_NODE)
                : targetNode.addNode(LOGICS_SRC_NODE, LOGIC_LIST_NODE_TYPE);

        if (logicsSrc.hasNode(logicId)) {
            JCRNodeWrapper existing = logicsSrc.getNode(logicId);
            try {
                JCRNodeWrapper current = (JCRNodeWrapper) existing.getProperty(LOGIC_NODE_SOURCE_PROPERTY).getNode();
                if (current.getIdentifier().equals(sourceFieldNode.getIdentifier())) {
                    return false;
                }
            } catch (Exception e) {
                log.debug("[FormLogicSync] Broken weakref for logicId '{}', re-resolving", logicId);
            }

            existing.setProperty(LOGIC_NODE_SOURCE_PROPERTY, sourceFieldNode);
            return true;
        }

        JCRNodeWrapper newNode = logicsSrc.addNode(logicId, LOGIC_SRC_NODE_TYPE);
        newNode.setProperty(LOGIC_NODE_SOURCE_PROPERTY, sourceFieldNode);
        return true;
    }

    static JCRNodeWrapper getBoundSourceNode(JCRNodeWrapper targetNode, String logicId) throws RepositoryException {
        if (!targetNode.hasNode(LOGICS_SRC_NODE)) {
            return null;
        }

        JCRNodeWrapper logicsSrc = targetNode.getNode(LOGICS_SRC_NODE);
        if (!logicsSrc.hasNode(logicId)) {
            return null;
        }

        try {
            return (JCRNodeWrapper) logicsSrc.getNode(logicId).getProperty(LOGIC_NODE_SOURCE_PROPERTY).getNode();
        } catch (Exception e) {
            log.debug("[FormLogicSync] Broken weakref for logicId '{}'", logicId);
            return null;
        }
    }

    static boolean removeAllLogicsSrc(JCRNodeWrapper targetNode) throws RepositoryException {
        if (!targetNode.hasNode(LOGICS_SRC_NODE)) {
            return false;
        }

        NodeIterator children = targetNode.getNode(LOGICS_SRC_NODE).getNodes();
        boolean updated = false;
        while (children.hasNext()) {
            children.nextNode().remove();
            updated = true;
        }

        return updated;
    }

    static void removeLogicsSrcNodes(JCRNodeWrapper element, Set<String> logicIds) throws RepositoryException {
        if (!element.hasNode(LOGICS_SRC_NODE)) {
            return;
        }

        JCRNodeWrapper logicsSrc = element.getNode(LOGICS_SRC_NODE);
        for (String logicId : logicIds) {
            if (logicsSrc.hasNode(logicId)) {
                logicsSrc.getNode(logicId).remove();
            }
        }
    }

    static Set<String> findOrphanLogicIds(JCRNodeWrapper element, Set<String> activeLogicIds)
            throws RepositoryException {
        if (!element.hasNode(LOGICS_SRC_NODE)) {
            return Set.of();
        }

        Set<String> orphans = new HashSet<>();
        NodeIterator children = element.getNode(LOGICS_SRC_NODE).getNodes();
        while (children.hasNext()) {
            String name = ((JCRNodeWrapper) children.nextNode()).getName();
            if (!activeLogicIds.contains(name)) {
                orphans.add(name);
            }
        }

        return orphans;
    }

    static Set<String> findOutOfScopeLogicIds(JCRNodeWrapper element, String formPath) throws RepositoryException {
        if (!element.hasNode(LOGICS_SRC_NODE)) {
            return Set.of();
        }

        Set<String> outOfScope = new HashSet<>();
        NodeIterator children = element.getNode(LOGICS_SRC_NODE).getNodes();
        while (children.hasNext()) {
            JCRNodeWrapper child = (JCRNodeWrapper) children.nextNode();
            boolean valid = false;
            try {
                JCRNodeWrapper sourceNode = (JCRNodeWrapper) child.getProperty(LOGIC_NODE_SOURCE_PROPERTY).getNode();
                valid = sourceNode.getPath().startsWith(formPath + "/");
            } catch (Exception e) {
                log.debug("[FormLogicSync] Broken weakref '{}' on '{}'", child.getName(), element.getPath());
            }

            if (!valid) {
                outOfScope.add(child.getName());
            }
        }

        return outOfScope;
    }
}
