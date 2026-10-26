package org.jahia.modules.formidable.formsimport.jahiaforms;

import org.jahia.modules.formidable.formsimport.FormChoice;
import org.jahia.modules.formidable.formsimport.ImportChoice;
import org.jahia.modules.formidable.formsimport.ImportReport;
import org.jahia.modules.formidable.formsimport.ImportWriter;
import org.jahia.modules.formidable.formsimport.model.ImportNote;
import org.jahia.modules.formidable.formsimport.model.ImportedField;
import org.jahia.modules.formidable.formsimport.model.ImportedFile;
import org.jahia.modules.formidable.formsimport.model.ImportedForm;
import org.jahia.modules.formidable.formsimport.model.ImportedSubmission;
import org.jahia.services.content.JCRNodeWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
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
 * <p>
 * A submission that cannot be written does not stop the import: it is left behind, said in the report, and
 * a later run tries it again; past {@link #MAX_FAILED_SUBMISSIONS} of them, or on any other failure, the
 * import stops with what it wrote saved and its report as far as it went ({@link Stopped}).
 */
public final class FormsImportRun {

    static final int BATCH_SIZE = 100;
    /** The submissions that may fail to be written before the import stops: past them, something is wrong with all. */
    static final int MAX_FAILED_SUBMISSIONS = 100;
    /** The notes of a submission that count a value dropped, and a value kept as stored. */
    private static final Set<String> DROPPED = Set.of("submission.passwordDropped", "submission.duplicateTarget");
    private static final Set<String> NOT_CONVERTED = Set.of("submission.notConverted", "submission.consentUnknown");
    private static final Logger log = LoggerFactory.getLogger(FormsImportRun.class);

    /** A form of the export with what the run knows of it. */
    private record Source(ImportedForm form, FormsForm definition, FormsResults results) {
    }

    /** A submission written and not saved yet, with where it goes, what it was read from, and the figures it counts in. */
    private record Pending(JCRNodeWrapper entry, FormsSubmission source, ImportedSubmission submission, ImportReport.FormEntry report) {
    }

    /** The import stopped before its end: what it wrote is saved, its report says how far it went. */
    public static final class Stopped extends RepositoryException {
        private final transient ImportReport report;

        Stopped(ImportReport report, Exception cause) {
            super(cause.getMessage(), cause);
            this.report = report;
        }

        public ImportReport report() {
            return report;
        }
    }

    private final FormsExportReader reader;
    private final ImportWriter writer;
    private final boolean dryRun;
    private final Map<String, FormChoice> choices;
    private final FormsFormConverter converter;
    private final ImportEnvironment.SitePages sitePages;
    /** By source form: the names the values land under when they go to a form, found or attached to. */
    private final Map<String, Map<String, String>> foundNames = new HashMap<>();
    private final List<Pending> pending = new ArrayList<>();
    private int failedSubmissions;

    /**
     * @param writer the writer on the target site; in a dry run it is only read from
     * @param choices what receives the results of each source form, by its name; the others are left out of an import,
     *                and described by a dry run as if the results alone were asked
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
        this.sitePages = environment.sitePages();
    }

    public ImportReport run() throws IOException, FormsExportException, RepositoryException {
        ImportReport report = new ImportReport(dryRun);
        report.importedFormsFolder(writer.importedFormsPath());
        FormsExport export = reader.readStructure();
        Map<String, Source> sources = sourcesOf(export);
        sitePages.displaysUnread().ifPresent(reason -> report.note(reason == ImportEnvironment.DisplaysUnread.NOT_ON_THIS_INSTANCE
                ? ImportNote.info("report.displaysElsewhere") : ImportNote.action("report.displaysUnread")));
        if (dryRun) {
            runOn(sources, report);
            return report;
        }
        try {
            runOn(sources, report);
        } catch (IOException | FormsExportException | RepositoryException | RuntimeException e) {
            throw new Stopped(report, e);
        }
        return report;
    }

    private void runOn(Map<String, Source> sources, ImportReport report) throws IOException, FormsExportException, RepositoryException {
        Map<String, JCRNodeWrapper> entries = new LinkedHashMap<>();
        for (Source source : sources.values()) {
            entries.put(source.form().name(), prepareForm(source, report));
        }
        importSubmissions(sources, entries, report);
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
        FormChoice choice = choiceOf(form);
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
     * What the administrator chose for the form. A form the choices do not name is left out by the import,
     * which writes only what was asked for; the dry run, which runs before any choice, describes it as if
     * the results alone were asked, so that the review shows its fields, its notes and whether it is there.
     */
    private FormChoice choiceOf(ImportedForm form) {
        return choices.getOrDefault(form.name(), dryRun ? FormChoice.RESULTS_ONLY : FormChoice.SKIP);
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
            default -> {
                // a form left out is never prepared: the run leaves it before this point
            }
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
        try {
            reader.readSubmissions(submission -> {
                Source source = sourceOfResults(sources, submission.formName());
                if (source == null) {
                    log.debug("[FormsImport] Submission {} belongs to no known entry ({}), skipped", submission.uuid(), submission.formName());
                    if (!dryRun) {
                        report.orphan(submission.uuid(), submission.created(), submission.formName());
                    }
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
                if (dryRun) {
                    count(entry, submission, converted);
                    // the dialog matches these against the options of the form the results may attach to
                    converted.values().forEach(entry::values);
                } else {
                    // what the submission lost is listed whether it is written or not; its figures count once it is
                    converted.report().forEach(note -> entry.incident(submission.uuid(), submission.created(), note));
                    missingFiles(entry, submission);
                    write(new Pending(entries.get(source.form().name()), submission, converted, entry));
                }
            });
            if (!dryRun) {
                saveBatch();
            }
        } catch (UncheckedIOException e) {
            throw e.getCause();
        } catch (UncheckedRepositoryException e) {
            throw e.cause;
        }
    }

    /** Writes a submission into the batch, saved once full; a failure leaves the submission behind, said in the report. */
    private void write(Pending submission) {
        pending.add(submission);
        try {
            writer.writeSubmission(submission.entry(), submission.submission(), this::openBinary);
        } catch (RepositoryException | IOException | RuntimeException e) {
            retryAlone(e, submission);
            return;
        }
        if (pending.size() >= BATCH_SIZE) {
            saveBatch();
        }
    }

    private java.io.InputStream openBinary(ImportedFile file) throws IOException {
        return reader.openBinary(new FormsFile(file.fileName(), file.mimeType(), file.sourcePath()));
    }

    /** Saves the batch: its submissions are imported; a failed save writes them again one by one. */
    private void saveBatch() {
        try {
            writer.save();
        } catch (RepositoryException e) {
            retryAlone(e, null);
            return;
        }
        pending.forEach(this::imported);
        pending.clear();
    }

    /**
     * Drops the batch a write or a save failed in, and writes its submissions again one by one, each saved
     * alone: the one that failed, and any other that fails alone, is left behind and reported.
     */
    private void retryAlone(Exception failure, Pending failed) {
        List<Pending> batch = List.copyOf(pending);
        pending.clear();
        discard();
        for (Pending submission : batch) {
            if (submission == failed) {
                failed(submission, failure);
                continue;
            }
            try {
                writer.writeSubmission(submission.entry(), submission.submission(), this::openBinary);
                writer.save();
                imported(submission);
            } catch (RepositoryException | IOException | RuntimeException e) {
                discard();
                failed(submission, e);
            }
        }
    }

    private void discard() {
        try {
            writer.discardSubmissions();
        } catch (RepositoryException e) {
            throw new UncheckedRepositoryException(e);
        }
    }

    private void failed(Pending submission, Exception e) {
        ImportedSubmission written = submission.submission();
        log.warn("[FormsImport] Submission {} could not be written, left behind", written.sourceId(), e);
        String reason = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        submission.report().submissionFailed()
                .incident(written.sourceId(), written.created(), ImportNote.action("submission.writeFailed", "reason", reason));
        if (++failedSubmissions >= MAX_FAILED_SUBMISSIONS) {
            throw new UncheckedRepositoryException(new RepositoryException(
                    MAX_FAILED_SUBMISSIONS + " submissions could not be written: the import stopped"));
        }
    }

    /** A submission saved: it counts in the figures of its form, its values and its files with it. */
    private void imported(Pending submission) {
        submission.report().submissionImported();
        count(submission.report(), submission.source(), submission.submission());
    }

    /** Lists the files of a submission that the zip does not hold. */
    private void missingFiles(ImportReport.FormEntry entry, FormsSubmission submission) {
        for (FormsResultField answer : submission.fields()) {
            for (FormsFile file : answer.files()) {
                if (reader.binarySize(file) < 0) {
                    entry.incident(submission.uuid(), submission.created(), ImportNote.action("submission.fileMissing", "file", file.name()));
                }
            }
        }
    }

    /**
     * Counts the values and the files of a submission, a file the zip does not hold as missing: every submission
     * read in a dry run, the submissions written in an import.
     */
    private void count(ImportReport.FormEntry entry, FormsSubmission submission, ImportedSubmission converted) {
        int dropped = (int) converted.report().stream().filter(note -> DROPPED.contains(note.code())).count();
        int notConverted = (int) converted.report().stream().filter(note -> NOT_CONVERTED.contains(note.code())).count();
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
