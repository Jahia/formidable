package org.jahia.test.modules.formidable.samples.integrity;

import org.jahia.modules.contentintegrity.api.ContentIntegrityError;
import org.jahia.modules.contentintegrity.api.ContentIntegrityErrorList;
import org.jahia.modules.contentintegrity.api.ContentIntegrityErrorType;
import org.jahia.modules.contentintegrity.services.impl.AbstractContentIntegrityCheck;
import org.jahia.services.content.JCRNodeWrapper;
import org.json.JSONException;
import org.json.JSONObject;

import javax.jcr.NodeIterator;
import javax.jcr.Property;
import javax.jcr.PropertyIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Value;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Base class for Formidable content-integrity checks.
 * Provides shared JCR node type / property constants, reusable error types,
 * and utility methods for navigating the form tree and parsing logic rules.
 */
abstract class AbstractFormidableIntegrityCheck extends AbstractContentIntegrityCheck {

    // -- JCR node type constants --

    protected static final String FMDB_FORM = "fmdb:form";
    protected static final String FMDB_FORM_REFERENCE = "fmdb:formReference";
    protected static final String FMDB_FORM_RESULTS = "fmdb:formResults";
    protected static final String FMDB_FORM_SUBMISSION = "fmdb:formSubmission";
    protected static final String FMDB_LOGIC_SRC = "fmdb:logicSrc";
    protected static final String FMDB_LOGIC_ELEMENT = "fmdbmix:formLogicElement";
    protected static final String FMDB_SUBMISSION_DATA = "fmdb:submissionData";
    protected static final String FMDB_FILE_FIELD = "fmdbmix:fileField";
    protected static final String FMDB_FORM_ELEMENT = "fmdbmix:formElement";
    protected static final String FMDB_NON_SUBMITTABLE = "fmdbmix:nonSubmittable";

    // -- JCR property / child-node name constants --

    protected static final String DATA_NODE = "data";
    protected static final String FILES_NODE = "files";
    protected static final String FIELDS_NODE = "fields";
    protected static final String J_NODE = "j:node";
    protected static final String LOGICS_PROPERTY = "logics";
    protected static final String LOGICS_SRC_NODE = "logicsSrc";
    protected static final String LOGIC_ID = "logicId";
    protected static final String LOGIC_NODE_SOURCE = "logicNodeSource";
    protected static final String PARENT_FORM = "parentForm";
    protected static final String SOURCE_NODE_ID = "sourceNodeId";
    protected static final String SUBMISSIONS_NODE = "submissions";

    // -- Shared error types used across all Formidable integrity checks --

    protected static final ContentIntegrityErrorType INVALID_TARGET_NODE_TYPE =
            createErrorType("INVALID_TARGET_NODE_TYPE", "Reference does not target the expected node type");
    protected static final ContentIntegrityErrorType MISSING_REQUIRED_CHILD =
            createErrorType("MISSING_REQUIRED_CHILD", "Missing required child node");
    protected static final ContentIntegrityErrorType INVALID_CHILD_NODE_TYPE =
            createErrorType("INVALID_CHILD_NODE_TYPE", "Child node has an unexpected node type");
    protected static final ContentIntegrityErrorType INVALID_LOGIC_RULE =
            createErrorType("INVALID_LOGIC_RULE", "Conditional logic rule JSON is invalid");
    protected static final ContentIntegrityErrorType MISSING_LOGICSRC_ENTRY =
            createErrorType("MISSING_LOGICSRC_ENTRY", "Missing logicsSrc entry for an in-scope logic rule");
    protected static final ContentIntegrityErrorType ORPHAN_LOGICSRC_ENTRY =
            createErrorType("ORPHAN_LOGICSRC_ENTRY", "logicsSrc entry has no matching JSON rule");
    protected static final ContentIntegrityErrorType OUT_OF_SCOPE_LOGIC_SOURCE =
            createErrorType("OUT_OF_SCOPE_LOGIC_SOURCE", "logicNodeSource points outside the owning form subtree");
    protected static final ContentIntegrityErrorType MISMATCHED_LOGIC_SOURCE =
            createErrorType("MISMATCHED_LOGIC_SOURCE", "logicNodeSource does not match the rule sourceNodeId");
    protected static final ContentIntegrityErrorType UNDECLARED_SUBMISSION_FIELD =
            createErrorType("UNDECLARED_SUBMISSION_FIELD", "Submission data contains an undeclared field name");
    protected static final ContentIntegrityErrorType UNDECLARED_FILE_STORAGE_FIELD =
            createErrorType("UNDECLARED_FILE_STORAGE_FIELD", "Submission files storage contains an undeclared file field");

