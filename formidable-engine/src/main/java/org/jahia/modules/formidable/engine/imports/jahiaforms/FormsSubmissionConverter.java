package org.jahia.modules.formidable.engine.imports.jahiaforms;

import org.jahia.modules.formidable.engine.imports.model.ImportedField;
import org.jahia.modules.formidable.engine.imports.model.ImportedFile;
import org.jahia.modules.formidable.engine.imports.model.ImportedForm;
import org.jahia.modules.formidable.engine.imports.model.ImportedSubmission;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns a Forms submission into the submission the import writes (docs/architecture/forms-import.md,
 * "The submissions"): each answer lands under the system name of the field created for its label node,
 * converted for the kind of that field; an answer whose field the form does not hold keeps its Forms name
 * and its value as stored, unless the Forms definition says the value is one to drop, a password.
 */
public final class FormsSubmissionConverter {

    private final ImportedForm form;
    private final FormsForm source;
    private final FormsResults results;

    /**
     * @param form the form the import created or found for the submissions
     * @param source the Forms form the export holds, which tells the kind of an answer whose field was not
     *               recreated; null once Forms deleted the form
     * @param results the results entry of the export the submissions come from: its label nodes tie an
     *                answer to a field; null when the export has none, which leaves every answer under its name
     */
    public FormsSubmissionConverter(ImportedForm form, FormsForm source, FormsResults results) {
        this.form = form;
        this.source = source;
        this.results = results;
    }

    public ImportedSubmission convert(FormsSubmission submission) {
        Map<String, List<String>> values = new LinkedHashMap<>();
        List<ImportedFile> files = new ArrayList<>();
        List<String> report = new ArrayList<>();
        for (FormsResultField answer : submission.fields()) {
            FormsLabel label = results == null ? null : results.label(answer.labelName());
            ImportedField field = fieldOf(answer, label);
            String name = field == null ? answer.labelName() : field.name();
            String kind = kindOf(answer, label, field);
            if (field == null && !"password".equals(kind)) {
                report.add("answer " + answer.name() + " kept under its Forms name: the form holds no field for it");
            }
            convertValue(answer, kind, storesAccepted(field), name, values, report);
            for (FormsFile file : answer.files()) {
                files.add(new ImportedFile(name, file.name(), file.mimeType(), file.path()));
            }
        }
        return new ImportedSubmission(submission.created(), submission.origin(), form.buildingLang(), values, files,
                submission.uuid(), form.sourceKey(), report);
    }

    private static void convertValue(FormsResultField answer, String kind, boolean accepted, String name,
                                     Map<String, List<String>> values, List<String> report) {
        FormsValues.Converted converted = FormsValues.convert(kind, answer.values(), accepted);
        if (converted.note() != null) {
            report.add("answer " + answer.name() + ": " + converted.note());
        }
        List<String> kept = converted.values().stream().filter(v -> v != null && !v.isBlank()).toList();
        if (!kept.isEmpty()) {
            values.put(name, kept);
        }
    }

    /** The field for an answer: by the fieldId of its label node, else by the Forms name, else none. */
    private ImportedField fieldOf(FormsResultField answer, FormsLabel label) {
        ImportedField byId = label == null ? null : form.fieldBySourceId(label.fieldId());
        if (byId != null) {
            return byId;
        }
        ImportedField byLabelName = form.fieldBySourceName(answer.labelName());
        return byLabelName != null ? byLabelName : form.fieldBySourceName(answer.name());
    }

    /**
     * The Forms kind of an answer: from the field created for it, else from the definition of the source
     * form, which still knows a field that was not recreated, a password; null when nothing knows it.
     */
    private String kindOf(FormsResultField answer, FormsLabel label, ImportedField field) {
        if (field != null && field.sourceType() != null) {
            return kindOf(field.sourceType());
        }
        if (source == null) {
            return null;
        }
        FormsField definition = label == null ? null : source.fieldById(label.fieldId());
        if (definition == null) {
            definition = source.fields().stream()
                    .filter(f -> f.name().equals(answer.labelName()) || f.name().equals(answer.name()))
                    .findFirst()
                    .orElse(null);
        }
        return definition == null ? null : definition.kind();
    }

    /**
     * Whether a ticked accept-terms box is to be stored as {@code true}: for a consent, and for the
     * checkbox fallback whose one option is {@code true} because Forms gave no choices to take the value from.
     */
    private static boolean storesAccepted(ImportedField field) {
        if (field == null) {
            return false;
        }
        if (FormsFieldTypes.CONSENT.equals(field.nodeType())) {
            return true;
        }
        return FormsFieldTypes.CHECKBOX.equals(field.nodeType()) && field.options().values().stream()
                .allMatch(options -> options.size() == 1 && FormsFormConverter.ACCEPTED.equals(new JSONObject(options.get(0)).optString("value")));
    }

    private static String kindOf(String formsType) {
        if (!formsType.startsWith(FormsField.TYPE_PREFIX) || !formsType.endsWith(FormsField.TYPE_SUFFIX)) {
            return null;
        }
        return formsType.substring(FormsField.TYPE_PREFIX.length(), formsType.length() - FormsField.TYPE_SUFFIX.length());
    }
}
