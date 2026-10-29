package org.jahia.modules.formidable.formsimport;

import org.jahia.modules.formidable.formsimport.model.ImportNote;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImportReportTest {

    @Test
    void aFieldListsTheDistinctValuesItMetUpToTheCap() {
        ImportReport report = new ImportReport(true);
        ImportReport.FormEntry survey = report.form("survey", Map.of()).field("colour", "fmdb:select", List.of()).field("name", "fmdb:inputText", List.of());
        survey.values("colour", List.of("red", "", "blue")).values("colour", List.of("red", "green"));
        for (int i = 0; i < ImportReport.VALUES_LISTED + 5; i++) {
            survey.values("name", List.of("visitor " + i));
        }
        survey.values("name", List.of("visitor 0"));

        JSONArray fields = report.toJson().getJSONArray("forms").getJSONObject(0).getJSONArray("fields");

        JSONObject colour = fields.getJSONObject(0);
        assertEquals(List.of("red", "blue", "green"), colour.getJSONArray("values").toList());
        assertFalse(colour.getBoolean("moreValues"));
        JSONObject name = fields.getJSONObject(1);
        assertEquals(ImportReport.VALUES_LISTED, name.getJSONArray("values").length());
        assertTrue(name.getBoolean("moreValues"));
    }

    @Test
    void theFiguresOfAFormAddUpAndAMissingFileIsReportedNotCounted() {
        ImportReport report = new ImportReport(true);
        report.importedFormsFolder("/sites/x/contents/imported-forms");
        ImportReport.FormEntry contact = report.form("contact-us", Map.of("en", "Contact Us"))
                .target("contact-us", "/sites/x/contents/imported-forms/contact-us", ImportReport.FormOutcome.CREATED)
                .field("your-name", "fmdb:inputText", List.of())
                .notes(List.of(ImportNote.action("form.redirectPageNotFound", "target", "/home")));
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
        JSONObject note = form.getJSONArray("notes").getJSONObject(0);
        assertEquals("form.redirectPageNotFound", note.getString("code"));
        assertEquals("action", note.getString("level"));
        assertEquals("/home", note.getJSONObject("params").getString("target"));
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

    @Test
    void aNoteOfTheRunIsSaidOnceAboveTheForms() {
        // Verifies the run-level notes: the same note added for several forms is kept once, at the top of the report.
        ImportReport report = new ImportReport(true);
        report.note(ImportNote.action("report.displaysUnread"));
        report.note(ImportNote.action("report.displaysUnread"));

        JSONArray notes = report.toJson().getJSONArray("notes");

        assertEquals(1, notes.length());
        assertEquals("report.displaysUnread", notes.getJSONObject(0).getString("code"));
        assertEquals(0, new ImportReport(true).toJson().getJSONArray("notes").length());
    }
}
