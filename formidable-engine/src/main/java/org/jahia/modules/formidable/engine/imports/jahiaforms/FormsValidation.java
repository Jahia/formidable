package org.jahia.modules.formidable.engine.imports.jahiaforms;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One validation rule of a Forms field, a child of its {@code validations} node: {@code fcnt:requiredValidation},
 * {@code fcnt:emailValidation}, {@code fcnt:rangeValidation}… with its options, {@code message} among them.
 */
record FormsValidation(String name, String type, Map<String, FormsOption> options) {

    static final String RULES_NODE = "validations";
    static final String REQUIRED = "fcnt:requiredValidation";
    static final String EMAIL = "fcnt:emailValidation";
    static final String RANGE = "fcnt:rangeValidation";
    static final String RANGE_LENGTH = "fcnt:rangeLengthValidation";
    static final String REGEX = "fcnt:regexValidation";
    static final String FILE = "fcnt:fileValidation";
    static final String FILE_NUMBER = "fcnt:fileNumberValidation";
    private static final String MESSAGE = "message";

    static FormsValidation from(XmlNode node) {
        Map<String, FormsOption> options = new LinkedHashMap<>();
        for (XmlNode child : node.children()) {
            if (FormsOption.isOption(child)) {
                options.put(child.name(), FormsOption.from(child));
            }
        }
        return new FormsValidation(node.name(), node.primaryType(), options);
    }

    boolean is(String ruleType) {
        return ruleType.equals(type);
    }

    FormsOption option(String name) {
        return options.get(name);
    }

    /** The message of the rule per language, empty when the rule has none. */
    Map<String, String> messages() {
        FormsOption message = options.get(MESSAGE);
        return message == null ? Map.of() : message.values();
    }
}
