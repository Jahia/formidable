package org.jahia.modules.formidable.engine.imports;

import org.jahia.services.content.JCRNodeWrapper;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.jcr.RepositoryException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Refuses the choices of a review that the import could not honour (docs/architecture/forms-import.md,
 * "The mapping"): an attachment to a form that is not a form of the site, a mapping that names a field the
 * dry run did not list for the source, or that sends a field to the name of another field of the source,
 * kept or mapped away: a submission holding both would lose one of its values, and a rename cannot chain on
 * another in one pass, so a chain of renames is two attachments.
 */
final class ChoicesCheck {

    /** The form of the site an identifier names; null when it names none. */
    @FunctionalInterface
    interface FormLookup {
        JCRNodeWrapper find(String identifier) throws RepositoryException;
    }

    private ChoicesCheck() {
    }

    /**
     * @param reportJson the report of the dry run, whose fields are the names a mapping may rename
     */
    static void check(Map<String, FormChoice> choices, String reportJson, FormLookup forms)
            throws RepositoryException, ImportJobs.RefusedException {
        JSONObject report = reportJson == null || reportJson.isBlank() ? new JSONObject() : new JSONObject(reportJson);
        for (Map.Entry<String, FormChoice> entry : choices.entrySet()) {
            FormChoice choice = entry.getValue();
            if (choice.choice() != ImportChoice.ATTACH) {
                continue;
            }
            String sourceName = entry.getKey();
            if (forms.find(choice.formId()) == null) {
                throw new ImportJobs.RefusedException("the form to attach " + sourceName + " to is not a form of the site");
            }
            checkMapping(sourceName, choice.mapping(), fieldNamesOf(report, sourceName));
        }
    }

    private static void checkMapping(String sourceName, Map<String, String> mapping, List<String> fields) throws ImportJobs.RefusedException {
        for (Map.Entry<String, String> name : mapping.entrySet()) {
            if (!fields.contains(name.getKey())) {
                throw new ImportJobs.RefusedException(sourceName + " has no field named " + name.getKey());
            }
            String target = name.getValue();
            if (!target.equals(name.getKey()) && fields.contains(target)) {
                throw new ImportJobs.RefusedException("the field " + name.getKey() + " of " + sourceName + " cannot land under "
                        + target + ", the name of another field of the source");
            }
        }
    }

    private static List<String> fieldNamesOf(JSONObject report, String sourceName) {
        List<String> names = new ArrayList<>();
        JSONArray forms = report.optJSONArray("forms");
        if (forms == null) {
            return names;
        }
        for (int i = 0; i < forms.length(); i++) {
            JSONObject form = forms.getJSONObject(i);
            if (sourceName.equals(form.optString("sourceName"))) {
                JSONArray fields = form.optJSONArray("fields");
                for (int j = 0; fields != null && j < fields.length(); j++) {
                    names.add(fields.getJSONObject(j).getString("name"));
                }
            }
        }
        return names;
    }
}
