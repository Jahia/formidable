package org.jahia.modules.formidable.engine.imports.jahiaforms;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * The label rule of docs/architecture/forms-import.md, "The labels": the title of a recreated field, per
 * language, is the first text found among the title of the definition, the {@code label} of its label
 * node, its placeholder without the trailing {@code *}, and the node name of the field.
 */
final class FormsLabels {

    static final String PLACEHOLDER = "placeholder";

    private FormsLabels() {
    }

    /**
     * @param field the definition, or null once Forms deleted the form
     * @param label the label node of the results, or null while the form was never published
     * @param fallback the node name of the field
     */
    static Map<String, String> titles(FormsField field, FormsLabel label, String fallback) {
        Map<String, String> titles = new LinkedHashMap<>();
        for (String language : languages(field, label)) {
            String title = firstText(
                    field == null ? null : field.titles().get(language),
                    label == null ? null : label.labels().get(language),
                    field == null || field.option(PLACEHOLDER) == null ? null : stripRequiredMark(field.option(PLACEHOLDER).in(language)));
            titles.put(language, title == null ? fallback : title);
        }
        return titles;
    }

    /** "Your First name*" and "Votre courriel *" give "Your First name" and "Votre courriel". */
    static String stripRequiredMark(String placeholder) {
        if (placeholder == null) {
            return null;
        }
        String text = placeholder.trim();
        while (text.endsWith("*")) {
            text = text.substring(0, text.length() - 1).trim();
        }
        return text;
    }

    private static Set<String> languages(FormsField field, FormsLabel label) {
        Set<String> languages = new LinkedHashSet<>();
        if (field != null) {
            languages.addAll(field.titles().keySet());
            if (field.option(PLACEHOLDER) != null) {
                languages.addAll(field.option(PLACEHOLDER).values().keySet());
            }
        }
        if (label != null) {
            languages.addAll(label.labels().keySet());
        }
        return languages;
    }

    private static String firstText(String... candidates) {
        for (String candidate : candidates) {
            if (candidate != null && !candidate.isBlank()) {
                return candidate.trim();
            }
        }
        return null;
    }
}
