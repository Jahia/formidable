package org.jahia.modules.formidable.engine.servlet;

import org.jahia.modules.formidable.engine.actions.FormDataParser;
import org.jahia.modules.formidable.engine.logic.ConditionalLogicRule;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRTemplate;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Value;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Traverses the JCR form tree and collects all field metadata needed by the submission pipeline:
 * allowed field names, types, choices, accept types, constraints, logic rules, and container hierarchy.
 */
class FormFieldMetadataCollector {

    private static final Logger log = LoggerFactory.getLogger(FormFieldMetadataCollector.class);

    private static final String FIELDS_NODE = "fields";
    private static final String LOGICS_SRC = "logicsSrc";
    private static final String LOGIC_NODE_SOURCE = "logicNodeSource";
    private static final Set<String> NON_SUBMITTABLE_FORM_ELEMENT_TYPES = Set.of(
            "fmdb:fieldset",
            "fmdb:inputButton"
    );

    record Result(
            Set<String> allowedFieldNames,
            Map<String, String> fieldTypes,
            Map<String, Set<String>> allowedChoices,
            Map<String, Set<String>> fieldAcceptTypes,
            Map<String, FormDataParser.FieldConstraints> fieldConstraints,
            Map<String, List<ConditionalLogicRule>> fieldLogicRules,
            Map<String, String> logicIdToFieldName,
            Map<String, String> fieldParentContainer
    ) {
        FormDataParser.FieldMetadata toParserMetadata() {
            return new FormDataParser.FieldMetadata(
                    allowedFieldNames, fieldTypes, allowedChoices, fieldAcceptTypes, fieldConstraints);
        }
    }

    static Result collect(String formId, Locale locale) {
        var allowedFieldNames    = new HashSet<String>();
        var fieldTypes           = new HashMap<String, String>();
        var allowedChoices       = new HashMap<String, Set<String>>();
        var fieldAcceptTypes     = new HashMap<String, Set<String>>();
        var fieldConstraints     = new HashMap<String, FormDataParser.FieldConstraints>();
        var fieldLogicRules      = new HashMap<String, List<ConditionalLogicRule>>();
        var logicIdToFieldName   = new HashMap<String, String>();
        var fieldParentContainer = new HashMap<String, String>();

        var ctx = new CollectorContext(allowedFieldNames, fieldTypes, allowedChoices,
                fieldAcceptTypes, fieldConstraints, fieldLogicRules, logicIdToFieldName, fieldParentContainer);

        try {
            JCRTemplate.getInstance().doExecuteWithSystemSessionAsUser(null, "live", locale, systemSession -> {
                JCRNodeWrapper formNode = systemSession.getNodeByIdentifier(formId);
                if (!formNode.hasNode(FIELDS_NODE)) {
                    log.debug("[FormFieldMetadataCollector] No '{}' child on form node '{}'",
                            FIELDS_NODE, formNode.getPath());
                    return null;
                }

                JCRNodeWrapper fieldList = formNode.getNode(FIELDS_NODE);
                NodeIterator it = fieldList.getNodes();
                while (it.hasNext()) {
                    javax.jcr.Node child = it.nextNode();
                    if (child instanceof JCRNodeWrapper w) {
                        traverseRecursively(w, null, ctx);
                    }
                }
                return null;
            });
        } catch (RepositoryException e) {
            log.warn("[FormFieldMetadataCollector] Could not collect field info from form '{}': {}",
                    formId, e.getMessage());
        }

        log.debug("[FormFieldMetadataCollector] Allowed fields: {}", allowedFieldNames);
        return new Result(allowedFieldNames, fieldTypes, allowedChoices,
                fieldAcceptTypes, fieldConstraints, fieldLogicRules, logicIdToFieldName, fieldParentContainer);
    }

    // --- Internal ---

    private record CollectorContext(
            Set<String> allowedFieldNames,
            Map<String, String> fieldTypes,
            Map<String, Set<String>> allowedChoices,
            Map<String, Set<String>> fieldAcceptTypes,
            Map<String, FormDataParser.FieldConstraints> fieldConstraints,
            Map<String, List<ConditionalLogicRule>> fieldLogicRules,
            Map<String, String> logicIdToFieldName,
            Map<String, String> fieldParentContainer
    ) {}

