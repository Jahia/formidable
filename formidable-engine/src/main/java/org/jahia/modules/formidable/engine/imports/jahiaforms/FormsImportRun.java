package org.jahia.modules.formidable.engine.imports.jahiaforms;

import org.jahia.modules.formidable.engine.imports.FormChoice;
import org.jahia.modules.formidable.engine.imports.ImportChoice;
import org.jahia.modules.formidable.engine.imports.ImportReport;
import org.jahia.modules.formidable.engine.imports.ImportWriter;
import org.jahia.modules.formidable.engine.imports.model.ImportedField;
import org.jahia.modules.formidable.engine.imports.model.ImportedFile;
import org.jahia.modules.formidable.engine.imports.model.ImportedForm;
import org.jahia.modules.formidable.engine.imports.model.ImportedSubmission;
import org.jahia.services.content.JCRNodeWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * One run over a Forms export, as a dry run or as the import (docs/architecture/forms-import.md,
 * "Running it"): the forms first, then the submissions by batches, with the report of what was created,
 * found, imported, skipped or left behind. The dry run reads everything and writes nothing.
 */
public final class FormsImportRun {

    static final int BATCH_SIZE = 100;
    private static final Logger log = LoggerFactory.getLogger(FormsImportRun.class);

    /** A form of the export with what the run knows of it. */
    private record Source(ImportedForm form, FormsForm definition, FormsResults results) {
    }

    private final FormsExportReader reader;
    private final ImportWriter writer;
    private final boolean dryRun;
    private final Map<String, FormChoice> choices;
    private final FormsFormConverter converter;
    /** By source form: the names the values land under when they go to a form, found or attached to. */
    private final Map<String, Map<String, String>> foundNames = new HashMap<>();

    /**
     * @param writer the writer on the target site; in a dry run it is only read from
     * @param choices what receives the results of each source form, by its name; the results alone for the others
     * @param registeredTypes whether the repository registers a node type
     * @param declaredOptionsSources whether the instance declares an options source by key
     * @param captchaConfigured whether the instance configures a captcha, for the forms that displayed one
     */
    public FormsImportRun(FormsExportReader reader, ImportWriter writer, boolean dryRun, Map<String, FormChoice> choices,
                          Predicate<String> registeredTypes, Predicate<String> declaredOptionsSources, boolean captchaConfigured) {
        this(reader, writer, dryRun, choices, ImportEnvironment.of(registeredTypes, declaredOptionsSources, captchaConfigured));
    }

    /** @param environment what the instance offers, which decides what the converter carries over */
    public FormsImportRun(FormsExportReader reader, ImportWriter writer, boolean dryRun, Map<String, FormChoice> choices,
                          ImportEnvironment environment) {
        this.reader = reader;
        this.writer = writer;
        this.dryRun = dryRun;
        this.choices = choices;
        this.converter = new FormsFormConverter(environment);
    }

    public ImportReport run() throws IOException, FormsExportException, RepositoryException {
        ImportReport report = new ImportReport(dryRun);
        report.importedFormsFolder(writer.importedFormsPath());
        FormsExport export = reader.readStructure();
        Map<String, Source> sources = sourcesOf(export);
        Map<String, JCRNodeWrapper> entries = new LinkedHashMap<>();
        for (Source source : sources.values()) {
            entries.put(source.form().name(), prepareForm(source, report));
        }
        importSubmissions(sources, entries, report);
        if (!dryRun) {
            writer.save();
        }
        return report;
    }

    /** Every form of the export, with its results entry; a results entry whose form is gone stands for its form. */
    private Map<String, Source> sourcesOf(FormsExport export) {
        Map<String, Source> sources = new LinkedHashMap<>();
        for (FormsForm definition : export.forms().values()) {
            FormsResults results = export.resultsOf(definition);
            sources.put(definition.name(), new Source(converter.convert(definition, results), definition, results));
        }
        for (FormsResults results : export.results().values()) {
            if (export.formOf(results) == null) {
                sources.put(results.name(), new Source(converter.convertFromLabels(results), null, results));
            }
        }
        return sources;
    }

