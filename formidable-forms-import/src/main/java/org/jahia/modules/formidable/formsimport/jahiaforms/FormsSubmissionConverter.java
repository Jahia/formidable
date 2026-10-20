package org.jahia.modules.formidable.formsimport.jahiaforms;

import org.jahia.modules.formidable.formsimport.model.ImportNote;
import org.jahia.modules.formidable.formsimport.model.ImportedField;
import org.jahia.modules.formidable.formsimport.model.ImportedFile;
import org.jahia.modules.formidable.formsimport.model.ImportedForm;
import org.jahia.modules.formidable.formsimport.model.ImportedSubmission;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Turns a Forms submission into the submission the import writes (docs/architecture/forms-import.md,
 * "The submissions"): each answer lands under the system name of the field created for its label node,
 * converted for the kind of that field; an answer whose field the form does not hold keeps its Forms name
 * and its value as stored, unless the Forms definition says the value is one to drop, a password.
 * <p>
 * When the form was found from an earlier run, the names come from the fields that form holds now, by
 * the source identity each one remembers ({@code fmdbmix:importedField}): a field the contributor renamed
 * keeps receiving its values, and a field they deleted leaves its answers under their Forms name.
 */
public final class FormsSubmissionConverter {

    /** The parameter of a submission note that names the field of the answer, by its title. */
    private static final String FIELD = "field";

    private final ImportedForm form;
    private final FormsForm source;
    private final FormsResults results;
    private final Map<String, String> foundNames;

    /**
     * @param form the form the import created or found for the submissions
     * @param source the Forms form the export holds, which tells the kind of an answer whose field was not
     *               recreated, a password, and the texts of an accept-terms box; null once Forms deleted the form
     * @param results the results entry of the export the submissions come from: its label nodes tie an
     *                answer to a field; null when the export has none, which leaves every answer under its name
     */
    public FormsSubmissionConverter(ImportedForm form, FormsForm source, FormsResults results) {
        this(form, source, results, null);
    }

    /**
     * @param foundNames the node name of every field the found form holds, by the source identity and by
     *                   the source name of the field it stands for; null when the run creates the form
     */
    public FormsSubmissionConverter(ImportedForm form, FormsForm source, FormsResults results, Map<String, String> foundNames) {
        this.form = form;
        this.source = source;
        this.results = results;
        this.foundNames = foundNames;
    }

    public ImportedSubmission convert(FormsSubmission submission) {
        Map<String, List<String>> values = new LinkedHashMap<>();
        List<ImportedFile> files = new ArrayList<>();
        List<ImportNote> report = new ArrayList<>();
        for (FormsResultField answer : submission.fields()) {
            FormsLabel label = results == null ? null : results.label(answer.labelName());
            FormsField definition = definitionOf(answer, label);
            if (convertMatrix(answer, definition, values, report)) {
                continue;
            }
            ImportedField field = fieldOf(answer, label);
            String fieldName = foundNames == null ? createdName(field) : foundName(answer, label);
            String name = fieldName == null ? answer.labelName() : fieldName;
            String kind = kindOf(field, definition);
            String title = titleOf(answer, label, field, definition);
            if (fieldName == null && !"password".equals(kind)) {
                report.add(ImportNote.info("submission.keptUnderSourceName", FIELD, title, "name", answer.labelName()));
            }
            convertValue(answer, kind, consentLabels(definition), new Target(name, title), values, report);
            for (FormsFile file : answer.files()) {
                files.add(new ImportedFile(name, file.name(), file.mimeType(), file.path()));
            }
        }
        return new ImportedSubmission(submission.created(), submission.origin(), form.buildingLang(), values, files,
                submission.uuid(), form.sourceKey(), report);
    }

    /** Where the values of an answer land: the name of the field, and its title, which the notes about it give. */
    private record Target(String name, String title) {
    }

    private static void convertValue(FormsResultField answer, String kind, FormsValues.ConsentLabels consent, Target target,
                                     Map<String, List<String>> values, List<ImportNote> report) {
        FormsValues.Converted converted = FormsValues.convert(kind, answer.values(), consent);
        if (converted.note() != null) {
            report.add(ImportNote.action("submission." + converted.note(), FIELD, target.title(), "count", String.valueOf(converted.count())));
        }
        store(target, converted.values(), values, report);
    }