    private static void traverseRecursively(JCRNodeWrapper node, String parentContainerName, CollectorContext ctx) {
        try {
            String nodeType = node.getPrimaryNodeTypeName();
            String currentContainerName = parentContainerName;

            boolean isContainer = node.isNodeType("fmdbmix:formStep")
                    || (NON_SUBMITTABLE_FORM_ELEMENT_TYPES.contains(nodeType) && node.isNodeType("fmdbmix:formLogicElement"));
            if (isContainer) {
                String containerName = node.getName();
                if (node.hasProperty("logics")) {
                    List<ConditionalLogicRule> rules = ConditionalLogicRule.parse(node.getProperty("logics").getValues());
                    if (!rules.isEmpty()) {
                        ctx.fieldLogicRules.put(containerName, rules);
                        resolveLogicsSrc(node, rules, ctx);
                        currentContainerName = containerName;
                    }
                }
            }

            if (node.isNodeType("fmdbmix:formElement")
                    && !NON_SUBMITTABLE_FORM_ELEMENT_TYPES.contains(nodeType)) {
                registerField(node, nodeType, currentContainerName, ctx);
            }

            NodeIterator it = node.getNodes();
            while (it.hasNext()) {
                javax.jcr.Node child = it.nextNode();
                if (child instanceof JCRNodeWrapper childNode) {
                    traverseRecursively(childNode, currentContainerName, ctx);
                }
            }
        } catch (RepositoryException e) {
            log.debug("[FormFieldMetadataCollector] Cannot traverse node '{}': {}", node.getPath(), e.getMessage());
        }
    }

    private static void registerField(JCRNodeWrapper node, String nodeType, String parentContainerName, CollectorContext ctx) {
        try {
            String name = node.getName();
            if (!ctx.allowedFieldNames.add(name)) {
                log.warn("[FormFieldMetadataCollector] Duplicate field name '{}'; later metadata overwrites.", name);
            }
            ctx.fieldTypes.put(name, nodeType);

            if (parentContainerName != null) {
                ctx.fieldParentContainer.put(name, parentContainerName);
            }

            if (node.isNodeType("fmdbmix:formLogicElement") && node.hasProperty("logics")) {
                List<ConditionalLogicRule> rules = ConditionalLogicRule.parse(node.getProperty("logics").getValues());
                if (!rules.isEmpty()) {
                    ctx.fieldLogicRules.put(name, rules);
                    resolveLogicsSrc(node, rules, ctx);
                }
            }

            switch (nodeType) {
                case "fmdb:checkbox", "fmdb:radio" -> collectChoices(node, name, "choices", ctx);
                case "fmdb:select"                 -> collectChoices(node, name, "options", ctx);
                case "fmdb:inputFile"              -> {
                    if (node.hasProperty("accept")) {
                        Set<String> accepted = java.util.Arrays.stream(node.getProperty("accept").getValues())
                                .map(v -> { try { return v.getString().trim(); } catch (Exception e2) { return ""; } })
                                .filter(s -> !s.isBlank())
                                .map(FormDataParser::resolveAcceptToken)
                                .collect(java.util.stream.Collectors.toSet());
                        if (!accepted.isEmpty()) ctx.fieldAcceptTypes.put(name, accepted);
                    }
                }
            }

            FormDataParser.FieldConstraints c = readConstraints(node, nodeType);
            if (c != null) ctx.fieldConstraints.put(name, c);
        } catch (RepositoryException e) {
            log.debug("[FormFieldMetadataCollector] Cannot collect metadata for '{}': {}", node.getPath(), e.getMessage());
        }
    }

