package org.jahia.modules.formidable.engine.imports.jahiaforms;

import org.jahia.modules.formidable.engine.imports.model.ImportedField;
import org.jahia.modules.formidable.engine.imports.model.ImportedFile;
import org.jahia.modules.formidable.engine.imports.model.ImportedForm;
import org.jahia.modules.formidable.engine.imports.model.ImportedSubmission;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns a Forms submission into the submission the import writes (docs/architecture/forms-import.md,
 * "The submissions"): each answer lands under the system name of the field created for its label node,
 * converted for the kind of that field; an answer whose field the form does not hold keeps its Forms name
 * and its value as stored.
 */
public final class FormsSubmissionConverter {

    private final ImportedForm form;
    private final FormsResults results;

    /**
     * @param form the form the import created or found for the submissions
     * @param results the results entry of the export the submissions come from: its label nodes tie an
     *                answer to a field; null when the export has none, which leaves every answer under its name
     */
    public FormsSubmissionConverter(ImportedForm form, FormsResults results) {
        this.form = form;
        this.results = results;
    }

    public ImportedSubmission convert(FormsSubmission submission) {
        Map<String, List<String>> values = new LinkedHashMap<>();
        List<ImportedFile> files = new ArrayList<>();
        List<String> report = new ArrayList<>();
        for (FormsResultField answer : submission.fields()) {
            ImportedField field = fieldOf(answer);
            String name = field == null ? answer.labelName() : field.name();
            if (field == null) {
                report.add("answer " + answer.name() + " kept under its Forms name: the form holds no field for it");
            }
            convertValue(answer, field, name, values, report);
            for (FormsFile file : answer.files()) {
                files.add(new ImportedFile(name, file.name(), file.mimeType(), file.path()));
            }
        }
        return new ImportedSubmission(submission.created(), submission.origin(), form.buildingLang(), values, files,
                submission.uuid(), form.sourceKey(), report);
    }

    private static void convertValue(FormsResultField answer, ImportedField field, String name,
                                     Map<String, List<String>> values, List<String> report) {
        String kind = field == null || field.sourceType() == null ? null : kindOf(field.sourceType());
        boolean consent = field != null && FormsFieldTypes.CONSENT.equals(field.nodeType());
        FormsValues.Converted converted = FormsValues.convert(kind, answer.values(), consent);
        if (converted.note() != null) {
            report.add("answer " + answer.name() + ": " + converted.note());
        }
        List<String> kept = converted.values().stream().filter(v -> v != null && !v.isBlank()).toList();
        if (!kept.isEmpty()) {
            values.put(name, kept);
        }
    }

    /** The field for an answer: by the fieldId of its label node, else by the Forms name, else none. */
    private ImportedField fieldOf(FormsResultField answer) {
        FormsLabel label = results == null ? null : results.label(answer.labelName());
        ImportedField byId = label == null ? null : form.fieldBySourceId(label.fieldId());
        if (byId != null) {
            return byId;
        }
        ImportedField byLabelName = form.fieldBySourceName(answer.labelName());
        return byLabelName != null ? byLabelName : form.fieldBySourceName(answer.name());
    }

    private static String kindOf(String formsType) {
        if (!formsType.startsWith(FormsField.TYPE_PREFIX) || !formsType.endsWith(FormsField.TYPE_SUFFIX)) {
            return null;
        }
        return formsType.substring(FormsField.TYPE_PREFIX.length(), formsType.length() - FormsField.TYPE_SUFFIX.length());
    }
}
