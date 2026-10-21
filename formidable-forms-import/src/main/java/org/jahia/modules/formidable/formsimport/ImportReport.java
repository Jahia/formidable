package org.jahia.modules.formidable.formsimport;

import org.jahia.modules.formidable.formsimport.model.ImportNote;
import org.json.JSONArray;
import org.json.JSONObject;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The report of a dry run or of an import (docs/architecture/forms-import.md, "Running it"): per source
 * form, what the import creates or found, its fields with what could not be carried over, the counts of
 * submissions, values and files. Built as it goes, serialised as JSON for the job node and the dialog.
 * <p>
 * An import also lists its incidents, what the report the dialog offers for download gives submission by submission: a value
 * dropped or kept as stored, a file the export does not hold, a submission that could not be written, a
 * submission of no form of the export; and, when it stopped before its end, why.
 */
public final class ImportReport {

    /**
     * What receives the results of a source form: a form created for them, a form or an entry found from an
     * earlier run, an entry alone, without a form, an existing form they attach to, or nothing: the form left
     * out of the run by the administrator.
     */
    public enum FormOutcome {
        CREATED("created"), FOUND("found"), RESULTS_ONLY("resultsOnly"), ATTACHED("attached"), SKIPPED("skipped");

        private final String json;

        FormOutcome(String json) {
            this.json = json;
        }
    }

    /** The distinct values a field is reported with: enough for the dialog to tell a choice field's options from them. */
    public static final int VALUES_LISTED = 30;

    /** The incidents listed per form and for the whole run; the ones past the cap are only counted. */
    public static final int INCIDENTS_LISTED = 1000;

    /** The incidents of a form or of the run, capped at {@link #INCIDENTS_LISTED}. */
    private static final class Incidents {
        private final List<JSONObject> listed = new ArrayList<>();
        private int omitted;

        void add(String submission, Instant created, ImportNote note) {
            if (listed.size() >= INCIDENTS_LISTED) {
                omitted++;
                return;
            }
            listed.add(note.toJson().put("submission", submission)
                    .put("created", created == null ? JSONObject.NULL : created.toString()));
        }

        void into(JSONObject json) {
            json.put("incidents", new JSONArray(listed)).put("incidentsOmitted", omitted);
        }
    }

    /** The figures of one source form. */
    public static final class FormEntry {
        private final String sourceName;
        private final Map<String, String> titles;
        /** The distinct values met per field, capped at {@link #VALUES_LISTED}; the names past the cap are in {@code moreValues}. */
        private final Map<String, Set<String>> values = new LinkedHashMap<>();
        private final Set<String> moreValues = new LinkedHashSet<>();
        private String targetName;
        private String targetPath;
        private FormOutcome outcome;
        private final List<JSONObject> fields = new ArrayList<>();
        private final List<ImportNote> notes = new ArrayList<>();
        private int submissionsFound;
        private int submissionsImported;
        private int submissionsAlreadyImported;
        private int submissionsFailed;
        private final Incidents incidents = new Incidents();
        private int valuesConverted;
        private int valuesDropped;
        private int valuesNotConverted;
        private int files;
        private long fileBytes;
        private int filesMissing;

        FormEntry(String sourceName, Map<String, String> titles) {
            this.sourceName = sourceName;
            this.titles = titles;
        }

        public FormEntry target(String name, String path, FormOutcome formOutcome) {
            targetName = name;
            targetPath = path;
            outcome = formOutcome;
            return this;
        }

        /** The form is left out of the run: nothing is written for it, its submissions are not to import. */
        public FormEntry skip() {
            outcome = FormOutcome.SKIPPED;
            return this;
        }

        public boolean isSkipped() {
            return outcome == FormOutcome.SKIPPED;
        }

        public FormEntry field(String name, String nodeType, List<ImportNote> report) {
            return field(name, nodeType, Map.of(), report);
        }

        /**
         * A field of the form, with its titles by language: what the dialog matches the fields of an
         * existing form against, when the administrator attaches the results to one.
         */
        public FormEntry field(String name, String nodeType, Map<String, String> titles, List<ImportNote> report) {
            fields.add(new JSONObject().put("name", name).put("type", nodeType).put("titles", new JSONObject(titles))
                    .put("notes", notesJson(report)));
            return this;
        }

        public FormEntry notes(List<ImportNote> lines) {
            notes.addAll(lines);
            return this;
        }

        /**
         * The values a submission holds for a field, kept distinct up to the cap: what the dialog matches
         * against the options of a choice field of the form the results attach to.
         */
        public FormEntry values(String field, List<String> submitted) {
            Set<String> distinct = values.computeIfAbsent(field, name -> new LinkedHashSet<>());
            for (String value : submitted) {
                if (value == null || value.isBlank()) {
                    continue;
                }
                if (distinct.size() >= VALUES_LISTED && !distinct.contains(value)) {
                    moreValues.add(field);
                } else {
                    distinct.add(value);
                }
            }
            return this;
        }

        public FormEntry submissionFound() {
            submissionsFound++;
            return this;
        }

        public FormEntry submissionImported() {
            submissionsImported++;
            return this;
        }

