package org.jahia.modules.formidable.engine.logic;

import org.jahia.services.content.JCRItemWrapper;
import org.jahia.services.content.JCRNodeWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Value;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.jahia.modules.formidable.engine.util.FormidableJcrConstants.FIELDS_NODE;
import static org.jahia.modules.formidable.engine.util.FormidableJcrConstants.FORM_LOGIC_ELEMENT_MIXIN;
import static org.jahia.modules.formidable.engine.util.FormidableJcrConstants.FORM_NODE_TYPE;
import static org.jahia.modules.formidable.engine.util.FormidableJcrConstants.LOGICS_PROPERTY;

/**
 * Idempotent service that keeps the logicsSrc child structure in sync
 * with the logics JSON payload on a fmdbmix:formLogicElement node.
 */
public final class FormLogicSyncService {

    private static final Logger log = LoggerFactory.getLogger(FormLogicSyncService.class);

    private FormLogicSyncService() {}

    /**
     * Cleans up logic dependencies after a subtree duplication.
     * Purges weakrefs that point outside the form boundary, then re-syncs.
     */
    public static boolean cleanupAfterDuplication(JCRNodeWrapper formNode) throws RepositoryException {
        boolean updated = false;
        String formPath = formNode.getPath();

        for (JCRNodeWrapper element : collectLogicElements(formNode.getNode(FIELDS_NODE))) {
            Set<String> outOfScope = FormLogicReferenceStore.findOutOfScopeLogicIds(element, formPath);
            if (!outOfScope.isEmpty()) {
                FormLogicReferenceStore.removeLogicsSrcNodes(element, outOfScope);
                updated = true;
            }

            updated |= sync(element);
        }

        return updated;
    }

    /**
     * Synchronises the logicsSrc child nodes with the logics property.
     * Must be called with a JCR session that will be saved by the caller.
     */
    public static boolean sync(JCRNodeWrapper targetNode) throws RepositoryException {
        if (!targetNode.isNodeType(FORM_LOGIC_ELEMENT_MIXIN)) {
            return false;
        }

        JCRNodeWrapper formNode = findFormAncestor(targetNode);
        if (formNode == null) {
            log.debug("[FormLogicSync] No fmdb:form ancestor found for '{}'", targetNode.getPath());
            return false;
        }

        if (!targetNode.hasProperty(LOGICS_PROPERTY)) {
            return FormLogicReferenceStore.removeAllLogicsSrc(targetNode);
        }

        Value[] values = targetNode.getProperty(LOGICS_PROPERTY).getValues();
        if (values.length == 0) {
            return FormLogicReferenceStore.removeAllLogicsSrc(targetNode);
        }

        FormLogicSourceResolver resolver = FormLogicSourceResolver.forTarget(formNode, targetNode);
        List<String> updatedJsonValues = new ArrayList<>();
        Set<String> activeLogicIds = new HashSet<>();
        boolean updated = false;

        for (Value value : values) {
            FormLogicJsonEntry entry = FormLogicJsonEntry.parse(value, targetNode.getPath());
            if (entry == null) {
                continue;
            }

            activeLogicIds.add(entry.logicId());
            updated |= resolver.resolveAndBind(entry);
            updated |= entry.isUpdated();
            updatedJsonValues.add(entry.toJsonString());
        }

        if (updated) {
            targetNode.setProperty(LOGICS_PROPERTY, updatedJsonValues.toArray(new String[0]));
        }

        Set<String> orphans = FormLogicReferenceStore.findOrphanLogicIds(targetNode, activeLogicIds);
        if (!orphans.isEmpty()) {
            FormLogicReferenceStore.removeLogicsSrcNodes(targetNode, orphans);
            updated = true;
        }

        return updated;
    }

    static JCRNodeWrapper findFormAncestor(JCRNodeWrapper node) throws RepositoryException {
        for (JCRItemWrapper ancestor : node.getAncestors()) {
            if (ancestor instanceof JCRNodeWrapper n && n.isNodeType(FORM_NODE_TYPE)) {
                return n;
            }
        }

        return null;
    }

    private static List<JCRNodeWrapper> collectLogicElements(JCRNodeWrapper node) throws RepositoryException {
        List<JCRNodeWrapper> result = new ArrayList<>();
        collectLogicElements(node, result);
        return result;
    }

    private static void collectLogicElements(JCRNodeWrapper node, List<JCRNodeWrapper> result)
            throws RepositoryException {
        NodeIterator it = node.getNodes();
        while (it.hasNext()) {
            JCRNodeWrapper child = (JCRNodeWrapper) it.nextNode();
            if (child.isNodeType(FORM_LOGIC_ELEMENT_MIXIN)) {
                result.add(child);
            }

            collectLogicElements(child, result);
        }
    }
}
