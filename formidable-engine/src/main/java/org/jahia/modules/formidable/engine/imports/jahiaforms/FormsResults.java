package org.jahia.modules.formidable.engine.imports.jahiaforms;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One results entry of Forms ({@code fcnt:formResults}) under {@code formFactory/results}: the form it
 * belongs to, written as a path, and its label nodes. Its submissions are read apart, one by one.
 *
 * @param parentFormPath the {@code parentForm} reference as the export writes it, {@code #/forms/contact-us}
 */
record FormsResults(String name, String uuid, String parentFormPath, String buildingLang,
                    Map<String, String> titles, Map<String, FormsLabel> labels) {

    static final String TYPE = "fcnt:formResults";
    /** The type of the {@code submissions} folder, whose subtree the structure reading skips. */
    static final String SUBMISSIONS_TYPE = "fcnt:submissions";
    private static final String PARENT_FORM = "parentForm";
    private static final String BUILDING_LANG = "buildingLang";

    static FormsResults from(XmlNode node) {
        Map<String, FormsLabel> labels = new LinkedHashMap<>();
        node.child(FormsLabel.LABELS_NODE).ifPresent(labelsNode ->
                labelsNode.children().forEach(label -> labels.put(label.name(), FormsLabel.from(label))));
        // a reference is the one single value Jahia encodes (DocumentViewExporter, JCRMultipleValueUtils.encode)
        return new FormsResults(node.name(), node.uuid(), Iso9075.decode(node.attribute(PARENT_FORM)), node.attribute(BUILDING_LANG),
                node.i18n("jcr:title"), labels);
    }

    /** The name of the form the entry belongs to, the last segment of {@code parentForm}, or null. */
    String parentFormName() {
        if (parentFormPath == null || parentFormPath.isBlank()) {
            return null;
        }
        return parentFormPath.substring(parentFormPath.lastIndexOf('/') + 1);
    }

    FormsLabel label(String fieldName) {
        return labels.get(fieldName);
    }
}
