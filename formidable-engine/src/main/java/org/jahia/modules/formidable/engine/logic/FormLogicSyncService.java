package org.jahia.modules.formidable.engine.logic;

import org.jahia.services.content.JCRItemWrapper;
import org.jahia.services.content.JCRNodeWrapper;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Value;
import java.util.*;

/**
 * Idempotent service that keeps the logicsSrc child structure in sync
 * with the logics JSON payload on a fmdbmix:formLogicElement node.
 */
public final class FormLogicSyncService {

    private static final Logger log = LoggerFactory.getLogger(FormLogicSyncService.class);
    private static final String LOGICS_SRC = "logicsSrc";
    private static final String LOGIC_NODE_SOURCE = "logicNodeSource";

    private FormLogicSyncService() {}

    /**
     * Cleans up logic dependencies after a subtree duplication.
     * Purges weakrefs that point outside the form boundary, then re-syncs
     * the remaining rules so weakrefs are rebuilt from sourceFieldName.
     *
     * @return true if any repository change was made
     */
    public static boolean cleanupAfterDuplication(JCRNodeWrapper formNode) throws RepositoryException {
        boolean updated = false;
        String formPath = formNode.getPath();

        List<JCRNodeWrapper> elements = new ArrayList<>();
        collectLogicElements(formNode.getNode("fields"), elements);

        for (JCRNodeWrapper element : elements) {
            Set<String> outOfScope = findOutOfScopeLogicIds(element, formPath);
            if (!outOfScope.isEmpty()) {
                removeLogicsSrcEntries(element, outOfScope);
                updated = true;
            }

            updated |= sync(element);
        }

        return updated;
    }

    /**
     * Synchronises the logicsSrc child nodes with the logics property.
     * Must be called with a JCR session that will be saved by the caller.
     *
     * @return true if any repository change was made
     */
    public static boolean sync(JCRNodeWrapper targetNode) throws RepositoryException {
        if (!targetNode.isNodeType("fmdbmix:formLogicElement")) {
            return false;
        }

        // Walk up the tree to find the owning fmdb:form — needed to resolve field names to UUIDs
        JCRNodeWrapper formNode = findFormAncestor(targetNode);
        if (formNode == null) {
            log.debug("[FormLogicSync] No fmdb:form ancestor found for '{}'", targetNode.getPath());
            return false;
        }

        // Build a name→node lookup for every field/step in the form subtree
        Map<String, JCRNodeWrapper> fieldsByName = buildFieldsByNameMap(formNode);

        // If logics was removed or is empty, clean up all logicsSrc children
        if (!targetNode.hasProperty("logics")) {
            return removeAllLogicsSrc(targetNode);
        }

        Value[] values = targetNode.getProperty("logics").getValues();
        if (values.length == 0) {
            return removeAllLogicsSrc(targetNode);
        }

        List<String> updatedJsonValues = new ArrayList<>();
        Set<String> activeLogicIds = new HashSet<>();
        boolean updated = false;

        for (Value v : values) {
            try {
                String json = v.getString();
                if (json == null || json.isBlank()) continue;

                JSONObject obj = new JSONObject(json);
                String sourceFieldName = obj.optString("sourceFieldName", "");
                if (sourceFieldName.isEmpty()) {
                    // No source field reference — nothing to sync, skip this entry entirely
                    log.debug("[FormLogicSync] Skipping rule without sourceFieldName on '{}'",
                            targetNode.getPath());
                    continue;
                }

                // Assign a logicId if missing (new rule or migration from the old sourceFieldId model)
                String logicId = obj.optString("logicId", "");
                if (logicId.isEmpty()) {
                    logicId = generateLogicId();
                    obj.put("logicId", logicId);
                    obj.remove("sourceFieldId");
                    updated = true;
                }

                activeLogicIds.add(logicId);

                // Resolve the source field name to its node and create/update the weakref
                JCRNodeWrapper sourceFieldNode = fieldsByName.get(sourceFieldName);
                if (sourceFieldNode != null) {
                    updated |= ensureLogicSrcNode(targetNode, logicId, sourceFieldNode);
                }

                updatedJsonValues.add(obj.toString());
            } catch (Exception e) {
                log.debug("[FormLogicSync] Skipping invalid logics entry on '{}': {}",
                        targetNode.getPath(), e.getMessage());
            }
        }

        // Write back the logics property only if a logicId was generated or migrated
        if (updated) {
            targetNode.setProperty("logics", updatedJsonValues.toArray(new String[0]));
        }

        // Remove logicsSrc children that no longer match any JSON rule
        Set<String> orphans = findOrphanLogicIds(targetNode, activeLogicIds);
        if (!orphans.isEmpty()) {
            removeLogicsSrcNodes(targetNode, orphans);
            updated = true;
        }

        return updated;
    }

    // --- Shared operations ---

    /**
     * Removes logicsSrc child nodes AND the corresponding JSON rules from the logics property.
     */
    private static void removeLogicsSrcEntries(JCRNodeWrapper element, Set<String> logicIdsToRemove)
            throws RepositoryException {
        removeLogicsSrcNodes(element, logicIdsToRemove);

        if (element.hasProperty("logics")) {
            Value[] values = element.getProperty("logics").getValues();
            List<String> remaining = new ArrayList<>();
            for (Value v : values) {
                try {
                    String json = v.getString();
                    JSONObject obj = new JSONObject(json);
                    String logicId = obj.optString("logicId", "");
                    if (!logicId.isEmpty() && !logicIdsToRemove.contains(logicId)) {
                        remaining.add(json);
                    }
                } catch (Exception e) {
                    // drop malformed entries
                }
            }

            element.setProperty("logics", remaining.toArray(new String[0]));
        }
    }

