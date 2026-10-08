package org.jahia.modules.formidable.engine.imports.jahiaforms;

import java.util.List;

/**
 * One answer of a submission, a {@code fcnt:resultField}: named after the field as it was named when
 * the visitor submitted, pointing at the label node of the field as it is named now.
 *
 * @param labelName the last segment of the {@code label} reference, the current name of the field
 * @param values the {@code result} values, always multiple in Forms, one for a single answer
 */
record FormsResultField(String name, String labelName, List<String> values, boolean optional, List<FormsFile> files) {

    static final String TYPE = "fcnt:resultField";
    private static final String LABEL = "label";
    private static final String RESULT = "result";
    private static final String OPTIONAL = "optional";

    static FormsResultField from(XmlNode node) {
        String labelPath = node.attribute(LABEL);
        String labelName = labelPath == null ? node.name() : labelPath.substring(labelPath.lastIndexOf('/') + 1);
        List<FormsFile> files = node.childrenOfType(FormsFile.TYPE).stream().map(FormsFile::from).toList();
        return new FormsResultField(node.name(), labelName, node.values(RESULT),
                Boolean.parseBoolean(node.attribute(OPTIONAL)), files);
    }

    /** The single value, or the first of several, or null when the answer is empty. */
    String value() {
        return values.isEmpty() ? null : values.get(0);
    }
}
