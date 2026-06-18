package org.jahia.modules.formidable.engine.logic;

import org.jahia.services.content.JCRNodeWrapper;

import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.jahia.modules.formidable.engine.util.FormidableJcrConstants.FIELDS_NODE;
import static org.jahia.modules.formidable.engine.util.FormidableJcrConstants.FORM_ELEMENT_MIXIN;
import static org.jahia.modules.formidable.engine.util.FormidableJcrConstants.FORM_STEP_MIXIN;

final class FormSourceFieldIndex {

    private final Map<String, JCRNodeWrapper> firstFieldByName;
    private final Set<String> validSourceIds;

    private FormSourceFieldIndex(Map<String, JCRNodeWrapper> firstFieldByName, Set<String> validSourceIds) {
        this.firstFieldByName = firstFieldByName;
        this.validSourceIds = validSourceIds;
    }

    static FormSourceFieldIndex build(JCRNodeWrapper formNode, String targetId) throws RepositoryException {
        Map<String, JCRNodeWrapper> firstFieldByName = new LinkedHashMap<>();
        Set<String> validSourceIds = new HashSet<>();
        collectFieldsBeforeTarget(formNode.getNode(FIELDS_NODE), targetId, firstFieldByName, validSourceIds);
        return new FormSourceFieldIndex(firstFieldByName, validSourceIds);
    }

    JCRNodeWrapper findFirstByName(String fieldName) {
        return firstFieldByName.get(fieldName);
    }

    boolean containsSourceId(String sourceId) {
        return validSourceIds.contains(sourceId);
    }

    /**
     * Depth-first traversal that collects fields appearing before the target node.
     * Returns {@code false} once the target has been found.
     */
    private static boolean collectFieldsBeforeTarget(
            JCRNodeWrapper node,
            String targetId,
            Map<String, JCRNodeWrapper> firstFieldByName,
            Set<String> validSourceIds
    ) throws RepositoryException {
        NodeIterator it = node.getNodes();
        while (it.hasNext()) {
            JCRNodeWrapper child = (JCRNodeWrapper) it.nextNode();
            if (child.getIdentifier().equals(targetId)) {
                return false;
            }

            if (child.isNodeType(FORM_ELEMENT_MIXIN) || child.isNodeType(FORM_STEP_MIXIN)) {
                firstFieldByName.putIfAbsent(child.getName(), child);
                validSourceIds.add(child.getIdentifier());
            }

            if (!collectFieldsBeforeTarget(child, targetId, firstFieldByName, validSourceIds)) {
                return false;
            }
        }

        return true;
    }
}
