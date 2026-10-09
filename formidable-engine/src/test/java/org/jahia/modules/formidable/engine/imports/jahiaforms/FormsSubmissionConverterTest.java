package org.jahia.modules.formidable.engine.imports.jahiaforms;

import org.jahia.modules.formidable.engine.imports.model.ImportedForm;
import org.jahia.modules.formidable.engine.imports.model.ImportedSubmission;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FormsSubmissionConverterTest {

    private static final FormsFormConverter CONVERTER = new FormsFormConverter(type -> true, source -> true);
    private static final FormsFormConverter ELEMENTS_ONLY = new FormsFormConverter(type -> !type.startsWith("fmdbext:"), source -> false);

    @Test
    void anAnswerLandsUnderTheSystemNameOfItsField() throws Exception {
        FormsExport export = FormsExportReaderTest.sampleReader().readStructure();
        FormsForm source = export.forms().get("contact-us");
        FormsResults results = export.resultsOf(source);
        ImportedForm form = CONVERTER.convert(source, results);
        List<FormsSubmission> submissions = new ArrayList<>();
        FormsExportReaderTest.sampleReader().readSubmissions(submissions::add);
        FormsSubmission first = submissions.stream().filter(s -> s.uuid().equals("2c591198-6092-485b-b936-2a8cbf0213c8")).findFirst().orElseThrow();

        ImportedSubmission converted = new FormsSubmissionConverter(form, source, results).convert(first);

        assertEquals(Instant.parse("2023-06-20T13:03:02.614Z"), converted.created());
        assertEquals("https://example.com/sites/motor-retail/home.html", converted.referer());
        assertEquals("en", converted.locale());
        assertEquals("2c591198-6092-485b-b936-2a8cbf0213c8", converted.sourceId());
        assertEquals(source.uuid(), converted.sourceFormId());
        assertEquals(List.of("your-last-name", "your-telephone-number", "your-email-address", "your-first-name", "your-enquiry"),
                new ArrayList<>(converted.values().keySet()));
        assertTrue(converted.values().get("your-email-address").get(0).endsWith("@example.com"));
        assertTrue(converted.files().isEmpty());
        assertTrue(converted.report().isEmpty(), converted.report().toString());
    }

    @Test
    void everySubmissionOfTheSampleConvertsWithoutANote() throws Exception {
        FormsExport export = FormsExportReaderTest.sampleReader().readStructure();
        Map<String, FormsSubmissionConverter> converters = new java.util.HashMap<>();
        for (FormsResults results : export.results().values()) {
            FormsForm source = export.formOf(results);
            converters.put(results.name(), new FormsSubmissionConverter(CONVERTER.convert(source, results), source, results));
        }
        List<ImportedSubmission> converted = new ArrayList<>();
        FormsExportReaderTest.sampleReader().readSubmissions(s -> converted.add(converters.get(s.formName()).convert(s)));

        assertEquals(106, converted.size());
        assertTrue(converted.stream().allMatch(s -> s.report().isEmpty()));
        assertTrue(converted.stream().allMatch(s -> !s.values().isEmpty()));
        assertTrue(converted.stream().noneMatch(s -> s.values().containsKey("text-input_0_1")));
    }

    /** The second run on a form the first created, whose fields the contributor has renamed and deleted since. */
    @Test
    void onAFoundFormAnAnswerLandsUnderTheNameItsFieldHasNow() throws Exception {
        FormsExport export = FormsExportReaderTest.sampleReader().readStructure();
        FormsForm source = export.forms().get("contact-us");
        FormsResults results = export.resultsOf(source);
        ImportedForm form = CONVERTER.convert(source, results);
        List<FormsSubmission> submissions = new ArrayList<>();
        FormsExportReaderTest.sampleReader().readSubmissions(submissions::add);
        FormsSubmission first = submissions.stream().filter(s -> s.uuid().equals("2c591198-6092-485b-b936-2a8cbf0213c8")).findFirst().orElseThrow();
        // the fields as the found form holds them: the first name renamed, the enquiry deleted
        Map<String, String> found = new java.util.HashMap<>();
        form.fields().filter(f -> !f.name().equals("your-enquiry")).forEach(f -> {
            found.put(f.sourceId(), f.name());
            found.put(f.sourceName(), f.name());
        });
        found.put(source.fields().get(0).uuid(), "firstname");
        found.put("text-input_0_1", "firstname");

        ImportedSubmission converted = new FormsSubmissionConverter(form, source, results, found).convert(first);

        assertEquals(List.of("your-last-name", "your-telephone-number", "your-email-address", "firstname", "text-area_0_4"),
                new ArrayList<>(converted.values().keySet()));
        assertFalse(converted.values().containsKey("your-first-name"));
        assertFalse(converted.values().containsKey("your-enquiry"));
        assertEquals(1, converted.report().size());
        assertTrue(converted.report().get(0).contains("text-area_0_4"), converted.report().get(0));
    }

    @Test
    void anAnswerWhoseFieldIsGoneKeepsItsFormsNameAndIsNoted() throws Exception {
        FormsExport export = FormsExportReaderTest.sampleReader().readStructure();
        FormsForm source = export.forms().get("contact-us");
        FormsResults results = export.resultsOf(source);
        ImportedForm form = CONVERTER.convert(source, results);
        FormsSubmission submission = new FormsSubmission("s1", "contact-us", Instant.EPOCH, null, null, "guest",
                List.of(new FormsResultField("old-field_0_9", "old-field_0_9", List.of("kept"), true, List.of()),
                        new FormsResultField("text-input_0_1", "text-input_0_1", List.of("Jane"), true, List.of())),
                "formFactory/results/contact-us/submissions/x/s1");

        ImportedSubmission converted = new FormsSubmissionConverter(form, source, results).convert(submission);
        assertEquals(List.of("kept"), converted.values().get("old-field_0_9"));
        assertEquals(List.of("Jane"), converted.values().get("your-first-name"));
        assertEquals(1, converted.report().size());
        assertTrue(converted.report().get(0).contains("old-field_0_9"));
    }

    @Test
    void aDateAndAFileAreConvertedForTheKindOfTheirField() {
        FormsField date = new FormsField("date_0_1", "ud", "fcnt:datePickerDefinition", Map.of("en", "Birthday"), null, Map.of(), List.of(), false, false);
        FormsField file = new FormsField("file_0_2", "uf", "fcnt:fileUploadDefinition", Map.of("en", "CV"), null, Map.of(), List.of(), false, false);
        FormsForm source = formOf(date, file);
        FormsResults results = resultsOf(Map.of("date_0_1", "ud", "file_0_2", "uf"));
        ImportedForm form = CONVERTER.convert(source, results);
        FormsSubmission submission = new FormsSubmission("s1", "f", Instant.EPOCH, "ref", null, "guest", List.of(
                new FormsResultField("date_0_1", "date_0_1", List.of("2024-08-12T22:00:00.000Z"), true, List.of()),
                new FormsResultField("file_0_2", "file_0_2", List.of("{\"url\":[],\"rendererName\":\"fileUpload\"}"), true,
                        List.of(new FormsFile("cv.pdf", "application/pdf", "formFactory/results/f/submissions/x/s1/file_0_2/cv.pdf")))),
                "formFactory/results/f/submissions/x/s1");

        ImportedSubmission converted = new FormsSubmissionConverter(form, source, results).convert(submission);
        assertEquals(List.of("2024-08-13"), converted.values().get("birthday"));
        assertFalse(converted.values().containsKey("cv"));
        assertEquals(1, converted.files().size());
        assertEquals("cv", converted.files().get(0).fieldName());
        assertEquals("cv.pdf", converted.files().get(0).fileName());
        assertEquals("application/pdf", converted.files().get(0).mimeType());
    }

    /** Forms stores the yes label of a ticked box and the no label of an unticked one, in the form's language. */
    @Test
    void aTickedAcceptTermsBoxStoresTrueAndAnUntickedOneNothingForAConsentAndForTheCheckbox() {
        Map<String, FormsOption> labels = Map.of(
                FormsOptionNames.YES, new FormsOption(FormsOptionNames.YES, null, Map.of("en", "Accepted", "fr", "Accepté")),
                FormsOptionNames.NO, new FormsOption(FormsOptionNames.NO, null, Map.of("en", "Not Accepted", "fr", "Refusé")));
        FormsField terms = new FormsField("terms_0_1", "ut", "fcnt:acceptTermCheckboxDefinition", Map.of("en", "I agree"), null, labels, List.of(), false, false);
        FormsForm source = formOf(terms);
        FormsResults results = resultsOf(Map.of("terms_0_1", "ut"));
        FormsSubmission ticked = submissionWith("Accepté");
        FormsSubmission unticked = submissionWith("Not Accepted");

        for (FormsFormConverter converter : List.of(CONVERTER, ELEMENTS_ONLY)) {
            FormsSubmissionConverter submissions = new FormsSubmissionConverter(converter.convert(source, results), source, results);
            assertEquals(List.of("true"), submissions.convert(ticked).values().get("i-agree"));
            assertFalse(submissions.convert(unticked).values().containsKey("i-agree"));
            assertTrue(submissions.convert(unticked).report().isEmpty());
        }
    }

    private static FormsSubmission submissionWith(String termsAnswer) {
        return new FormsSubmission("s1", "f", Instant.EPOCH, null, null, "guest",
                List.of(new FormsResultField("terms_0_1", "terms_0_1", List.of(termsAnswer), true, List.of())),
                "formFactory/results/f/submissions/x/s1");
    }

    private static FormsForm formOf(FormsField... fields) {
        FormsStep step = new FormsStep("step-1", 1, Map.of(), List.of(fields));
        return new FormsForm("f", "u", "formFactory/forms/f", "en", Map.of("en", "F"), Map.of(), FormsForm.Settings.NONE, List.of(step), List.of());
    }

    private static FormsResults resultsOf(Map<String, String> fieldIdsByName) {
        Map<String, FormsLabel> labels = new java.util.LinkedHashMap<>();
        fieldIdsByName.forEach((name, id) -> labels.put(name, new FormsLabel(name, id, Map.of(), Map.of())));
        return new FormsResults("f", "ur", "#/forms/f", "en", Map.of(), labels);
    }
}
