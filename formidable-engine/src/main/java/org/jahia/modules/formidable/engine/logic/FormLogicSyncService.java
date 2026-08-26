package org.jahia.modules.formidable.engine.logic;

import org.jahia.services.content.JCRItemWrapper;
import org.jahia.services.content.JCRNodeWrapper;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Value;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
            updated |= FieldKeys.assignIfMissing(element);

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
     * Repairs fieldKey collisions created by duplicating a subtree inside the same form
     * (copy/paste of a fieldset, step or field). Every copied element whose fieldKey is
     * already used outside the copied subtree receives a fresh key, and the rules stored
     * inside the copied subtree are remapped to the fresh keys, so the copy references
     * its own internal sources instead of the original ones.
     * Cross-form copies and full-form duplications never collide (keys are random UUIDs)
     * and pass through unchanged.
     */
    public static boolean remapFieldKeysAfterCopy(JCRNodeWrapper copiedRoot, JCRNodeWrapper formNode)
            throws RepositoryException {
        List<JCRNodeWrapper> copiedElements = new ArrayList<>();
        if (copiedRoot.isNodeType(FORM_LOGIC_ELEMENT_MIXIN)) {
            copiedElements.add(copiedRoot);
        }

        collectLogicElements(copiedRoot, copiedElements);
        if (copiedElements.isEmpty()) {
            return false;
        }

        Set<String> copiedIds = new HashSet<>();
        for (JCRNodeWrapper element : copiedElements) {
            copiedIds.add(element.getIdentifier());
        }

        Set<String> keysOutsideCopy = new HashSet<>();
        for (JCRNodeWrapper element : collectLogicElements(formNode.getNode(FIELDS_NODE))) {
            if (!copiedIds.contains(element.getIdentifier())) {
                String key = FieldKeys.get(element);
                if (key != null) {
                    keysOutsideCopy.add(key);
                }
            }
        }

        Map<String, String> remappedKeys = new HashMap<>();
        boolean updated = false;
        for (JCRNodeWrapper element : copiedElements) {
            String key = FieldKeys.get(element);
            if (key != null && keysOutsideCopy.contains(key)) {
                remappedKeys.put(key, FieldKeys.assign(element));
                updated = true;
            }
        }

        if (!remappedKeys.isEmpty()) {
            for (JCRNodeWrapper element : copiedElements) {
                updated |= rewriteSourceFieldKeys(element, remappedKeys);
            }

            log.info("[FormLogicSync] Remapped {} duplicated fieldKey(s) under '{}'",
                    remappedKeys.size(), copiedRoot.getPath());
        }

        return updated;
    }

    private static boolean rewriteSourceFieldKeys(JCRNodeWrapper element, Map<String, String> remappedKeys)
            throws RepositoryException {
        if (!element.hasProperty(LOGICS_PROPERTY)) {
            return false;
        }

        Value[] values = element.getProperty(LOGICS_PROPERTY).getValues();
        List<String> rewritten = new ArrayList<>();
        boolean updated = false;

        for (Value value : values) {
            String rawJson = value.getString();
            try {
                JSONObject json = new JSONObject(rawJson);
                String newKey = remappedKeys.get(json.optString("sourceFieldKey", ""));
                if (newKey != null) {
                    json.put("sourceFieldKey", newKey);
                    rawJson = json.toString();
                    updated = true;
                }
            } catch (Exception e) {
                log.debug("[FormLogicSync] Skipping unparseable logics entry on '{}': {}",
                        element.getPath(), e.getMessage());
            }

            rewritten.add(rawJson);
        }

        if (updated) {
            element.setProperty(LOGICS_PROPERTY, rewritten.toArray(new String[0]));
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

        boolean keyAssigned = FieldKeys.assignIfMissing(targetNode);

        if (!targetNode.hasProperty(LOGICS_PROPERTY)) {
            return FormLogicReferenceStore.removeAllLogicsSrc(targetNode) || keyAssigned;
        }

        Value[] values = targetNode.getProperty(LOGICS_PROPERTY).getValues();
        if (values.length == 0) {
            return FormLogicReferenceStore.removeAllLogicsSrc(targetNode) || keyAssigned;
        }

        FormLogicSourceResolver resolver = FormLogicSourceResolver.forTarget(formNode, targetNode);
        List<String> updatedJsonValues = new ArrayList<>();
        Set<String> activeLogicIds = new HashSet<>();
        boolean jsonUpdated = false;
        int droppedLeftovers = 0;
        boolean updated = keyAssigned;

        for (Value value : values) {
            String rawJson = value.getString();
            if (rawJson == null || rawJson.isBlank()) {
                droppedLeftovers++;
                continue;
            }

            // Rules whose target was never chosen can only hide the field: they are
            // removed at save. Corrupt entries are kept untouched rather than lost.
            JSONObject parsedRule = null;
            try {
                parsedRule = new JSONObject(rawJson);
            } catch (RuntimeException e) {
                log.debug("[FormLogicSync] Keeping unparseable logics entry on '{}' untouched: {}",
                        targetNode.getPath(), e.getMessage());
            }

            if (parsedRule != null && FormLogicRuleCleanup.isTargetlessLeftover(parsedRule)) {
                droppedLeftovers++;
                continue;
            }

            FormLogicJsonEntry entry = FormLogicJsonEntry.parse(value, targetNode.getPath());
            if (entry == null) {
                updatedJsonValues.add(rawJson);
                continue;
            }

            // Provider rules reference browser state (a JS variable, a URL parameter, a
            // cookie…), not a form field: nothing to resolve or bind, and their logicId must
            // not keep a logicsSrc weakref alive (the rule may have been a field rule before
            // its source type changed).
            if (entry.isFieldRule()) {
                activeLogicIds.add(entry.logicId());
                jsonUpdated |= resolver.resolveAndBind(entry);
            }

            jsonUpdated |= entry.isUpdated();
            updatedJsonValues.add(entry.toJsonString());
        }

        if (jsonUpdated || droppedLeftovers > 0) {
            targetNode.setProperty(LOGICS_PROPERTY, updatedJsonValues.toArray(new String[0]));
            updated = true;
        }

        if (droppedLeftovers > 0) {
            log.info("[FormLogicSync] Removed {} targetless logic rule(s) on '{}'",
                    droppedLeftovers, targetNode.getPath());
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
