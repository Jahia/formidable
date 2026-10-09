package org.jahia.modules.formidable.engine.imports.jahiaforms;

import org.jahia.modules.formidable.engine.imports.model.ImportedField;
import org.jahia.modules.formidable.engine.imports.model.ImportedFile;
import org.jahia.modules.formidable.engine.imports.model.ImportedForm;
import org.jahia.modules.formidable.engine.imports.model.ImportedSubmission;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
     *               recreated, a password, and the texts of an accept-terms box; null once Forms deleted the form
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
            FormsField definition = definitionOf(answer, label);
            String name = field == null ? answer.labelName() : field.name();
            String kind = kindOf(field, definition);
            if (field == null && !"password".equals(kind)) {
                report.add("answer " + answer.name() + " kept under its Forms name: the form holds no field for it");
            }
            convertValue(answer, kind, consentLabels(definition), name, values, report);
            for (FormsFile file : answer.files()) {
                files.add(new ImportedFile(name, file.name(), file.mimeType(), file.path()));
            }
        }
        return new ImportedSubmission(submission.created(), submission.origin(), form.buildingLang(), values, files,
                submission.uuid(), form.sourceKey(), report);
    }

    private static void convertValue(FormsResultField answer, String kind, FormsValues.ConsentLabels consent, String name,
                                     Map<String, List<String>> values, List<String> report) {
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
    private ImportedField fieldOf(FormsResultField answer, FormsLabel label) {
        ImportedField byId = label == null ? null : form.fieldBySourceId(label.fieldId());
        if (byId != null) {
            return byId;
        }
        ImportedField byLabelName = form.fieldBySourceName(answer.labelName());
        return byLabelName != null ? byLabelName : form.fieldBySourceName(answer.name());
    }

    /** The Forms definition of an answer, by the fieldId of its label node, else by name; null when the export lost it. */
    private FormsField definitionOf(FormsResultField answer, FormsLabel label) {
        if (source == null) {
            return null;
        }
        FormsField byId = label == null ? null : source.fieldById(label.fieldId());
        if (byId != null) {
            return byId;
        }
        return source.fields().stream()
                .filter(f -> f.name().equals(answer.labelName()) || f.name().equals(answer.name()))
                .findFirst()
                .orElse(null);
    }

    /**
     * The Forms kind of an answer: from the field created for it, else from the definition of the source
     * form, which still knows a field that was not recreated, a password; null when nothing knows it.
     */
    private static String kindOf(ImportedField field, FormsField definition) {
        if (field != null && field.sourceType() != null) {
            return kindOf(field.sourceType());
        }
        return definition == null ? null : definition.kind();
    }

    /** The texts an accept-terms box submits, from its {@code yes} and {@code no} options; null without the definition. */
    private static FormsValues.ConsentLabels consentLabels(FormsField definition) {
        if (definition == null) {
            return null;
        }
        return new FormsValues.ConsentLabels(texts(definition.option(FormsOptionNames.YES)), texts(definition.option(FormsOptionNames.NO)));
    }

    /** Every text of an option, in every language, trimmed and non-blank. */
    private static Set<String> texts(FormsOption option) {
        Set<String> texts = new LinkedHashSet<>();
        if (option == null) {
            return texts;
        }
        if (option.value() != null && !option.value().isBlank()) {
            texts.add(option.value().trim());
        }
        option.values().values().stream()
                .filter(v -> v != null && !v.isBlank())
                .forEach(v -> texts.add(v.trim()));
        return texts;
    }

    private static String kindOf(String formsType) {
        if (!formsType.startsWith(FormsField.TYPE_PREFIX) || !formsType.endsWith(FormsField.TYPE_SUFFIX)) {
            return null;
        }
        return formsType.substring(FormsField.TYPE_PREFIX.length(), formsType.length() - FormsField.TYPE_SUFFIX.length());
    }
}