    /**
     * Reports what receives the results of the form, and in an import writes it; null when nothing is
     * written. A form the import created, found from an earlier run, is reported with the fields it holds
     * now, which are the ones the submissions of this run land under; an entry found from an earlier run,
     * written alone or attached to a form, names the values as that run did; an entry written alone takes
     * the names generated from the export, an attachment the names of the form through its mapping.
     */
    private JCRNodeWrapper prepareForm(Source source, ImportReport report) throws RepositoryException {
        ImportedForm form = source.form();
        ImportReport.FormEntry entry = report.form(form.name(), form.titles());
        FormChoice choice = choices.getOrDefault(form.name(), FormChoice.RESULTS_ONLY);
        if (choice.choice() == ImportChoice.SKIP) {
            // left out: nothing written, no marker, a later run of the same export proposes the form again
            entry.skip();
            return null;
        }
        JCRNodeWrapper existingForm = writer.findForm(form.sourceId(), form.sourceResultsId());
        JCRNodeWrapper existingEntry = existingForm == null ? writer.findImportedEntry(form.sourceKey()) : null;
        JCRNodeWrapper targetForm = null;
        if (existingForm != null) {
            entry.target(existingForm.getName(), existingForm.getPath(), ImportReport.FormOutcome.FOUND);
            List<ImportWriter.FoundField> fields = writer.importedFields(existingForm);
            fields.forEach(field -> entry.field(field.name(), field.nodeType(), List.of()));
            foundNames.put(form.name(), namesOf(fields));
        } else if (existingEntry != null) {
            JCRNodeWrapper attachedForm = writer.formOf(existingEntry);
            JCRNodeWrapper target = attachedForm != null ? attachedForm : existingEntry;
            entry.target(target.getName(), target.getPath(), ImportReport.FormOutcome.FOUND);
            Map<String, String> mapping = writer.mappingOf(existingEntry, form.sourceKey());
            form.fields().forEach(field -> entry.field(mapping.getOrDefault(field.name(), field.name()), field.nodeType(), List.of()));
            foundNames.put(form.name(), namesOf(form, mapping));
        } else {
            // nothing of the form exists yet: the report tells what a created form could not carry over, which
            // the dialog shows while the administrator can still choose what receives the results
            entry.notes(form.report());
            form.fields().forEach(field -> entry.field(field.name(), field.nodeType(), field.titles(), field.report()));
            targetForm = prepareNew(form, choice, entry);
        }
        if (dryRun) {
            return null;
        }
        JCRNodeWrapper results;
        if (existingForm != null) {
            results = writer.findOrCreateResultsEntry(existingForm, form);
        } else if (existingEntry != null) {
            results = existingEntry;
        } else if (choice.choice() == ImportChoice.CREATE) {
            JCRNodeWrapper formNode = writer.findOrCreateForm(form).node();
            entry.target(formNode.getName(), formNode.getPath(), ImportReport.FormOutcome.CREATED);
            // the entry points at the form: the form is persisted first, whatever the entry's own save does
            writer.saveForms();
            results = writer.findOrCreateResultsEntry(formNode, form);
        } else if (choice.choice() == ImportChoice.ATTACH) {
            results = writer.findOrCreateResultsEntry(targetForm, form);
            writer.recordMapping(results, form.sourceKey(), choice.mapping());
        } else {
            results = writer.createResultsOnlyEntry(form);
            entry.target(results.getName(), results.getPath(), ImportReport.FormOutcome.RESULTS_ONLY);
        }
        writer.save();
        return results;
    }

    /**
     * Reports what the choice of the administrator writes for a form nothing exists for yet, and names the
     * values of an attachment after the fields of the form it attaches to; the form to attach to, or null.
     */
    private JCRNodeWrapper prepareNew(ImportedForm form, FormChoice choice, ImportReport.FormEntry entry) throws RepositoryException {
        switch (choice.choice()) {
            case CREATE -> entry.target(form.name(), writer.importedFormsPath() + "/" + form.name(), ImportReport.FormOutcome.CREATED);
            case ATTACH -> {
                JCRNodeWrapper targetForm = writer.findSiteForm(choice.formId());
                if (targetForm == null) {
                    throw new RepositoryException("The form to attach " + form.name() + " to does not exist any more in the site");
                }
                entry.target(targetForm.getName(), targetForm.getPath(), ImportReport.FormOutcome.ATTACHED);
                foundNames.put(form.name(), namesOf(form, choice.mapping()));
                return targetForm;
            }
            case RESULTS_ONLY -> entry.target(form.name(), writer.resultsRootPath() + "/" + form.name(), ImportReport.FormOutcome.RESULTS_ONLY);
        }
        return null;
    }

    /** The node name of each found field, by the identity and by the name of the source field it stands for. */
    private static Map<String, String> namesOf(List<ImportWriter.FoundField> fields) {
        Map<String, String> names = new HashMap<>();
        for (ImportWriter.FoundField field : fields) {
            if (field.sourceId() != null) {
                names.putIfAbsent(field.sourceId(), field.name());
            }
            if (field.sourceName() != null) {
                names.putIfAbsent(field.sourceName(), field.name());
            }
        }
        return names;
    }

