package org.jahia.modules.formidable.engine.imports.jahiaforms;

import java.util.List;
import java.util.Map;

/**
 * One step of a Forms form ({@code fcnt:step}), with its fields in the order of the export.
 */
record FormsStep(String name, long number, Map<String, String> titles, List<FormsField> fields) {

    static final String TYPE = "fcnt:step";
    private static final String STEP_NUMBER = "stepNumber";

    static FormsStep from(XmlNode node) {
        List<FormsField> fields = node.children().stream()
                .filter(FormsField::isDefinition)
                .map(FormsField::from)
                .toList();
        return new FormsStep(node.name(), numberOf(node.attribute(STEP_NUMBER)), node.i18n("jcr:title"), fields);
    }

    /** The step number, 0 when absent or not a number: the order of the export then stands. */
    private static long numberOf(String stepNumber) {
        if (stepNumber == null || stepNumber.isBlank()) {
            return 0;
        }
        try {
            return Long.parseLong(stepNumber.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
