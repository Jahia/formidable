package org.jahia.modules.formidable.engine.servlet;

import org.jahia.modules.formidable.engine.actions.FormDataParser;
import org.jahia.modules.formidable.engine.logic.ConditionalLogicRule;
import org.jahia.modules.formidable.engine.options.FormidableOptionsSourceService;
import org.jahia.modules.formidable.engine.options.ManualOptionEntries;
import org.jahia.modules.formidable.engine.util.JcrProps;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRSessionFactory;
import org.jahia.services.content.JCRTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.Node;
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
import static org.jahia.modules.formidable.engine.util.FormidableJcrConstants.MANUAL_OPTIONS_MIXIN;
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
    private static final String[] RESOLVED_OPTIONS_MIXINS =
            {"fmdbmix:sourcedOptions", "fmdbmix:categoryOptions", "fmdbmix:contentOptions"};

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
         * @throws RepositoryException on repository access failure; runtime exceptions
         *         from the source itself propagate as-is — the caller treats every
         *         failure alike (the field becomes unresolvable, D11)
         */
        String[] resolve(JCRNodeWrapper fieldNode) throws RepositoryException;
    }

    // Without a resolver, a sourced field behaves as unresolvable: reject rather than accept blindly.
    private static final SourcedOptionsResolver NO_RESOLVER = node -> {
        throw new IllegalStateException("No options source resolver available");
    };

    static Result collect(String formId, Locale locale, FormidableOptionsSourceService optionsSourceService)
            throws RepositoryException {
        // The service reference is mandatory on the servlet, but degrade to the
        // unresolvable-safe resolver anyway: without it only sourced/category fields
        // become unverifiable, instead of every submission failing.
        // Options are re-resolved through the requester's live session, not the
        // collector's system session: content/category resolution must carry the
        // visitor's ACLs so the accepted set mirrors the rendered one (D11) — a
        // published-but-ACL-hidden content is otherwise accepted at validation while
        // absent from the rendered options. A field unreadable by the requester
        // surfaces as a resolution failure, i.e. unresolvable, i.e. rejected.
        SourcedOptionsResolver resolver = optionsSourceService == null
                ? NO_RESOLVER
                : node -> optionsSourceService.resolveForField(
                        JCRSessionFactory.getInstance().getCurrentUserSession(WORKSPACE_LIVE, locale)
                                .getNodeByIdentifier(node.getIdentifier()),
                        locale.toLanguageTag());

        return JCRTemplate.getInstance().doExecuteWithSystemSessionAsUser(null, WORKSPACE_LIVE, locale, systemSession -> {
            JCRNodeWrapper formNode = systemSession.getNodeByIdentifier(formId);
            return collectFromFormNode(formNode, resolver);
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

        // Only explicit structural containers can propagate a conditional-logic
        // visibility context to descendant fields.
        if (node.isNodeType(FORM_CONTAINER_MIXIN)) {
            currentContainerName = registerConditionalContainer(node, parentContainerName, ctx);
        }

        if (node.isNodeType(FORM_ELEMENT_MIXIN)
                && !node.isNodeType(NON_SUBMITTABLE_MIXIN)) {
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

    /**
     * Registers a conditional container's rules and its edge to the enclosing
     * container, and returns the container name the descendants inherit — unchanged
     * when this container carries no rules.
     *
     * <p>A conditional container nested in another one chains verdicts through the
     * same parent map as the fields: a field whose direct container is a fieldset must
     * still inherit the enclosing step's verdict, which the evaluator walks parent by
     * parent. A name equal to the enclosing container's (JCR names are only unique
     * among siblings) would be a self-edge: the evaluator's cycle guard resolves it to
     * VISIBLE, and one visible parent wins — the REAL enclosing chain would never be
     * consulted. Collision noise, not structure: skipped.
     */
    private static String registerConditionalContainer(JCRNodeWrapper node, String parentContainerName,
            CollectorContext ctx) throws RepositoryException {
        if (!node.hasProperty(LOGICS_PROPERTY)) {
            return parentContainerName;
        }

        List<ConditionalLogicRule> rules = ConditionalLogicRule.parse(node.getProperty(LOGICS_PROPERTY).getValues());
        if (rules.isEmpty()) {
            return parentContainerName;
        }

        String containerName = node.getName();
        ctx.fieldLogicRules.put(containerName, rules);
        resolveLogicsSrc(node, rules, ctx);
        if (parentContainerName != null && !containerName.equals(parentContainerName)) {
            ctx.fieldParentContainers
                    .computeIfAbsent(containerName, k -> new HashSet<>())
                    .add(parentContainerName);
        }

        return containerName;
    }

    private static void registerField(JCRNodeWrapper node, String parentContainerName, CollectorContext ctx)
            throws RepositoryException {
        String name = node.getName();
        String nodeType = node.getPrimaryNodeTypeName();

        // Track parent container before the duplicate check so that all containers
        // are recorded. isHidden() treats a field as hidden only when ALL its parents
        // are hidden, mirroring the front-end closest() logic. A field named like its
        // own container would be a self-edge (see the container-side comment): skipped.
        if (parentContainerName != null && !name.equals(parentContainerName)) {
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

    /**
     * The allowed values of a manual choice field. Option values are one identity
     * set across languages, authored in the site's default language and re-aligned
     * onto the other languages by ManualOptionsLanguageSync — so the allowed set is
     * read from the DEFAULT language, never from the submitter-chosen locale: a
     * diverged or legacy translation (not yet re-aligned) can neither smuggle
     * values in nor reject legitimate default-language values. Without a
     * default-language master (a field authored in another language only), the
     * localized read stays the identity.
     *
     * Keyed on the MIXIN, not on the submitted locale carrying fmdb:options: a form
     * rendered in a language nobody translated still renders the master's entries
     * (ManualOptionsDisplayService), so the values it can legitimately submit must be
     * read there too.
     */
    private static Set<String> collectManualChoices(JCRNodeWrapper node) throws RepositoryException {
        if (node.isNodeType(MANUAL_OPTIONS_MIXIN)) {
            Set<String> masterChoices = collectDefaultLanguageChoices(node);
            if (masterChoices != null) {
                return masterChoices;
            }
        }

        return collectChoices(node, node.getName(), resolveChoicePropertyName(node));
    }

    private static Set<String> collectDefaultLanguageChoices(JCRNodeWrapper node) throws RepositoryException {
        String defaultLanguage = node.getResolveSite() != null
                ? node.getResolveSite().getDefaultLanguage()
                : null;
        if (defaultLanguage == null) {
            return null;
        }

        Node master = ManualOptionEntries.findTranslation(node, defaultLanguage);
        if (master == null) {
            return null;
        }

        Set<String> choices = new HashSet<>();
        for (String raw : ManualOptionEntries.readOptions(master)) {
            addChoiceValue(choices, raw, node.getName());
        }

        return choices.isEmpty() ? null : choices;
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
        // One shared reading of the entry storage; the trim-and-drop-empties policy
        // is this allowed-set's own (the language sync keeps entries verbatim).
        String value = ManualOptionEntries.value(jsonOption);
        if (value == null) {
            log.debug("[FormFieldMetadataCollector] Could not parse choice JSON for field '{}'", fieldName);
            return;
        }

        String val = value.trim();
        if (!val.isEmpty()) {
            choices.add(val);
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
                choices = collectManualChoices(node);
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
            minDate = resolveDateBound(node, true, false);
            maxDate = resolveDateBound(node, false, false);
        } else if (datetimeLocalField) {
            minDate = resolveDateBound(node, true, true);
            maxDate = resolveDateBound(node, false, true);
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

    /**
     * Resolves one bound of a date-typed field from its bound mode (fmdbmix:dateBounds /
     * fmdbmix:datetimeBounds): a fixed date, the submission day, the submission day
     * shifted by a signed offset, or nothing — the modes are exclusive, so there is no
     * bound-combination rule. The day-following modes are resolved once per submission,
     * widened to the extreme calendar day any inhabited timezone can currently be
     * (UTC-12 for a minimum, UTC+14 for a maximum), so a visitor is never rejected for
     * a value their own picker allowed whatever the server's or the visitor's zone.
     * Fixed bounds stay exact.
     *
     * A node stored before bound modes existed carries no mode but may carry a fixed
     * value — which by then has no applicable property definition anymore, so it is
     * read on the underlying node: validation keeps enforcing it until the startup
     * migration re-homes it under the fixed-bound mixin.
     */
    private static String resolveDateBound(JCRNodeWrapper node, boolean minBound, boolean withTime) {
        String mode = JcrProps.string(node, minBound ? "fmdb:minBoundMode" : "fmdb:maxBoundMode", null);
        String fixedProperty = minBound ? "min" : "max";
        if ("today".equals(mode) || "relative".equals(mode)) {
            return resolveDayFollowingBound(node, minBound, withTime, "relative".equals(mode));
        }

        if ("date".equals(mode)) {
            return JcrProps.dateAsIso(node, fixedProperty, withTime, null);
        }

        if (mode == null) {
            String fixed = JcrProps.dateAsIso(node, fixedProperty, withTime, null);
            return fixed != null ? fixed : JcrProps.rawDateAsIso(node, fixedProperty, withTime, null);
        }

        return null;
    }

    /**
     * The submission day, optionally shifted by a signed amount of days, months or
     * years — 'today' is the zero offset, so the two modes share one resolution.
     * Widened to the extreme calendar day any inhabited timezone can currently be
     * (UTC-12 for a minimum, UTC+14 for a maximum); month and year arithmetic is
     * java.time's (clamps to the end of shorter months), mirrored by the rendered
     * input's island. The validator accepts seconds and millis, so the max covers
     * the whole last minute of the day: T23:59 alone would reject a T23:59:30 value
     * the "until the end of the submission day" contract allows.
     */
    private static String resolveDayFollowingBound(JCRNodeWrapper node, boolean minBound, boolean withTime, boolean relative) {
        String amountProperty = minBound ? "fmdb:minRelativeAmount" : "fmdb:maxRelativeAmount";
        String unitProperty = minBound ? "fmdb:minRelativeUnit" : "fmdb:maxRelativeUnit";
        long amount = relative ? JcrProps.longValue(node, amountProperty, 0) : 0;
        String unit = relative ? JcrProps.string(node, unitProperty, "days") : "days";
        java.time.LocalDate day = shiftDay(
                java.time.LocalDate.now(java.time.ZoneOffset.ofHours(minBound ? -12 : 14)), amount, unit);
        String dayStartOrEnd = minBound ? "T00:00" : "T23:59:59.999";
        return withTime ? day + dayStartOrEnd : day.toString();
    }

    /** The base day shifted by a signed amount of the given unit (java.time clamping). */
    private static java.time.LocalDate shiftDay(java.time.LocalDate base, long amount, String unit) {
        return switch (unit) {
            case "months" -> base.plusMonths(amount);
            case "years" -> base.plusYears(amount);
            default -> base.plusDays(amount);
        };
    }
}