        public FormEntry submissionAlreadyImported() {
            submissionsAlreadyImported++;
            return this;
        }

        /** A submission the import could not write: left behind, a later run tries it again. */
        public FormEntry submissionFailed() {
            submissionsFailed++;
            return this;
        }

        /** Something a submission lost or kept as it was, or why it was not written: a line of the downloadable report. */
        public FormEntry incident(String submission, Instant created, ImportNote note) {
            incidents.add(submission, created, note);
            return this;
        }

        public FormEntry values(int converted, int dropped, int notConverted) {
            valuesConverted += converted;
            valuesDropped += dropped;
            valuesNotConverted += notConverted;
            return this;
        }

        public FormEntry file(long bytes) {
            files++;
            fileBytes += Math.max(bytes, 0);
            return this;
        }

        /** A file a submission references that the export does not hold: left behind, and said so. */
        public FormEntry fileMissing() {
            filesMissing++;
            return this;
        }

        public int submissionsToImport() {
            return isSkipped() ? 0 : submissionsFound - submissionsAlreadyImported;
        }

        private static JSONArray notesJson(List<ImportNote> notes) {
            JSONArray array = new JSONArray();
            notes.forEach(note -> array.put(note.toJson()));
            return array;
        }

        JSONObject toJson() {
            JSONArray reported = new JSONArray();
            for (JSONObject field : fields) {
                String name = field.getString("name");
                reported.put(new JSONObject(field.toString())
                        .put("values", new JSONArray(values.getOrDefault(name, Set.of())))
                        .put("moreValues", moreValues.contains(name)));
            }
            JSONObject json = new JSONObject()
                    .put("sourceName", sourceName)
                    .put("titles", new JSONObject(titles))
                    .put("targetName", targetName)
                    .put("targetPath", targetPath)
                    .put("outcome", outcome == null ? JSONObject.NULL : outcome.json)
                    .put("fields", reported)
                    .put("notes", notesJson(notes))
                    .put("submissions", new JSONObject()
                            .put("found", submissionsFound)
                            .put("imported", submissionsImported)
                            .put("alreadyImported", submissionsAlreadyImported)
                            .put("failed", submissionsFailed)
                            .put("toImport", submissionsToImport()))
                    .put("values", new JSONObject()
                            .put("converted", valuesConverted)
                            .put("dropped", valuesDropped)
                            .put("notConverted", valuesNotConverted))
                    .put("files", new JSONObject().put("count", files).put("bytes", fileBytes).put("missing", filesMissing));
            incidents.into(json);
            return json;
        }
    }

    private final boolean dryRun;
    private final Map<String, FormEntry> forms = new LinkedHashMap<>();
    private String importedFormsFolder;
    /** The incidents of no form: the submissions of a results entry the export holds no form nor entry for. */
    private final Incidents incidents = new Incidents();
    private String stopped;

    public ImportReport(boolean dryRun) {
        this.dryRun = dryRun;
    }

    public FormEntry form(String sourceName, Map<String, String> titles) {
        return forms.computeIfAbsent(sourceName, name -> new FormEntry(name, titles));
    }

    public FormEntry form(String sourceName) {
        return forms.get(sourceName);
    }

    public void importedFormsFolder(String path) {
        importedFormsFolder = path;
    }

    /** A submission of no form of the export: not imported, and said so. */
    public void orphan(String submission, Instant created, String formName) {
        incidents.add(submission, created, ImportNote.action("submission.noForm", "form", formName));
    }

    /** The import stopped before its end, for this reason: what it wrote stays, a later run goes on from there. */
    public void stopped(String reason) {
        stopped = reason;
    }

    public boolean isDryRun() {
        return dryRun;
    }

    /** Whether the import would write anything: a form or an entry to create, or a submission to import. */
    public boolean hasSomethingToImport() {
        return forms.values().stream().anyMatch(f -> !f.isSkipped() && (f.outcome != FormOutcome.FOUND || f.submissionsToImport() > 0));
    }

    public JSONObject toJson() {
        JSONArray entries = new JSONArray();
        int found = 0;
        int toImport = 0;
        int imported = 0;
        int failed = 0;
        for (FormEntry form : forms.values()) {
            entries.put(form.toJson());
            found += form.submissionsFound;
            toImport += form.submissionsToImport();
            imported += form.submissionsImported;
            failed += form.submissionsFailed;
        }
        JSONObject json = new JSONObject()
                .put("dryRun", dryRun)
                .put("stopped", stopped == null ? JSONObject.NULL : stopped)
                .put("importedFormsFolder", importedFormsFolder == null ? JSONObject.NULL : importedFormsFolder)
                .put("forms", entries)
                .put("totals", new JSONObject()
                        .put("forms", forms.size())
                        .put("submissionsFound", found)
                        .put("submissionsToImport", toImport)
                        .put("submissionsImported", imported)
                        .put("submissionsFailed", failed))
                .put("nothingToImport", !hasSomethingToImport());
        incidents.into(json);
        return json;
    }

    @Override
    public String toString() {
        return toJson().toString();
    }
}
