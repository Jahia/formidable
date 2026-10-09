package org.jahia.modules.formidable.engine.imports;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The report of a dry run or of an import (docs/architecture/forms-import.md, "Running it"): per source
 * form, what the import creates or found, its fields with what could not be carried over, the counts of
 * submissions, values and files. Built as it goes, serialised as JSON for the job node and the dialog.
 */
public final class ImportReport {

    /**
     * What receives the results of a source form: a form created for them, a form or an entry found from an
     * earlier run, or an entry alone, without a form.
     */
    public enum FormOutcome {
        CREATED("created"), FOUND("found"), RESULTS_ONLY("resultsOnly");

        private final String json;

        FormOutcome(String json) {
            this.json = json;
        }
    }

    /** The figures of one source form. */
    public static final class FormEntry {
        private final String sourceName;
        private final Map<String, String> titles;
        private String targetName;
        private String targetPath;
        private FormOutcome outcome;
        private final List<JSONObject> fields = new ArrayList<>();
        private final List<String> notes = new ArrayList<>();
        private int submissionsFound;
        private int submissionsImported;
        private int submissionsAlreadyImported;
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

        public FormEntry field(String name, String nodeType, List<String> report) {
            fields.add(new JSONObject().put("name", name).put("type", nodeType).put("notes", new JSONArray(report)));
            return this;
        }

        public FormEntry notes(List<String> lines) {
            notes.addAll(lines);
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
            return submissionsFound - submissionsAlreadyImported;
        }

        JSONObject toJson() {
            return new JSONObject()
                    .put("sourceName", sourceName)
                    .put("titles", new JSONObject(titles))
                    .put("targetName", targetName)
                    .put("targetPath", targetPath)
                    .put("outcome", outcome == null ? JSONObject.NULL : outcome.json)
                    .put("fields", new JSONArray(fields))
                    .put("notes", new JSONArray(notes))
                    .put("submissions", new JSONObject()
                            .put("found", submissionsFound)
                            .put("imported", submissionsImported)
                            .put("alreadyImported", submissionsAlreadyImported)
                            .put("toImport", submissionsToImport()))
                    .put("values", new JSONObject()
                            .put("converted", valuesConverted)
                            .put("dropped", valuesDropped)
                            .put("notConverted", valuesNotConverted))
                    .put("files", new JSONObject().put("count", files).put("bytes", fileBytes).put("missing", filesMissing));
        }
    }

    private final boolean dryRun;
    private final Map<String, FormEntry> forms = new LinkedHashMap<>();
    private String importedFormsFolder;

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

    public boolean isDryRun() {
        return dryRun;
    }

    /** Whether the import would write anything: a form or an entry to create, or a submission to import. */
    public boolean hasSomethingToImport() {
        return forms.values().stream().anyMatch(f -> f.outcome != FormOutcome.FOUND || f.submissionsToImport() > 0);
    }

    public JSONObject toJson() {
        JSONArray entries = new JSONArray();
        int found = 0;
        int toImport = 0;
        int imported = 0;
        for (FormEntry form : forms.values()) {
            entries.put(form.toJson());
            found += form.submissionsFound;
            toImport += form.submissionsToImport();
            imported += form.submissionsImported;
        }
        return new JSONObject()
                .put("dryRun", dryRun)
                .put("importedFormsFolder", importedFormsFolder == null ? JSONObject.NULL : importedFormsFolder)
                .put("forms", entries)
                .put("totals", new JSONObject()
                        .put("forms", forms.size())
                        .put("submissionsFound", found)
                        .put("submissionsToImport", toImport)
                        .put("submissionsImported", imported))
                .put("nothingToImport", !hasSomethingToImport());
    }

    @Override
    public String toString() {
        return toJson().toString();
    }
}
