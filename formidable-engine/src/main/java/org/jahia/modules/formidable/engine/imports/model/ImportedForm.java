package org.jahia.modules.formidable.engine.imports.model;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * A form the import creates in the {@code imported-forms} folder of the site, with the entry of results
 * that the writer attaches to it. Source-agnostic: the source system and the keys of the form in it are
 * what {@code fmdbmix:importedForm} records (docs/architecture/forms-import.md, "Content model").
 *
 * @param name the node name wanted, the name of the form in the source; the writer takes the next free one
 * @param buttonLabels the labels of the buttons the source form carried, by Formidable property
 *                     ({@code submitBtnLabel}, {@code nextBtnLabel}, {@code previousBtnLabel}) then by language
 * @param captcha whether the source form displayed a captcha
 * @param elements the fields, steps and fieldsets under {@code fields}, in order
 * @param sourceId the identity of the form in the source system, or null once the source deleted it
 * @param sourceResultsId the identity of its results entry in the source system, or null when it had none
 * @param report what the import could not carry over for the form itself, one line each
 */
public record ImportedForm(String name, Map<String, String> titles, String buildingLang,
                           Map<String, String> submissionMessage, Map<String, Map<String, String>> buttonLabels,
                           boolean captcha,
                           List<ImportedElement> elements, List<ImportedAction> actions,
                           String sourceSystem, String sourceId, String sourceResultsId, String sourcePath,
                           List<String> report) {

    /** Every field of the form, at any depth, in order. */
    public Stream<ImportedField> fields() {
        return elements.stream().flatMap(element -> element instanceof ImportedField field
                ? Stream.of(field)
                : ((ImportedContainer) element).fields());
    }

    /** The field that stands for a field of the source, by its source identity, or null. */
    public ImportedField fieldBySourceId(String id) {
        return id == null ? null : fields().filter(f -> id.equals(f.sourceId())).findFirst().orElse(null);
    }

    /** The field that stands for a field of the source, by its source node name, or null. */
    public ImportedField fieldBySourceName(String sourceName) {
        return sourceName == null ? null : fields().filter(f -> sourceName.equals(f.sourceName())).findFirst().orElse(null);
    }

    /** The key of the form for the submissions that point at it: its source identity, else its results entry's. */
    public String sourceKey() {
        return sourceId != null ? sourceId : sourceResultsId;
    }
}