    /**
     * The name the values of each field of the export land under once mapped onto a form, by the identity
     * and by the name of the field: the field of the form the mapping sends it to, else its own name.
     */
    private static Map<String, String> namesOf(ImportedForm form, Map<String, String> mapping) {
        Map<String, String> names = new HashMap<>();
        form.fields().forEach(field -> {
            String name = mapping.getOrDefault(field.name(), field.name());
            if (field.sourceId() != null) {
                names.putIfAbsent(field.sourceId(), name);
            }
            names.putIfAbsent(field.sourceName(), name);
        });
        return names;
    }

    private void importSubmissions(Map<String, Source> sources, Map<String, JCRNodeWrapper> entries, ImportReport report)
            throws IOException, FormsExportException, RepositoryException {
        Set<String> imported = writer.importedSubmissionIds();
        Map<String, FormsSubmissionConverter> converters = new LinkedHashMap<>();
        sources.forEach((name, source) -> converters.put(resultsName(source), new FormsSubmissionConverter(
                source.form(), source.definition(), source.results(), foundNames.get(name))));
        int[] pending = {0};
        try {
            reader.readSubmissions(submission -> {
                Source source = sourceOfResults(sources, submission.formName());
                if (source == null) {
                    log.debug("[FormsImport] Submission {} belongs to no known entry ({}), skipped", submission.uuid(), submission.formName());
                    return;
                }
                ImportReport.FormEntry entry = report.form(source.form().name()).submissionFound();
                if (entry.isSkipped()) {
                    return;
                }
                if (imported.contains(submission.uuid())) {
                    entry.submissionAlreadyImported();
                    return;
                }
                ImportedSubmission converted = converters.get(submission.formName()).convert(submission);
                count(entry, submission, converted);
                if (dryRun) {
                    // the dialog matches these against the options of the form the results may attach to
                    converted.values().forEach(entry::values);
                } else {
                    write(entries.get(source.form().name()), converted, entry);
                    if (++pending[0] >= BATCH_SIZE) {
                        saveBatch();
                        pending[0] = 0;
                    }
                }
            });
        } catch (UncheckedIOException e) {
            throw e.getCause();
        } catch (UncheckedRepositoryException e) {
            throw e.cause;
        }
    }

    private void write(JCRNodeWrapper entry, ImportedSubmission submission, ImportReport.FormEntry report) {
        try {
            writer.writeSubmission(entry, submission, this::openBinary);
            report.submissionImported();
        } catch (RepositoryException e) {
            throw new UncheckedRepositoryException(e);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private java.io.InputStream openBinary(ImportedFile file) throws IOException {
        return reader.openBinary(new FormsFile(file.fileName(), file.mimeType(), file.sourcePath()));
    }

    private void saveBatch() {
        try {
            writer.save();
        } catch (RepositoryException e) {
            throw new UncheckedRepositoryException(e);
        }
    }

    /** Counts the values and the files of a submission; a file the zip does not hold is reported, not counted. */
    private void count(ImportReport.FormEntry entry, FormsSubmission submission, ImportedSubmission converted) {
        int dropped = (int) converted.report().stream().filter(line -> line.contains("dropped")).count();
        int notConverted = (int) converted.report().stream().filter(line -> line.contains("could not be converted")).count();
        int converted0 = converted.values().values().stream().mapToInt(java.util.List::size).sum();
        entry.values(converted0, dropped, notConverted);
        for (FormsResultField answer : submission.fields()) {
            for (FormsFile file : answer.files()) {
                long size = reader.binarySize(file);
                if (size < 0) {
                    entry.fileMissing();
                } else {
                    entry.file(size);
                }
            }
        }
    }

    private static String resultsName(Source source) {
        return source.results() != null ? source.results().name() : source.form().name();
    }

    private static Source sourceOfResults(Map<String, Source> sources, String resultsName) {
        return sources.values().stream().filter(s -> resultsName.equals(resultsName(s))).findFirst().orElse(null);
    }

    /** Carries a RepositoryException out of the submissions callback. */
    private static final class UncheckedRepositoryException extends RuntimeException {
        private final transient RepositoryException cause;

        UncheckedRepositoryException(RepositoryException cause) {
            super(cause);
            this.cause = cause;
        }
    }

    /** The fields the report lists for a form, for the tests. */
    static java.util.List<String> fieldNames(ImportedForm form) {
        return form.fields().map(ImportedField::name).toList();
    }
}
