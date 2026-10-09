package org.jahia.modules.formidable.engine.imports.jahiaforms;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The structure of a Forms export: the forms under {@code formFactory/forms} and the results entries
 * under {@code formFactory/results}, both by node name. The submissions are not here: they are streamed
 * one by one by {@link FormsExportReader#readSubmissions}.
 */
record FormsExport(String rootName, Map<String, FormsForm> forms, Map<String, FormsResults> results) {

    static final String FORMS_NODE = "forms";
    static final String RESULTS_NODE = "results";

    static FormsExport from(XmlNode root) {
        Map<String, FormsForm> forms = new LinkedHashMap<>();
        root.child(FORMS_NODE).ifPresent(node -> node.childrenOfType(FormsForm.TYPE)
                .forEach(form -> forms.put(form.name(), FormsForm.from(form))));
        Map<String, FormsResults> results = new LinkedHashMap<>();
        root.child(RESULTS_NODE).ifPresent(node -> node.childrenOfType(FormsResults.TYPE)
                .forEach(entry -> results.put(entry.name(), FormsResults.from(entry))));
        return new FormsExport(root.name(), forms, results);
    }

    /** The form a results entry points at through {@code parentForm}, or null once Forms deleted it. */
    FormsForm formOf(FormsResults entry) {
        String name = entry.parentFormName();
        return name == null ? null : forms.get(name);
    }

    /** The results entry of a form, or null while the form was never published nor submitted. */
    FormsResults resultsOf(FormsForm form) {
        return results.values().stream()
                .filter(r -> form.name().equals(r.parentFormName()))
                .findFirst()
                .orElse(null);
    }
}
