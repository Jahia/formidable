package org.jahia.modules.formidable.engine.servlet;

import org.jahia.modules.formidable.engine.actions.FormDataParser;
import org.jahia.modules.formidable.engine.logic.ConditionalLogicRule;
import org.jahia.modules.formidable.engine.options.FormidableOptionsSourceService;
import org.jahia.modules.formidable.engine.util.JcrProps;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRTemplate;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Value;
import java.util.*;

import static org.jahia.modules.formidable.engine.util.FormidableJcrConstants.FIELDS_NODE;
import static org.jahia.modules.formidable.engine.util.FormidableJcrConstants.FORM_CONTAINER_MIXIN;
import static org.jahia.modules.formidable.engine.util.FormidableJcrConstants.FORM_ELEMENT_MIXIN;
import static org.jahia.modules.formidable.engine.util.FormidableJcrConstants.FORM_LOGIC_ELEMENT_MIXIN;
import static org.jahia.modules.formidable.engine.util.FormidableJcrConstants.LOGIC_NODE_SOURCE_PROPERTY;
import static org.jahia.modules.formidable.engine.util.FormidableJcrConstants.LOGICS_PROPERTY;
import static org.jahia.modules.formidable.engine.util.FormidableJcrConstants.LOGICS_SRC_NODE;
import static org.jahia.modules.formidable.engine.util.FormidableJcrConstants.NON_SUBMITTABLE_MIXIN;
import static org.jahia.modules.formidable.engine.util.FormidableJcrConstants.WORKSPACE_LIVE;

/**
 * Traverses the JCR form tree and collects all field metadata needed by the submission pipeline:
 * allowed field names, types, choices, accept types, constraints, logic rules, and container hierarchy.
 */
class FormFieldMetadataCollector {

    private static final Logger log = LoggerFactory.getLogger(FormFieldMetadataCollector.class);
    private static final String CHOICES_PROPERTY = "choices";
    private static final String UNIFIED_OPTIONS_PROPERTY = "fmdb:options";
    // Mixins whose options are resolved by the engine instead of being stored on the
    // node; must stay aligned with FormidableOptionsSourceService.resolveForField.
    private static final String[] RESOLVED_OPTIONS_MIXINS = {"fmdbmix:sourcedOptions", "fmdbmix:categoryOptions"};

    record Result(
            Map<String, FormDataParser.FieldInfo> fieldInfos,
            Map<String, List<ConditionalLogicRule>> fieldLogicRules,
            Map<String, String> logicIdToFieldName,
            Map<String, Set<String>> fieldParentContainers
    ) {
        FormDataParser.FieldMetadata toParserMetadata() {
            return new FormDataParser.FieldMetadata(fieldInfos);
        }
    }

    /**
     * Delivers the options of a sourced choice field at collection time (D11: submitted
     * values are validated against the re-resolved list). Kept as a seam so the collector
     * stays unit-testable without the OSGi service.
     */
    @FunctionalInterface
    interface SourcedOptionsResolver {
        /**
         * @return the options as JSON-encoded strings, or null when the field does not
         *         use an options source
         */
        String[] resolve(JCRNodeWrapper fieldNode) throws Exception;
    }

    // Without a resolver, a sourced field behaves as unresolvable: reject rather than accept blindly.
    private static final SourcedOptionsResolver NO_RESOLVER = node -> {
        throw new IllegalStateException("No options source resolver available");
    };

    static Result collect(String formId, Locale locale, FormidableOptionsSourceService optionsSourceService)
            throws RepositoryException {
        return JCRTemplate.getInstance().doExecuteWithSystemSessionAsUser(null, WORKSPACE_LIVE, locale, systemSession -> {
            JCRNodeWrapper formNode = systemSession.getNodeByIdentifier(formId);
            return collectFromFormNode(formNode,
                    node -> optionsSourceService.resolveForField(node, locale.toLanguageTag()));
        });
    }

    static Result collectFromFormNode(JCRNodeWrapper formNode) throws RepositoryException {
        return collectFromFormNode(formNode, NO_RESOLVER);
    }