    private static void resolveLogicsSrc(JCRNodeWrapper node, List<ConditionalLogicRule> rules, CollectorContext ctx) {
        try {
            if (!node.hasNode(LOGICS_SRC)) {
                return;
            }

            JCRNodeWrapper logicsSrc = node.getNode(LOGICS_SRC);
            for (ConditionalLogicRule rule : rules) {
                String logicId = rule.logicId();
                if (logicId == null || logicId.isEmpty()) {
                    continue;
                }

                if (!logicsSrc.hasNode(logicId)) {
                    continue;
                }

                JCRNodeWrapper srcNode = logicsSrc.getNode(logicId);
                try {
                    JCRNodeWrapper sourceField = (JCRNodeWrapper) srcNode.getProperty(LOGIC_NODE_SOURCE).getNode();
                    ctx.logicIdToFieldName.put(logicId, sourceField.getName());
                } catch (Exception e) {
                    log.debug("[FormFieldMetadataCollector] Broken weakref for logicId '{}' on '{}'",
                            logicId, node.getPath());
                }
            }
        } catch (RepositoryException e) {
            log.debug("[FormFieldMetadataCollector] Cannot resolve logicsSrc on '{}': {}", node.getPath(), e.getMessage());
        }
    }

    private static void collectChoices(JCRNodeWrapper node, String fieldName, String propName, CollectorContext ctx) {
        try {
            if (!node.hasProperty(propName)) return;
            Value[] values = node.getProperty(propName).getValues();
            Set<String> choices = new HashSet<>();
            for (Value v : values) {
                try {
                    JSONObject obj = new JSONObject(v.getString());
                    String val = obj.optString("value", "").trim();
                    if (!val.isEmpty()) choices.add(val);
                } catch (Exception e) {
                    log.debug("[FormFieldMetadataCollector] Could not parse choice JSON for field '{}'", fieldName);
                }
            }
            if (!choices.isEmpty()) ctx.allowedChoices.put(fieldName, choices);
        } catch (RepositoryException e) {
            log.debug("[FormFieldMetadataCollector] Cannot read '{}' on '{}': {}", propName, node.getPath(), e.getMessage());
        }
    }

    private static FormDataParser.FieldConstraints readConstraints(JCRNodeWrapper node, String nodeType) {
        try {
            boolean required  = readBoolean(node, "required");
            long minLength    = readLong(node, "minLength");
            long maxLength    = readLong(node, "maxLength");
            String pattern    = readString(node, "pattern");
            String minDate    = null;
            String maxDate    = null;

            if ("fmdb:inputDate".equals(nodeType)) {
                minDate = readDateAsIso(node, "min", false);
                maxDate = readDateAsIso(node, "max", false);
            } else if ("fmdb:inputDatetimeLocal".equals(nodeType)) {
                minDate = readDateAsIso(node, "min", true);
                maxDate = readDateAsIso(node, "max", true);
            }

            if (!required && minLength < 0 && maxLength < 0 && pattern == null
                    && minDate == null && maxDate == null) {
                return null;
            }
            return new FormDataParser.FieldConstraints(required, minLength, maxLength, pattern, minDate, maxDate);
        } catch (RepositoryException e) {
            log.debug("[FormFieldMetadataCollector] Cannot read constraints for '{}': {}", node.getPath(), e.getMessage());
            return null;
        }
    }

    private static boolean readBoolean(JCRNodeWrapper node, String prop) throws RepositoryException {
        return node.hasProperty(prop) && node.getProperty(prop).getBoolean();
    }

    private static long readLong(JCRNodeWrapper node, String prop) throws RepositoryException {
        return node.hasProperty(prop) ? node.getProperty(prop).getLong() : -1L;
    }

    private static String readString(JCRNodeWrapper node, String prop) throws RepositoryException {
        if (!node.hasProperty(prop)) return null;
        String v = node.getProperty(prop).getString();
        return v.isBlank() ? null : v;
    }

    private static String readDateAsIso(JCRNodeWrapper node, String prop, boolean includeTime)
            throws RepositoryException {
        if (!node.hasProperty(prop)) return null;
        Calendar cal = node.getProperty(prop).getDate();
        var ldt = cal.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
        return includeTime
                ? ldt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm"))
                : ldt.toLocalDate().toString();
    }
}