    /**
     * Removes logicsSrc child nodes by name.
     */
    private static void removeLogicsSrcNodes(JCRNodeWrapper element, Set<String> logicIds)
            throws RepositoryException {
        JCRNodeWrapper logicsSrc = element.getNode(LOGICS_SRC);
        for (String logicId : logicIds) {
            if (logicsSrc.hasNode(logicId)) {
                logicsSrc.getNode(logicId).remove();
            }
        }
    }

    // --- Detection strategies ---

    /**
     * Finds logicsSrc children whose name is not in the active set (deleted from JSON).
     */
    private static Set<String> findOrphanLogicIds(JCRNodeWrapper element, Set<String> activeLogicIds)
            throws RepositoryException {
        Set<String> orphans = new HashSet<>();
        NodeIterator children = element.getNode(LOGICS_SRC).getNodes();
        while (children.hasNext()) {
            JCRNodeWrapper child = (JCRNodeWrapper) children.nextNode();
            if (!activeLogicIds.contains(child.getName())) {
                orphans.add(child.getName());
            }
        }

        return orphans;
    }

    /**
     * Finds logicsSrc children whose weakref points outside the given form subtree.
     */
    private static Set<String> findOutOfScopeLogicIds(JCRNodeWrapper element, String formPath)
            throws RepositoryException {
        Set<String> outOfScope = new HashSet<>();
        NodeIterator children = element.getNode(LOGICS_SRC).getNodes();
        while (children.hasNext()) {
            JCRNodeWrapper child = (JCRNodeWrapper) children.nextNode();
            boolean valid = false;
            try {
                JCRNodeWrapper sourceNode = (JCRNodeWrapper) child.getProperty(LOGIC_NODE_SOURCE).getNode();
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

    // --- Node-level operations ---

    /**
     * Creates or updates a single logicsSrc/{logicId} child node with a weakreference
     * pointing to the resolved source field.
     */
    private static boolean ensureLogicSrcNode(JCRNodeWrapper targetNode, String logicId, JCRNodeWrapper sourceFieldNode)
            throws RepositoryException {
        JCRNodeWrapper logicsSrc = targetNode.getNode(LOGICS_SRC);
        boolean updated = false;

        if (logicsSrc.hasNode(logicId)) {
            JCRNodeWrapper existing = logicsSrc.getNode(logicId);
            String currentRef = existing.getProperty(LOGIC_NODE_SOURCE).getNode().getIdentifier();
            if (!sourceFieldNode.getIdentifier().equals(currentRef)) {
                existing.setProperty(LOGIC_NODE_SOURCE, sourceFieldNode);
                updated = true;
            }
        } else {
            JCRNodeWrapper newNode = logicsSrc.addNode(logicId, "fmdb:logicSrc");
            newNode.setProperty(LOGIC_NODE_SOURCE, sourceFieldNode);
            updated = true;
        }

        return updated;
    }

    /**
     * Removes every child under logicsSrc — used when the logics property
     * is cleared or removed entirely.
     */
    private static boolean removeAllLogicsSrc(JCRNodeWrapper targetNode) throws RepositoryException {
        JCRNodeWrapper logicsSrc = targetNode.getNode(LOGICS_SRC);
        NodeIterator children = logicsSrc.getNodes();
        boolean updated = false;
        while (children.hasNext()) {
            children.nextNode().remove();
            updated = true;
        }

        return updated;
    }

    // --- Tree utilities ---

    /**
     * Walks up the JCR tree from the given node until it finds an fmdb:form ancestor.
     */
    static JCRNodeWrapper findFormAncestor(JCRNodeWrapper node) throws RepositoryException {
        for (JCRItemWrapper ancestor : node.getAncestors()) {
            if (ancestor instanceof JCRNodeWrapper ancestorNode && ancestorNode.isNodeType("fmdb:form")) {
                return ancestorNode;
            }
        }

        return null;
    }

    /**
     * Recursively collects all fmdbmix:formLogicElement descendants.
     */
    private static void collectLogicElements(JCRNodeWrapper node, List<JCRNodeWrapper> result)
            throws RepositoryException {
        NodeIterator it = node.getNodes();
        while (it.hasNext()) {
            JCRNodeWrapper child = (JCRNodeWrapper) it.nextNode();
            if (child.isNodeType("fmdbmix:formLogicElement")) {
                result.add(child);
            }

            collectLogicElements(child, result);
        }
    }

    /**
     * Builds a map of field name → JCR node for every fmdbmix:formElement and
     * fmdbmix:formStep descendant of the form node.
     */
    private static Map<String, JCRNodeWrapper> buildFieldsByNameMap(JCRNodeWrapper formNode) throws RepositoryException {
        Map<String, JCRNodeWrapper> map = new HashMap<>();
        collectFields(formNode.getNode("fields"), map);
        return map;
    }

    private static void collectFields(JCRNodeWrapper node, Map<String, JCRNodeWrapper> map) throws RepositoryException {
        NodeIterator it = node.getNodes();
        while (it.hasNext()) {
            JCRNodeWrapper child = (JCRNodeWrapper) it.nextNode();
            if (child.isNodeType("fmdbmix:formElement") || child.isNodeType("fmdbmix:formStep")) {
                map.put(child.getName(), child);
            }

            collectFields(child, map);
        }
    }

    private static String generateLogicId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
