package org.jahia.modules.formidable.engine.imports;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImportReportTest {

    @Test
    void theFiguresOfAFormAddUpAndAMissingFileIsReportedNotCounted() {
        ImportReport report = new ImportReport(true);
        report.importedFormsFolder("/sites/x/contents/imported-forms");
        ImportReport.FormEntry contact = report.form("contact-us", Map.of("en", "Contact Us"))
                .target("contact-us", "/sites/x/contents/imported-forms/contact-us", ImportReport.FormOutcome.CREATED)
                .field("your-name", "fmdb:inputText", List.of())
                .notes(List.of("redirect reported"));
        contact.submissionFound().values(3, 1, 0).file(1024).file(2048).fileMissing();
        contact.submissionFound().submissionAlreadyImported();

        JSONObject json = report.toJson();
        JSONObject form = json.getJSONArray("forms").getJSONObject(0);
        assertEquals("created", form.getString("outcome"));
        assertEquals(2, form.getJSONObject("submissions").getInt("found"));
        assertEquals(1, form.getJSONObject("submissions").getInt("alreadyImported"));
        assertEquals(1, form.getJSONObject("submissions").getInt("toImport"));
        assertEquals(3, form.getJSONObject("values").getInt("converted"));
        assertEquals(1, form.getJSONObject("values").getInt("dropped"));
        assertEquals(2, form.getJSONObject("files").getInt("count"));
        assertEquals(3072, form.getJSONObject("files").getLong("bytes"));
        assertEquals(1, form.getJSONObject("files").getInt("missing"));
        assertEquals(List.of("redirect reported"), form.getJSONArray("notes").toList());
        assertEquals(1, json.getJSONObject("totals").getInt("forms"));
        assertEquals(2, json.getJSONObject("totals").getInt("submissionsFound"));
        assertEquals(1, json.getJSONObject("totals").getInt("submissionsToImport"));
        assertTrue(json.getBoolean("dryRun"));
        assertFalse(json.getBoolean("nothingToImport"));
    }

    @Test
    void anEntryWrittenAloneIsSomethingToImportEvenWithoutASubmission() {
        ImportReport report = new ImportReport(true);
        report.form("survey", Map.of()).target("survey", "/sites/x/formidable-results/survey", ImportReport.FormOutcome.RESULTS_ONLY);

        assertTrue(report.hasSomethingToImport());
        assertEquals("resultsOnly", report.toJson().getJSONArray("forms").getJSONObject(0).getString("outcome"));
    }

    @Test
    void aReportWhereEveryFormIsFoundAndEverySubmissionImportedHasNothingToImport() {
        ImportReport report = new ImportReport(true);
        report.form("contact-us", Map.of()).target("contact-us", "/sites/x/contents/custom/contact-us", ImportReport.FormOutcome.FOUND)
                .submissionFound().submissionAlreadyImported();

        assertFalse(report.hasSomethingToImport());
        assertTrue(report.toJson().getBoolean("nothingToImport"));
        assertEquals("found", report.toJson().getJSONArray("forms").getJSONObject(0).getString("outcome"));
    }
}