    /** The non-blank values under the name of their field; a second answer sent to one name is said, not kept. */
    private static void store(Target target, List<String> answers, Map<String, List<String>> values, List<ImportNote> report) {
        List<String> kept = answers.stream().filter(v -> v != null && !v.isBlank()).toList();
        if (kept.isEmpty()) {
            return;
        }
        if (values.containsKey(target.name())) {
            // two answers of one submission sent to one name by a mapping: the first stays, the second is said
            report.add(ImportNote.action("submission.duplicateTarget", FIELD, target.title()));
            return;
        }
        values.put(target.name(), kept);
    }

    /**
     * The answers of a matrix, one row at a time, under the field created for each row (the fieldset
     * rendered with the Matrix view of {@code FormsFormConverter}): the column keys, one for a radios
     * matrix, several for a checkboxes one. False when the answer is not one of a matrix, when its value is
     * not the JSON of one, or when a row has no field of its own — a form imported before the rows had
     * theirs holds one text field for the whole matrix, which keeps receiving the rows as lines.
     */
    private boolean convertMatrix(FormsResultField answer, FormsField definition, Map<String, List<String>> values, List<ImportNote> report) {
        if (definition == null || !FormsFieldTypes.isMatrix(definition.kind())) {
            return false;
        }
        Optional<Map<String, List<String>>> rows = FormsValues.matrixRows(answer.value());
        if (rows.isEmpty()) {
            return false;
        }
        Map<String, Target> targets = new LinkedHashMap<>();
        for (String rowKey : rows.get().keySet()) {
            Target target = rowTarget(definition, rowKey);
            if (target == null) {
                return false;
            }
            targets.put(rowKey, target);
        }
        rows.get().forEach((rowKey, answers) -> store(targets.get(rowKey), answers, values, report));
        return true;
    }

    /**
     * The field of one row of a matrix: the one converted for it, by the source the row field remembers
     * (the identity of the matrix and the key of the row, else their names); on a found form, the field of
     * that form that remembers the same source. Null when neither knows the row.
     */
    private Target rowTarget(FormsField definition, String rowKey) {
        String byId = FormsFieldTypes.matrixRow(definition.uuid(), rowKey);
        String byName = FormsFieldTypes.matrixRow(definition.name(), rowKey);
        ImportedField row = form.fieldBySourceId(byId);
        if (row == null) {
            row = form.fieldBySourceName(byName);
        }
        String title = row == null ? rowKey : row.titles().getOrDefault(form.buildingLang(), row.name());
        if (foundNames == null) {
            return row == null ? null : new Target(row.name(), title);
        }
        String name = byId == null ? null : foundNames.get(byId);
        if (name == null) {
            name = foundNames.get(byName);
        }
        return name == null ? null : new Target(name, title);
    }

    /**
     * The title of the field of an answer, in the building language of the form, which the notes give: the
     * title of the field converted for it, else the title of its Forms definition, else the label Forms kept
     * with the results, else its Forms name.
     */
    private String titleOf(FormsResultField answer, FormsLabel label, ImportedField field, FormsField definition) {
        String lang = form.buildingLang();
        List<Map<String, String>> sources = new ArrayList<>();
        if (field != null) {
            sources.add(field.titles());
        }
        if (definition != null) {
            sources.add(definition.titles());
        }
        if (label != null) {
            sources.add(label.labels());
        }
        for (Map<String, String> titles : sources) {
            String title = titles.get(lang);
            if (title != null && !title.isBlank()) {
                return title;
            }
        }
        return sources.stream().flatMap(titles -> titles.values().stream())
                .filter(title -> title != null && !title.isBlank()).findFirst().orElse(answer.name());
    }

    /** On a form the run creates: the system name of the field converted for the answer; null when none was. */
    private static String createdName(ImportedField field) {
        return field == null ? null : field.name();
    }

    /** On a form found: the field that remembers the answer's source, by identity, else by name; null when none does. */
    private String foundName(FormsResultField answer, FormsLabel label) {
        String byId = label == null || label.fieldId() == null ? null : foundNames.get(label.fieldId());
        if (byId != null) {
            return byId;
        }
        String byLabelName = foundNames.get(answer.labelName());
        return byLabelName != null ? byLabelName : foundNames.get(answer.name());
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