    static Result collectFromFormNode(JCRNodeWrapper formNode, SourcedOptionsResolver optionsResolver)
            throws RepositoryException {
        var fieldInfos = new HashMap<String, FormDataParser.FieldInfo>();
        var fieldLogicRules = new HashMap<String, List<ConditionalLogicRule>>();
        var logicIdToFieldName = new HashMap<String, String>();
        var fieldParentContainers = new HashMap<String, Set<String>>();

        var ctx = new CollectorContext(fieldInfos, fieldLogicRules, logicIdToFieldName, fieldParentContainers, optionsResolver);

        if (!formNode.hasNode(FIELDS_NODE)) {
            log.debug("[FormFieldMetadataCollector] No '{}' child on form node '{}'",
                    FIELDS_NODE, formNode.getPath());
            return new Result(fieldInfos, fieldLogicRules, logicIdToFieldName, fieldParentContainers);
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
        return new Result(fieldInfos, fieldLogicRules, logicIdToFieldName, fieldParentContainers);
    }

    // --- Internal ---

    private record CollectorContext(
            Map<String, FormDataParser.FieldInfo> fieldInfos,
            Map<String, List<ConditionalLogicRule>> fieldLogicRules,
            Map<String, String> logicIdToFieldName,
            Map<String, Set<String>> fieldParentContainers,
            SourcedOptionsResolver optionsResolver
    ) {}

    private static void traverseRecursively(JCRNodeWrapper node, String parentContainerName, CollectorContext ctx)
            throws RepositoryException {
        String currentContainerName = parentContainerName;
        boolean nonSubmittable = node.isNodeType(NON_SUBMITTABLE_MIXIN);

        // Only explicit structural containers can propagate a conditional-logic
        // visibility context to descendant fields.
        boolean isContainer = node.isNodeType(FORM_CONTAINER_MIXIN);
        if (isContainer) {
            String containerName = node.getName();
            if (node.hasProperty(LOGICS_PROPERTY)) {
                List<ConditionalLogicRule> rules = ConditionalLogicRule.parse(node.getProperty(LOGICS_PROPERTY).getValues());
                if (!rules.isEmpty()) {
                    ctx.fieldLogicRules.put(containerName, rules);
                    resolveLogicsSrc(node, rules, ctx);
                    currentContainerName = containerName;
                }
            }
        }

        if (node.isNodeType(FORM_ELEMENT_MIXIN)
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

        // Track parent container before the duplicate check so that all containers
        // are recorded. isHidden() treats a field as hidden only when ALL its parents
        // are hidden, mirroring the front-end closest() logic.
        if (parentContainerName != null) {
            ctx.fieldParentContainers.computeIfAbsent(name, k -> new HashSet<>()).add(parentContainerName);
        }

        if (ctx.fieldInfos.containsKey(name)) {
            log.debug("[FormFieldMetadataCollector] Duplicate field name '{}' — parent containers: {}",
                    name, ctx.fieldParentContainers.get(name));
            return;
        }

        if (node.isNodeType(FORM_LOGIC_ELEMENT_MIXIN) && node.hasProperty(LOGICS_PROPERTY)) {
            List<ConditionalLogicRule> rules = ConditionalLogicRule.parse(node.getProperty(LOGICS_PROPERTY).getValues());
            if (!rules.isEmpty()) {
                ctx.fieldLogicRules.put(name, rules);
                resolveLogicsSrc(node, rules, ctx);
            }
        }

        ctx.fieldInfos.put(name, buildFieldInfo(node, nodeType, ctx.optionsResolver));
    }

    private static void resolveLogicsSrc(JCRNodeWrapper node, List<ConditionalLogicRule> rules, CollectorContext ctx)
            throws RepositoryException {
        if (!node.hasNode(LOGICS_SRC_NODE)) {
            return;
        }

        JCRNodeWrapper logicsSrc = node.getNode(LOGICS_SRC_NODE);
        for (ConditionalLogicRule rule : rules) {
            String logicId = rule.logicId();
            if (logicId != null && !logicId.isEmpty() && logicsSrc.hasNode(logicId)) {
                JCRNodeWrapper srcNode = logicsSrc.getNode(logicId);
                try {
                    JCRNodeWrapper sourceField = (JCRNodeWrapper) srcNode.getProperty(LOGIC_NODE_SOURCE_PROPERTY).getNode();
                    ctx.logicIdToFieldName.put(logicId, sourceField.getName());
                } catch (Exception e) {
                    log.debug("[FormFieldMetadataCollector] Broken weakref for logicId '{}' on '{}'",
                            logicId, node.getPath());
                }
            }
        }
    }

    private static boolean usesResolvedOptions(JCRNodeWrapper node) throws RepositoryException {
        for (String mixin : RESOLVED_OPTIONS_MIXINS) {
            if (node.isNodeType(mixin)) {
                return true;
            }
        }

        return false;
    }

    private static Set<String> collectChoices(JCRNodeWrapper node, String fieldName, String propName)
            throws RepositoryException {
        if (!node.hasProperty(propName)) return Set.of();
        Value[] values = node.getProperty(propName).getValues();
        Set<String> choices = new HashSet<>();
        for (Value v : values) {
            addChoiceValue(choices, v.getString(), fieldName);
        }
        return choices.isEmpty() ? Set.of() : choices;
    }

    private static Set<String> extractChoiceValues(String[] jsonOptions, String fieldName) {
        if (jsonOptions == null) {
            return Set.of();
        }
        Set<String> choices = new HashSet<>();
        for (String option : jsonOptions) {
            addChoiceValue(choices, option, fieldName);
        }
        return choices.isEmpty() ? Set.of() : choices;
    }

    private static void addChoiceValue(Set<String> choices, String jsonOption, String fieldName) {
        try {
            JSONObject obj = new JSONObject(jsonOption);
            String val = obj.optString("value", "").trim();
            if (!val.isEmpty()) choices.add(val);
        } catch (Exception e) {
            log.debug("[FormFieldMetadataCollector] Could not parse choice JSON for field '{}'", fieldName);
        }
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

    private static FormDataParser.FieldInfo buildFieldInfo(JCRNodeWrapper node, String nodeType,
            SourcedOptionsResolver optionsResolver) throws RepositoryException {
        boolean nonSubmittable = node.isNodeType(NON_SUBMITTABLE_MIXIN);
        boolean choiceField = node.isNodeType("fmdbmix:choiceField");
        boolean fileField = node.isNodeType("fmdbmix:fileField");
        boolean emailField = node.isNodeType("fmdbmix:emailField");
        boolean dateField = node.isNodeType("fmdbmix:dateField");
        boolean datetimeLocalField = node.isNodeType("fmdbmix:datetimeLocalField");
        boolean colorField = node.isNodeType("fmdbmix:colorField");
        boolean numberField = node.isNodeType("fmdbmix:numberField");
        boolean booleanField = node.isNodeType("fmdbmix:booleanField");

        Set<String> choices = Set.of();
        boolean choicesUnresolvable = false;
        if (choiceField) {
            if (usesResolvedOptions(node)) {
                // D11: submitted values are checked against the re-resolved source. When the
                // source cannot deliver, the field is flagged so the validator rejects any
                // non-empty value (and an empty one on a required field) instead of accepting
                // blindly what an empty allowlist would let through.
                try {
                    choices = extractChoiceValues(optionsResolver.resolve(node), node.getName());
                } catch (Exception e) {
                    log.warn("[FormFieldMetadataCollector] Options source of field '{}' failed at validation time: {}",
                            node.getName(), e.getMessage());
                    choicesUnresolvable = true;
                }
            } else {
                choices = collectChoices(node, node.getName(), resolveChoicePropertyName(node));
            }
        }
        Set<String> acceptedTypes = fileField ? collectAcceptTypes(node) : Set.of();
        FormDataParser.FieldConstraints constraints = readConstraints(node, dateField, datetimeLocalField, numberField);

        return new FormDataParser.FieldInfo(
                nodeType,
                nonSubmittable,
                choiceField,
                fileField,
                emailField,
                dateField,
                datetimeLocalField,
                colorField,
                numberField,
                booleanField,
                choices,
                choicesUnresolvable,
                acceptedTypes,
                constraints
        );
    }

    private static String resolveChoicePropertyName(JCRNodeWrapper node) throws RepositoryException {
        if (node.hasProperty(UNIFIED_OPTIONS_PROPERTY)) {
            return UNIFIED_OPTIONS_PROPERTY;
        }
        // Legacy names, kept for content not yet migrated to fmdbmix:manualOptions.
        if (node.hasProperty(CHOICES_PROPERTY)) {
            return CHOICES_PROPERTY;
        }
        if (node.hasProperty("options")) {
            return "options";
        }
        return UNIFIED_OPTIONS_PROPERTY;
    }

    private static FormDataParser.FieldConstraints readConstraints(
            JCRNodeWrapper node,
            boolean dateField,
            boolean datetimeLocalField,
            boolean numberField
    ) {
        boolean required  = JcrProps.bool(node, "required", false);
        long minLength    = JcrProps.longValue(node, "minLength", -1L);
        long maxLength    = JcrProps.longValue(node, "maxLength", -1L);
        String pattern    = JcrProps.string(node, "pattern", null);
        if (pattern != null && pattern.isBlank()) {
            pattern = null;
        }
        String minDate    = null;
        String maxDate    = null;
        Double minNumber  = null;
        Double maxNumber  = null;

        if (dateField) {
            minDate = JcrProps.dateAsIso(node, "min", false, null);
            maxDate = JcrProps.dateAsIso(node, "max", false, null);
        } else if (datetimeLocalField) {
            minDate = JcrProps.dateAsIso(node, "min", true, null);
            maxDate = JcrProps.dateAsIso(node, "max", true, null);
        } else if (numberField) {
            // Convention of the number field types (e.g. fmdbext:rating/scale): the
            // rendered range is declared through minValue/maxValue LONG properties.
            minNumber = JcrProps.doubleOrNull(node, "minValue");
            maxNumber = JcrProps.doubleOrNull(node, "maxValue");
        }

        if (!required && minLength < 0 && maxLength < 0 && pattern == null
                && minDate == null && maxDate == null
                && minNumber == null && maxNumber == null) {
            return null;
        }
        return new FormDataParser.FieldConstraints(
                required, minLength, maxLength, pattern, minDate, maxDate, minNumber, maxNumber);
    }
}
