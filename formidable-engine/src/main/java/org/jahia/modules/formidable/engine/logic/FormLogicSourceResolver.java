package org.jahia.modules.formidable.engine.logic;

import org.jahia.services.content.JCRNodeWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;

final class FormLogicSourceResolver {

    private static final Logger log = LoggerFactory.getLogger(FormLogicSourceResolver.class);

    private final JCRNodeWrapper targetNode;
    private final String formScopePrefix;
    private final FormSourceFieldIndex sourceFieldIndex;

    private FormLogicSourceResolver(
            JCRNodeWrapper targetNode,
            String formScopePrefix,
            FormSourceFieldIndex sourceFieldIndex
    ) {
        this.targetNode = targetNode;
        this.formScopePrefix = formScopePrefix;
        this.sourceFieldIndex = sourceFieldIndex;
    }

    static FormLogicSourceResolver forTarget(JCRNodeWrapper formNode, JCRNodeWrapper targetNode)
            throws RepositoryException {
        FormSourceFieldIndex sourceFieldIndex = FormSourceFieldIndex.build(formNode, targetNode.getIdentifier());
        return new FormLogicSourceResolver(targetNode, formNode.getPath() + "/", sourceFieldIndex);
    }

    boolean resolveAndBind(FormLogicJsonEntry entry) throws RepositoryException {
        JCRNodeWrapper sourceNode = resolveSourceNode(entry);
        if (sourceNode == null) {
            return false;
        }

        // The resolved source must carry a fieldKey so the rule can persist a stable
        // business reference; assign one on the fly for legacy fields.
        String sourceFieldKey = FieldKeys.get(sourceNode);
        boolean updated = false;
        if (sourceFieldKey == null) {
            sourceFieldKey = FieldKeys.assign(sourceNode);
            updated = true;
        }

        entry.updateSourceNodeId(sourceNode.getIdentifier());
        entry.updateSourceFieldKey(sourceFieldKey);
        return FormLogicReferenceStore.ensureLogicSrcNode(targetNode, entry.logicId(), sourceNode) || updated;
    }

    /**
     * Resolution order: sourceFieldKey is the business identity and takes precedence.
     * When several fields share the same key (transient state during a same-form
     * duplication, before remapping), the technical pointers (sourceNodeId, weakref)
     * disambiguate as long as they designate a node carrying that key. The legacy
     * chain (sourceNodeId, weakref, sourceFieldName) remains for rules stored before
     * fieldKey existed.
     */
    private JCRNodeWrapper resolveSourceNode(FormLogicJsonEntry entry) throws RepositoryException {
        String sourceFieldKey = entry.sourceFieldKey();
        if (!sourceFieldKey.isEmpty()) {
            JCRNodeWrapper candidate = resolveFromSourceNodeId(entry.sourceNodeId());
            if (candidate != null && sourceFieldKey.equals(FieldKeys.get(candidate))) {
                return candidate;
            }

            candidate = resolveFromExistingWeakref(entry.logicId());
            if (candidate != null && sourceFieldKey.equals(FieldKeys.get(candidate))) {
                return candidate;
            }

            candidate = sourceFieldIndex.findFirstByFieldKey(sourceFieldKey);
            if (candidate != null) {
                return candidate;
            }
        }

        JCRNodeWrapper candidate = resolveFromSourceNodeId(entry.sourceNodeId());
        if (candidate != null) {
            return candidate;
        }

        candidate = resolveFromExistingWeakref(entry.logicId());
        if (candidate != null) {
            return candidate;
        }

        return sourceFieldIndex.findFirstByName(entry.sourceFieldName());
    }

    private JCRNodeWrapper resolveFromSourceNodeId(String sourceNodeId) throws RepositoryException {
        if (sourceNodeId.isEmpty()) {
            return null;
        }

        try {
            JCRNodeWrapper candidate = targetNode.getSession().getNodeByIdentifier(sourceNodeId);
            return isValidSourceCandidate(candidate) ? candidate : null;
        } catch (Exception e) {
            log.debug("[FormLogicSync] sourceNodeId '{}' not resolvable: {}", sourceNodeId, e.getMessage());
            return null;
        }
    }

    private JCRNodeWrapper resolveFromExistingWeakref(String logicId) throws RepositoryException {
        JCRNodeWrapper candidate = FormLogicReferenceStore.getBoundSourceNode(targetNode, logicId);
        return isValidSourceCandidate(candidate) ? candidate : null;
    }

    private boolean isValidSourceCandidate(JCRNodeWrapper candidate) throws RepositoryException {
        return candidate != null
                && candidate.getPath().startsWith(formScopePrefix)
                && sourceFieldIndex.containsSourceId(candidate.getIdentifier());
    }
}
