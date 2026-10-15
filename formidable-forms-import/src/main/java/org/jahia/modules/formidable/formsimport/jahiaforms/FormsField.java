package org.jahia.modules.formidable.formsimport.jahiaforms;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * One field definition of a Forms form: a node of one of the {@code fcnt:*Definition} types under a
 * step, with its title per language, its option children, its validation rules, its prefills, and whether
 * it carries conditional logic, which the import reports and does not rebuild.
 *
 * @param name the node name, {@code text-input_0_1}
 * @param uuid the {@code jcr:uuid}, what the label nodes of the results point at as {@code fieldId}
 * @param type the primary type, {@code fcnt:inputDefinition}
 * @param titles {@code jcr:title} per language, often empty strings
 * @param choiceField the {@code choiceField} property: the names of the option nodes that hold the choices
 * @param prefills the prefill nodes of the field, in their order
 */
record FormsField(String name, String uuid, String type, Map<String, String> titles, String choiceField,
                  Map<String, FormsOption> options, List<FormsValidation> validations,
                  List<FormsPrefill> prefills, boolean hasLogic) {

    static final String TYPE_SUFFIX = "Definition";
    static final String TYPE_PREFIX = "fcnt:";
    private static final String TITLE = "jcr:title";
    private static final String CHOICE_FIELD = "choiceField";
    private static final String PREFILLS_NODE = "prefills";
    private static final String LOGICS_NODE = "logics";

    static boolean isDefinition(XmlNode node) {
        String type = node.primaryType();
        return type != null && type.startsWith(TYPE_PREFIX) && type.endsWith(TYPE_SUFFIX);
    }

    static FormsField from(XmlNode node) {
        Map<String, FormsOption> options = new LinkedHashMap<>();
        List<FormsValidation> validations = new ArrayList<>();
        List<FormsPrefill> prefills = new ArrayList<>();
        boolean hasLogic = false;
        for (XmlNode child : node.children()) {
            if (FormsOption.isOption(child)) {
                options.put(child.name(), FormsOption.from(child));
            } else if (FormsValidation.RULES_NODE.equals(child.name())) {
                child.children().forEach(rule -> validations.add(FormsValidation.from(rule)));
            } else if (PREFILLS_NODE.equals(child.name())) {
                child.children().forEach(prefill -> prefills.add(FormsPrefill.from(prefill)));
            } else if (LOGICS_NODE.equals(child.name())) {
                hasLogic = !child.children().isEmpty();
            }
        }
        return new FormsField(node.name(), node.uuid(), node.primaryType(), node.i18n(TITLE),
                node.attribute(CHOICE_FIELD), options, validations, prefills, hasLogic);
    }

    boolean prefilled() {
        return !prefills.isEmpty();
    }

    /** The short type name, {@code input} for {@code fcnt:inputDefinition}. */
    String kind() {
        return type.substring(TYPE_PREFIX.length(), type.length() - TYPE_SUFFIX.length());
    }

    FormsOption option(String optionName) {
        return options.get(optionName);
    }

    Optional<FormsValidation> validation(String ruleType) {
        return validations.stream().filter(v -> v.is(ruleType)).findFirst();
    }

    /** The option nodes that hold the choices, in the order {@code choiceField} names them. */
    List<FormsOption> choiceOptions() {
        if (choiceField == null || choiceField.isBlank()) {
            return List.of();
        }
        List<FormsOption> found = new ArrayList<>();
        for (String optionName : choiceField.split(",")) {
            FormsOption option = options.get(optionName.trim());
            if (option != null) {
                found.add(option);
            }
        }
        return found;
    }
}
