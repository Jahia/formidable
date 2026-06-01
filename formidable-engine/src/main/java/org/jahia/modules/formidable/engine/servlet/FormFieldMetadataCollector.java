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

    record Result(
            Map<String, FormDataParser.FieldInfo> fieldInfos,
            Map<String, List<ConditionalLogicRule>> fieldLogicRules,
            Map<String, String> logicIdToFieldName,
            Map<String, String> fieldParentContainer
    ) {
        FormDataParser.FieldMetadata toParserMetadata() {
            return new FormDataParser.FieldMetadata(fieldInfos);
        }
    }

    static Result collect(String formId, Locale locale) throws RepositoryException {
        return JCRTemplate.getInstance().doExecuteWithSystemSessionAsUser(null, "live", locale, systemSession -> {
            JCRNodeWrapper formNode = systemSession.getNodeByIdentifier(formId);
            return collectFromFormNode(formNode);
        });
    }

    static Result collectFromFormNode(JCRNodeWrapper formNode) throws RepositoryException {
        var fieldInfos = new HashMap<String, FormDataParser.FieldInfo>();
        var fieldLogicRules = new HashMap<String, List<ConditionalLogicRule>>();
        var logicIdToFieldName = new HashMap<String, String>();
        var fieldParentContainer = new HashMap<String, String>();

        var ctx = new CollectorContext(fieldInfos, fieldLogicRules, logicIdToFieldName, fieldParentContainer);

        if (!formNode.hasNode(FIELDS_NODE)) {
            log.debug("[FormFieldMetadataCollector] No '{}' child on form node '{}'",
                    FIELDS_NODE, formNode.getPath());
            return new Result(fieldInfos, fieldLogicRules, logicIdToFieldName, fieldParentContainer);
        }

        JCRNodeWrapper fieldList = formNode.getNode(FIELDS_NODE);
        NodeIterator it = fieldList.getNodes();
        while (it.hasNext()) {
            javax.jcr.Node child = it.nextNode();
            if (child instanceof JCRNodeWrapper w) {
                traverseRecursively(w, null, ctx);
            }
        }

        log.debug("[FormFieldMetadataCollector] Allowed fields: {}", fieldInfos.keySet());
        return new Result(fieldInfos, fieldLogicRules, logicIdToFieldName, fieldParentContainer);
    }

    // --- Internal ---

    private record CollectorContext(
            Map<String, FormDataParser.FieldInfo> fieldInfos,
            Map<String, List<ConditionalLogicRule>> fieldLogicRules,
            Map<String, String> logicIdToFieldName,
            Map<String, String> fieldParentContainer
    ) {}

    private static void traverseRecursively(JCRNodeWrapper node, String parentContainerName, CollectorContext ctx)
            throws RepositoryException {
        String nodeType = node.getPrimaryNodeTypeName();
        String currentContainerName = parentContainerName;
        boolean nonSubmittable = node.isNodeType("fmdbmix:nonSubmittable");

        // Only explicit structural containers can propagate a conditional-logic
        // visibility context to descendant fields.
        boolean isContainer = node.isNodeType("fmdbmix:formContainer");
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
                && !nonSubmittable) {
            registerField(node, currentContainerName, ctx);
        }

        NodeIterator it = node.getNodes();
        while (it.hasNext()) {
            javax.jcr.Node child = it.nextNode();
            if (child instanceof JCRNodeWrapper childNode) {
                traverseRecursively(childNode, currentContainerName, ctx);
            }
        }
    }

    private static void registerField(JCRNodeWrapper node, String parentContainerName, CollectorContext ctx)
            throws RepositoryException {
        String name = node.getName();
        String nodeType = node.getPrimaryNodeTypeName();
        if (ctx.fieldInfos.containsKey(name)) {
            log.warn("[FormFieldMetadataCollector] Duplicate field name '{}'; later metadata overwrites.", name);
        }

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

        ctx.fieldInfos.put(name, buildFieldInfo(node, nodeType));
    }

    private static void resolveLogicsSrc(JCRNodeWrapper node, List<ConditionalLogicRule> rules, CollectorContext ctx)
            throws RepositoryException {
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
    }

    private static Set<String> collectChoices(JCRNodeWrapper node, String fieldName, String propName)
            throws RepositoryException {
        if (!node.hasProperty(propName)) return Set.of();
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
        return choices.isEmpty() ? Set.of() : choices;
    }

    private static Set<String> collectAcceptTypes(JCRNodeWrapper node) throws RepositoryException {
        if (!node.hasProperty("accept")) {
            return Set.of();
        }

        Set<String> accepted = java.util.Arrays.stream(node.getProperty("accept").getValues())
                .map(v -> {
                    try {
                        return v.getString().trim();
                    } catch (Exception e2) {
                        return "";
                    }
                })
                .filter(s -> !s.isBlank())
                .map(FormDataParser::resolveAcceptToken)
                .collect(java.util.stream.Collectors.toSet());

        return accepted.isEmpty() ? Set.of() : accepted;
    }

    private static FormDataParser.FieldInfo buildFieldInfo(JCRNodeWrapper node, String nodeType) throws RepositoryException {
        boolean nonSubmittable = node.isNodeType("fmdbmix:nonSubmittable");
        boolean choiceField = node.isNodeType("fmdbmix:choiceField");
        boolean fileField = node.isNodeType("fmdbmix:fileField");
        boolean emailField = node.isNodeType("fmdbmix:emailField");
        boolean dateField = node.isNodeType("fmdbmix:dateField");
        boolean datetimeLocalField = node.isNodeType("fmdbmix:datetimeLocalField");
        boolean colorField = node.isNodeType("fmdbmix:colorField");

        Set<String> choices = choiceField ? collectChoices(node, node.getName(), resolveChoicePropertyName(node)) : Set.of();
        Set<String> acceptedTypes = fileField ? collectAcceptTypes(node) : Set.of();
        FormDataParser.FieldConstraints constraints = readConstraints(node, dateField, datetimeLocalField);

        return new FormDataParser.FieldInfo(
                nodeType,
                nonSubmittable,
                choiceField,
                fileField,
                emailField,
                dateField,
                datetimeLocalField,
                colorField,
                choices,
                acceptedTypes,
                constraints
        );
    }

    private static String resolveChoicePropertyName(JCRNodeWrapper node) throws RepositoryException {
        if (node.hasProperty("choices")) {
            return "choices";
        }
        if (node.hasProperty("options")) {
            return "options";
        }
        return "choices";
    }

    private static FormDataParser.FieldConstraints readConstraints(
            JCRNodeWrapper node,
            boolean dateField,
            boolean datetimeLocalField
    )
            throws RepositoryException {
        boolean required  = readBoolean(node, "required");
        long minLength    = readLong(node, "minLength");
        long maxLength    = readLong(node, "maxLength");
        String pattern    = readString(node, "pattern");
        String minDate    = null;
        String maxDate    = null;

        if (dateField) {
            minDate = readDateAsIso(node, "min", false);
            maxDate = readDateAsIso(node, "max", false);
        } else if (datetimeLocalField) {
            minDate = readDateAsIso(node, "min", true);
            maxDate = readDateAsIso(node, "max", true);
        }

        if (!required && minLength < 0 && maxLength < 0 && pattern == null
                && minDate == null && maxDate == null) {
            return null;
        }
        return new FormDataParser.FieldConstraints(required, minLength, maxLength, pattern, minDate, maxDate);
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
