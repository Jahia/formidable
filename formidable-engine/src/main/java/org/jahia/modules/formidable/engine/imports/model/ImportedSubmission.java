package org.jahia.modules.formidable.engine.imports.model;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * A submission the import writes under the results entry of a form, as {@code SaveToJcrFormAction}
 * would have written it: the values by field name, the files, and the metadata Formidable keeps.
 *
 * @param created the moment of the submission in the source, what {@code jcr:created} and the split folders take
 * @param referer the page the visitor submitted from, or null
 * @param locale the language of the submission, the building language of the form when the source has no better
 * @param values the values by field name, one or several strings each, empty answers left out
 * @param sourceId the identity of the submission in the source system, what a later run skips on
 * @param sourceFormId the key of its form in the source system (see {@link ImportedForm#sourceKey()})
 * @param report what the import could not convert in this submission, one line each
 */
public record ImportedSubmission(Instant created, String referer, String locale, Map<String, List<String>> values,
                                 List<ImportedFile> files, String sourceId, String sourceFormId, List<String> report) {
}
