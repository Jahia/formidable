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
        String number = node.attribute(STEP_NUMBER);
        return new FormsStep(node.name(), number == null ? 0 : Long.parseLong(number), node.i18n("jcr:title"), fields);
    }
}