    /**
     * Asserts that a REFERENCE property points to a node of the expected type.
     *
     * @return {@code null} if the referenced node matches, or an error list otherwise.
     */
    protected ContentIntegrityErrorList requireReferencedNodeType(
            JCRNodeWrapper node,
            String propertyName,
            String expectedNodeType
    ) {
        try {
            // Resolve the REFERENCE property to its target node
            JCRNodeWrapper referencedNode = (JCRNodeWrapper) node.getProperty(propertyName).getNode();

            // Target type matches — nothing to report
            if (referencedNode.isNodeType(expectedNodeType)) {
                return null;
            }

            // Type mismatch: report the expected vs actual node type
            ContentIntegrityError error = createPropertyRelatedError(node, INVALID_TARGET_NODE_TYPE)
                    .addExtraInfo("property-name", propertyName)
                    .addExtraInfo("expected-node-type", expectedNodeType)
                    .addExtraInfo("actual-node-type", referencedNode.getPrimaryNodeTypeName(), true);
            return createSingleError(error);
        } catch (RepositoryException e) {
            return createSingleError(createFrameworkError(node, "Failed to resolve property '" + propertyName + "'", e));
        }
    }

    /**
     * Asserts that a required child node exists and has the expected type.
     *
     * @return {@code null} if the child exists and matches, or an error list otherwise.
     */
    protected ContentIntegrityErrorList requireChildNodeType(
            JCRNodeWrapper node,
            String childName,
            String expectedNodeType
    ) {
        try {
            // Child is missing entirely
            if (!node.hasNode(childName)) {
                ContentIntegrityError error = createError(node, MISSING_REQUIRED_CHILD)
                        .addExtraInfo("child-name", childName)
                        .addExtraInfo("expected-node-type", expectedNodeType, true);
                return createSingleError(error);
            }

            // Child exists — check its node type
            JCRNodeWrapper child = node.getNode(childName);
            if (child.isNodeType(expectedNodeType)) {
                return null;
            }

            // Node type mismatch
            ContentIntegrityError error = createError(node, INVALID_CHILD_NODE_TYPE)
                    .addExtraInfo("child-name", childName)
                    .addExtraInfo("expected-node-type", expectedNodeType)
                    .addExtraInfo("actual-node-type", child.getPrimaryNodeTypeName(), true);
            return createSingleError(error);
        } catch (RepositoryException e) {
            return createSingleError(createFrameworkError(node, "Failed to validate child '" + childName + "'", e));
        }
    }

    /**
     * Merges multiple nullable error lists into a single list.
     * Skips {@code null} entries and returns {@code null} if all inputs are {@code null}.
     */
    protected ContentIntegrityErrorList mergeErrorLists(ContentIntegrityErrorList... errorLists) {
        ContentIntegrityErrorList mergedErrors = null;
        for (ContentIntegrityErrorList errorList : errorLists) {
            // Skip null entries (check passed without errors)
            if (errorList == null) {
                continue;
            }

            // First non-null list becomes the accumulator; subsequent ones are appended
            if (mergedErrors == null) {
                mergedErrors = errorList;
            } else {
                mergedErrors = mergedErrors.addAll(errorList);
            }
        }

        return mergedErrors;
    }

    /**
     * Walks up the JCR tree from the given node to find the nearest ancestor of type {@code fmdb:form}.
     *
     * @return the owning form node, or {@code null} if the node is not nested inside a form.
     */
    protected JCRNodeWrapper findOwningForm(JCRNodeWrapper node) throws RepositoryException {
        JCRNodeWrapper current = node;
        while (current != null) {
            if (current.isNodeType(FMDB_FORM)) {
                return current;
            }

            current = current.getParent();
        }

        return null;
    }

    protected JCRNodeWrapper findFormResultsAncestor(JCRNodeWrapper node) throws RepositoryException {
        JCRNodeWrapper current = node;
        while (current != null) {
            if (current.isNodeType(FMDB_FORM_RESULTS)) {
                return current;
            }

            current = current.getParent();
        }

        return null;
    }

    protected JCRNodeWrapper resolveOwningFormFromResults(JCRNodeWrapper resultsNode) throws RepositoryException {
        if (resultsNode == null || !resultsNode.hasProperty(PARENT_FORM)) {
            return null;
        }

        try {
            return (JCRNodeWrapper) resultsNode.getProperty(PARENT_FORM).getNode();
        } catch (RepositoryException e) {
            return null;
        }
    }

