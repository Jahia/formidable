package org.jahia.modules.formidable.engine.imports.jahiaforms;

import java.util.Map;

/**
 * One option node of a Forms definition, validation rule or action ({@code fcnt:definitionOptions}),
 * named after the setting it holds: {@code placeholder}, {@code helptext}, {@code choices}, {@code message}…
 * A translatable option ({@code fcnt:definitionOptionsTranslatable}) carries its {@code jsonValue} per
 * language, a plain one carries it once.
 *
 * @param name the node name, the setting
 * @param value the plain {@code jsonValue}, or null for a translatable option
 * @param values the {@code jsonValue} per language, empty for a plain option
 */
record FormsOption(String name, String value, Map<String, String> values) {

    static final String TYPE = "fcnt:definitionOptions";
    static final String TRANSLATABLE_TYPE = "fcnt:definitionOptionsTranslatable";
    private static final String JSON_VALUE = "jsonValue";

    static FormsOption from(XmlNode node) {
        return new FormsOption(node.name(), node.attribute(JSON_VALUE), node.i18n(JSON_VALUE));
    }

    static boolean isOption(XmlNode node) {
        return node.isOfType(TYPE) || node.isOfType(TRANSLATABLE_TYPE);
    }

    /** The value in a language, else the plain value, else the value of the first language, else null. */
    String in(String language) {
        if (values.containsKey(language)) {
            return values.get(language);
        }
        if (value != null) {
            return value;
        }
        return values.values().stream().findFirst().orElse(null);
    }

    boolean isBlank() {
        return (value == null || value.isBlank()) && values.values().stream().allMatch(v -> v == null || v.isBlank());
    }
}
