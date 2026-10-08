package org.jahia.modules.formidable.engine.imports.jahiaforms;

import java.util.Map;

/**
 * One label node of a Forms results entry, {@code results/<form>/labels/<fieldName>}: what Forms knows
 * of a field on the results side. Forms refreshes it at each publication, so its name is the current
 * name of the field, and its {@code fieldId} the field's {@code jcr:uuid}.
 *
 * @param labels the {@code label} per language, often empty strings
 * @param choices the {@code choices} JSON per language, for a choice field
 */
record FormsLabel(String name, String fieldId, Map<String, String> labels, Map<String, String> choices) {

    static final String LABELS_NODE = "labels";
    private static final String FIELD_ID = "fieldId";
    private static final String LABEL = "label";
    private static final String CHOICES = "choices";

    static FormsLabel from(XmlNode node) {
        return new FormsLabel(node.name(), node.attribute(FIELD_ID), node.i18n(LABEL), node.i18n(CHOICES));
    }

    boolean hasChoices() {
        return choices.values().stream().anyMatch(c -> c != null && !c.isBlank());
    }
}