    /** Collects all direct child node names into a Set for fast lookup. */
    protected Set<String> getChildNames(JCRNodeWrapper node) throws RepositoryException {
        Set<String> names = new HashSet<>();
        NodeIterator children = node.getNodes();
        while (children.hasNext()) {
            names.add(children.nextNode().getName());
        }

        return names;
    }

    protected Set<String> collectDeclaredFieldNames(JCRNodeWrapper formNode) throws RepositoryException {
        Set<String> fieldNames = new HashSet<>();
        if (formNode == null || !formNode.hasNode(FIELDS_NODE)) {
            return fieldNames;
        }

        collectDeclaredFieldNamesRecursively(formNode.getNode(FIELDS_NODE), fieldNames);
        return fieldNames;
    }

    protected Set<String> collectDeclaredFileFieldNames(JCRNodeWrapper formNode) throws RepositoryException {
        Set<String> fieldNames = new HashSet<>();
        if (formNode == null || !formNode.hasNode(FIELDS_NODE)) {
            return fieldNames;
        }

        collectDeclaredFileFieldNamesRecursively(formNode.getNode(FIELDS_NODE), fieldNames);
        return fieldNames;
    }

    protected Set<String> getUserPropertyNames(JCRNodeWrapper node) throws RepositoryException {
        Set<String> propertyNames = new HashSet<>();
        PropertyIterator properties = node.getProperties();
        while (properties.hasNext()) {
            Property property = properties.nextProperty();
            if (property.getDefinition().isProtected()) {
                continue;
            }

            String propertyName = property.getName();
            if (propertyName.startsWith("jcr:") || propertyName.startsWith("j:")) {
                continue;
            }

            propertyNames.add(propertyName);
        }

        return propertyNames;
    }

    private void collectDeclaredFieldNamesRecursively(JCRNodeWrapper node, Set<String> fieldNames) throws RepositoryException {
        if (node.isNodeType(FMDB_FORM_ELEMENT) && !node.isNodeType(FMDB_NON_SUBMITTABLE)) {
            fieldNames.add(node.getName());
        }

        NodeIterator children = node.getNodes();
        while (children.hasNext()) {
            javax.jcr.Node child = children.nextNode();
            if (child instanceof JCRNodeWrapper childNode) {
                collectDeclaredFieldNamesRecursively(childNode, fieldNames);
            }
        }
    }

    private void collectDeclaredFileFieldNamesRecursively(JCRNodeWrapper node, Set<String> fieldNames) throws RepositoryException {
        if (node.isNodeType(FMDB_FILE_FIELD) && !node.isNodeType(FMDB_NON_SUBMITTABLE)) {
            fieldNames.add(node.getName());
        }

        NodeIterator children = node.getNodes();
        while (children.hasNext()) {
            javax.jcr.Node child = children.nextNode();
            if (child instanceof JCRNodeWrapper childNode) {
                collectDeclaredFileFieldNamesRecursively(childNode, fieldNames);
            }
        }
    }

    /**
     * Parses the multi-valued "logics" property into a list of {@link LogicRule} records.
     * Each value is a JSON string containing at least {@code logicId} and {@code sourceNodeId}.
     *
     * @return an empty list if the property is absent; never {@code null}.
     * @throws RepositoryException if a value contains malformed JSON.
     */
    protected List<LogicRule> parseLogicRules(JCRNodeWrapper node) throws RepositoryException {
        List<LogicRule> rules = new ArrayList<>();
        if (!node.hasProperty(LOGICS_PROPERTY)) {
            return rules;
        }

        for (Value value : node.getProperty(LOGICS_PROPERTY).getValues()) {
            try {
                JSONObject json = new JSONObject(value.getString());
                // Extract the two key fields; default to empty string when absent
                rules.add(new LogicRule(
                        json.optString(LOGIC_ID, ""),
                        json.optString(SOURCE_NODE_ID, ""),
                        value.getString()
                ));
            } catch (JSONException e) {
                throw new RepositoryException("Invalid JSON in logics property", e);
            }
        }

        return rules;
    }

    /**
     * Lightweight record representing one parsed conditional-logic rule.
     *
     * @param logicId      identifier used as the child node name under {@code logicsSrc/}
     * @param sourceNodeId UUID of the source form element this rule observes
     * @param rawJson      original JSON string (kept for error reporting)
     */
    protected record LogicRule(String logicId, String sourceNodeId, String rawJson) {
    }
}
