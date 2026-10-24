package org.jahia.modules.formidable.formsimport.jahiaforms;

import org.jahia.modules.formidable.formsimport.FormChoice;
import org.jahia.modules.formidable.formsimport.ImportReport;
import org.jahia.modules.formidable.formsimport.ImportWriter;
import org.jahia.modules.formidable.formsimport.model.ImportedFile;
import org.jahia.modules.formidable.formsimport.model.ImportedSubmission;
import org.jahia.services.content.JCRNodeWrapper;
import javax.jcr.RepositoryException;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The run over the sample export against a writer that is mocked: what the dry run reports and leaves
 * alone, the order the import writes in, and the names a second run gives to its values.
 */
class FormsImportRunTest {

    private static final String FOLDER = "/sites/x/contents/imported-forms";
    private static final String CONTACT_ID = "08246a0f-de43-4dbb-b91e-55cdd366614b";
    private static final String RESULTS = "/sites/x/formidable-results";
    private static final Map<String, FormChoice> CREATE_ALL = Map.of("contact-us", FormChoice.CREATE,
            "newsletterregistration", FormChoice.CREATE, "newsletter-registration", FormChoice.CREATE);
    private static final Map<String, FormChoice> RESULTS_ALL = Map.of("contact-us", FormChoice.RESULTS_ONLY,
            "newsletterregistration", FormChoice.RESULTS_ONLY, "newsletter-registration", FormChoice.RESULTS_ONLY);

    private static ImportWriter writer() throws RepositoryException {
        ImportWriter writer = mock(ImportWriter.class);
        when(writer.importedFormsPath()).thenReturn(FOLDER);
        when(writer.resultsRootPath()).thenReturn(RESULTS);
        when(writer.importedSubmissionIds()).thenReturn(Set.of());
        return writer;
    }

    private static JCRNodeWrapper node(String name, String path) {
        JCRNodeWrapper node = mock(JCRNodeWrapper.class);
        when(node.getName()).thenReturn(name);
        when(node.getPath()).thenReturn(path);
        return node;
    }

    @Test
    void theDryRunReportsEveryFormAndSubmissionAndWritesNothing() throws Exception {
        ImportWriter writer = writer();
        FormsImportRun run = new FormsImportRun(FormsExportReaderTest.sampleReader(), writer, true, Map.of(), type -> true, source -> true, false);

        JSONObject report = run.run().toJson();

        assertTrue(report.getBoolean("dryRun"));
        assertEquals(3, report.getJSONObject("totals").getInt("forms"));
        assertEquals(106, report.getJSONObject("totals").getInt("submissionsFound"));
        assertEquals(106, report.getJSONObject("totals").getInt("submissionsToImport"));
        assertEquals(0, report.getJSONObject("totals").getInt("submissionsImported"));
        JSONObject contact = report.getJSONArray("forms").getJSONObject(0);
        // before any choice, a form is described as the results alone, under an entry named after it, the fields as its columns
        assertEquals("resultsOnly", contact.getString("outcome"));
        assertEquals(RESULTS + "/contact-us", contact.getString("targetPath"));
        assertEquals(5, contact.getJSONArray("fields").length());
        assertEquals(44, contact.getJSONObject("submissions").getInt("found"));
        verify(writer, never()).findOrCreateForm(any());
        verify(writer, never()).findOrCreateResultsEntry(any(), any());
        verify(writer, never()).createResultsOnlyEntry(any());
        verify(writer, never()).writeSubmission(any(), any(), any());
        verify(writer, never()).save();
    }

    @Test
    void displaysThatCouldNotBeReadAreNamedOnceForTheRun() throws Exception {
        // Verifies point 6 of the review: a redirect set on a display is lost when the displays cannot be read, so
        // the run says it once, at its level, and not once per form; a run that read them says nothing. Displays on
        // another instance are information, a failed read is a to-do. The pages know why only once the forms asked
        // for their displays, so a run that reads the reason before the conversion has no note.
        assertRunNote(ImportEnvironment.DisplaysUnread.NOT_ON_THIS_INSTANCE, "report.displaysElsewhere", "info");
        assertRunNote(ImportEnvironment.DisplaysUnread.QUERY_FAILED, "report.displaysUnread", "action");
        JSONObject read = new FormsImportRun(FormsExportReaderTest.sampleReader(), writer(), true, Map.of(), type -> true, source -> true, false)
                .run().toJson();
        assertEquals(0, read.getJSONArray("notes").length());
    }

    private void assertRunNote(ImportEnvironment.DisplaysUnread reason, String code, String level) throws Exception {
        ImportEnvironment.SitePages unread = new ImportEnvironment.SitePages() {
            private ImportEnvironment.DisplaysUnread asked;

            @Override
            public Optional<String> pageOf(String reference) {
                return Optional.empty();
            }

            @Override
            public List<FormsDisplay> displaysOf(String formUuid) {
                asked = reason;
                return List.of();
            }

            @Override
            public Optional<ImportEnvironment.DisplaysUnread> displaysUnread() {
                return Optional.ofNullable(asked);
            }
        };
        ImportEnvironment environment = new ImportEnvironment(type -> true, source -> true, type -> false,
                ImportEnvironment.ANY_PROPERTY, false, unread);

        JSONObject report = new FormsImportRun(FormsExportReaderTest.sampleReader(), writer(), true, Map.of(), environment).run().toJson();

        assertEquals(1, report.getJSONArray("notes").length(), code);
        assertEquals(code, report.getJSONArray("notes").getJSONObject(0).getString("code"));
        assertEquals(level, report.getJSONArray("notes").getJSONObject(0).getString("level"), code);
    }

    @Test
    void theImportSavesAFormBeforeItsEntryAndWritesEverySubmissionByBatches() throws Exception {
        ImportWriter writer = writer();
        JCRNodeWrapper form = node("contact-us", FOLDER + "/contact-us");
        JCRNodeWrapper entry = mock(JCRNodeWrapper.class);
        when(writer.findOrCreateForm(any())).thenReturn(new ImportWriter.FormHandle(form, true));
        when(writer.findOrCreateResultsEntry(any(), any())).thenReturn(entry);
        FormsImportRun run = new FormsImportRun(FormsExportReaderTest.sampleReader(), writer, false, CREATE_ALL, type -> true, source -> true, false);

        ImportReport report = run.run();

        assertEquals(106, report.toJson().getJSONObject("totals").getInt("submissionsImported"));
        InOrder order = inOrder(writer);
        order.verify(writer).findOrCreateForm(any());
        order.verify(writer).saveForms();
        order.verify(writer).findOrCreateResultsEntry(eq(form), any());
        order.verify(writer).save();
        verify(writer, org.mockito.Mockito.times(106)).writeSubmission(eq(entry), any(), any());
        // three forms saved one by one, then a batch of 100 and the remainder
        verify(writer, org.mockito.Mockito.times(5)).save();
    }

    @Test
    void aSecondRunFindsTheFormAndNamesTheValuesAfterTheFieldsItHoldsNow() throws Exception {
        ImportWriter writer = writer();
        JCRNodeWrapper found = node("contact-us", "/sites/x/contents/reviewed/contact-us");
        JCRNodeWrapper entry = mock(JCRNodeWrapper.class);
        when(writer.findForm(eq(CONTACT_ID), any())).thenReturn(found);
        FormsExport export = FormsExportReaderTest.sampleReader().readStructure();
        FormsForm source = export.forms().get("contact-us");
        // the form as the contributor left it: the first name renamed, the enquiry deleted; the telephone field
        // was renamed in Forms between the two exports, so only its identity can find it
        when(writer.importedFields(found)).thenReturn(List.of(
                new ImportWriter.FoundField("firstname", "fmdb:inputText", source.fields().get(0).uuid(), "text-input_0_1"),
                new ImportWriter.FoundField("your-last-name", "fmdb:inputText", source.fields().get(1).uuid(), "text-input_0_1_copy_01"),
                new ImportWriter.FoundField("your-email-address", "fmdb:inputEmail", source.fields().get(2).uuid(), "email-input_0_2"),
                new ImportWriter.FoundField("your-telephone-number", "fmdb:inputText", source.fields().get(3).uuid(), "phone-input_0_9")));
        JCRNodeWrapper other = node("other", FOLDER + "/other");
        when(writer.findOrCreateForm(any())).thenReturn(new ImportWriter.FormHandle(other, true));
        when(writer.findOrCreateResultsEntry(any(), any())).thenReturn(entry);
        FormsImportRun run = new FormsImportRun(FormsExportReaderTest.sampleReader(), writer, false, CREATE_ALL, type -> true, s -> true, false);

        JSONObject report = run.run().toJson();

        JSONObject contact = report.getJSONArray("forms").getJSONObject(0);
        assertEquals("found", contact.getString("outcome"));
        assertEquals("/sites/x/contents/reviewed/contact-us", contact.getString("targetPath"));
        assertEquals(List.of("firstname", "your-last-name", "your-email-address", "your-telephone-number"),
                contact.getJSONArray("fields").toList().stream().map(f -> ((Map<?, ?>) f).get("name")).toList());
        // the two newsletter forms are created, the found one is left as it is
        verify(writer, org.mockito.Mockito.times(2)).saveForms();
        verify(writer, never()).findOrCreateForm(org.mockito.ArgumentMatchers.argThat(f -> f.name().equals("contact-us")));
        ArgumentCaptor<ImportedSubmission> written = ArgumentCaptor.forClass(ImportedSubmission.class);
        verify(writer, org.mockito.Mockito.times(106)).writeSubmission(any(), written.capture(), any());
        List<ImportedSubmission> ofContact = written.getAllValues().stream().filter(s -> CONTACT_ID.equals(s.sourceFormId())).toList();
        assertEquals(44, ofContact.size());
        assertTrue(ofContact.stream().allMatch(s -> s.values().containsKey("firstname")));
        assertTrue(ofContact.stream().allMatch(s -> s.values().containsKey("your-telephone-number")));
        assertTrue(ofContact.stream().noneMatch(s -> s.values().containsKey("text-input_0_5")));
        assertTrue(ofContact.stream().noneMatch(s -> s.values().containsKey("your-first-name")));
        assertTrue(ofContact.stream().noneMatch(s -> s.values().containsKey("your-enquiry")));
        assertTrue(ofContact.stream().allMatch(s -> s.values().containsKey("text-area_0_4")));
    }

    @Test
    void theImportLeavesOutEveryFormTheChoicesDoNotName() throws Exception {
        ImportWriter writer = writer();
        FormsImportRun run = new FormsImportRun(FormsExportReaderTest.sampleReader(), writer, false, Map.of(), type -> true, s -> true, false);

        JSONObject report = run.run().toJson();

        assertEquals(3, report.getJSONObject("totals").getInt("forms"));
        assertEquals(0, report.getJSONObject("totals").getInt("submissionsImported"));
        report.getJSONArray("forms").forEach(form -> assertEquals("skipped", ((JSONObject) form).getString("outcome")));
        verify(writer, never()).findForm(any(), any());
        verify(writer, never()).createResultsOnlyEntry(any());
        verify(writer, never()).findOrCreateForm(any());
        verify(writer, never()).writeSubmission(any(), any(), any());
    }

    @Test
    void theImportWritesTheResultsAloneWhenAsked() throws Exception {
        ImportWriter writer = writer();
        JCRNodeWrapper entry = node("contact-us", RESULTS + "/contact-us");
        when(writer.createResultsOnlyEntry(any())).thenReturn(entry);
        FormsImportRun run = new FormsImportRun(FormsExportReaderTest.sampleReader(), writer, false, RESULTS_ALL, type -> true, s -> true, false);

        JSONObject report = run.run().toJson();

        JSONObject contact = report.getJSONArray("forms").getJSONObject(0);
        assertEquals("resultsOnly", contact.getString("outcome"));
        assertEquals(RESULTS + "/contact-us", contact.getString("targetPath"));
        assertEquals(106, report.getJSONObject("totals").getInt("submissionsImported"));
        verify(writer, org.mockito.Mockito.times(3)).createResultsOnlyEntry(any());
        verify(writer, never()).findOrCreateForm(any());
        verify(writer, never()).saveForms();
        verify(writer, never()).findOrCreateResultsEntry(any(), any());
        verify(writer, org.mockito.Mockito.times(106)).writeSubmission(eq(entry), any(), any());
    }

    @Test
    void aSecondRunFindsTheEntryWrittenAloneAndAddsNothingToIt() throws Exception {
        ImportWriter writer = writer();
        JCRNodeWrapper found = node("contact-us", RESULTS + "/contact-us");
        JCRNodeWrapper other = node("other", RESULTS + "/other");
        when(writer.findImportedEntry(CONTACT_ID)).thenReturn(found);
        when(writer.createResultsOnlyEntry(any())).thenReturn(other);
        FormsImportRun run = new FormsImportRun(FormsExportReaderTest.sampleReader(), writer, false, RESULTS_ALL, type -> true, s -> true, false);

        JSONObject report = run.run().toJson();

        JSONObject contact = report.getJSONArray("forms").getJSONObject(0);
        assertEquals("found", contact.getString("outcome"));
        assertEquals(RESULTS + "/contact-us", contact.getString("targetPath"));
        assertEquals(5, contact.getJSONArray("fields").length());
        // the two newsletter forms get their own entries, the found one receives its submissions as it is
        verify(writer, org.mockito.Mockito.times(2)).createResultsOnlyEntry(any());
        verify(writer, never()).createResultsOnlyEntry(org.mockito.ArgumentMatchers.argThat(f -> f.name().equals("contact-us")));
        verify(writer, org.mockito.Mockito.times(44)).writeSubmission(eq(found), any(), any());
    }

    @Test
    void theImportAttachesTheResultsToTheChosenFormAndNamesTheValuesAfterItsFields() throws Exception {
        ImportWriter writer = writer();
        JCRNodeWrapper target = node("contact", "/sites/x/contents/forms/contact");
        JCRNodeWrapper entry = node("contact", RESULTS + "/contact");
        JCRNodeWrapper other = node("other", RESULTS + "/other");
        when(writer.findSiteForm("f-contact")).thenReturn(target);
        when(writer.findOrCreateResultsEntry(eq(target), any())).thenReturn(entry);
        when(writer.createResultsOnlyEntry(any())).thenReturn(other);
        Map<String, String> mapping = Map.of("your-first-name", "firstname", "your-email-address", "email");
        Map<String, FormChoice> choices = Map.of("contact-us", FormChoice.attachTo("f-contact", mapping));
        FormsImportRun run = new FormsImportRun(FormsExportReaderTest.sampleReader(), writer, false, choices, type -> true, s -> true, false);

        JSONObject report = run.run().toJson();

        JSONObject contact = report.getJSONArray("forms").getJSONObject(0);
        assertEquals("attached", contact.getString("outcome"));
        assertEquals("/sites/x/contents/forms/contact", contact.getString("targetPath"));
        // the fields keep their own names in the report, with their titles for the dialog to match
        assertEquals("your-first-name", contact.getJSONArray("fields").getJSONObject(0).getString("name"));
        assertEquals("Your First name", contact.getJSONArray("fields").getJSONObject(0).getJSONObject("titles").getString("en"));
        verify(writer, never()).findOrCreateForm(any());
        verify(writer).recordMapping(entry, CONTACT_ID, mapping);
        ArgumentCaptor<ImportedSubmission> written = ArgumentCaptor.forClass(ImportedSubmission.class);
        verify(writer, org.mockito.Mockito.times(44)).writeSubmission(eq(entry), written.capture(), any());
        assertTrue(written.getAllValues().stream().allMatch(s -> s.values().containsKey("firstname")));
        assertTrue(written.getAllValues().stream().allMatch(s -> s.values().containsKey("email")));
        assertTrue(written.getAllValues().stream().allMatch(s -> s.values().containsKey("your-last-name")));
        assertTrue(written.getAllValues().stream().noneMatch(s -> s.values().containsKey("your-first-name")));
    }

    @Test
    void aSecondRunFindsTheAttachedEntryAndNamesTheValuesAsTheFirstDid() throws Exception {
        ImportWriter writer = writer();
        JCRNodeWrapper target = node("contact", "/sites/x/contents/forms/contact");
        JCRNodeWrapper entry = node("contact", RESULTS + "/contact");
        JCRNodeWrapper other = node("other", RESULTS + "/other");
        when(writer.findImportedEntry(CONTACT_ID)).thenReturn(entry);
        when(writer.formOf(entry)).thenReturn(target);
        when(writer.mappingOf(entry, CONTACT_ID)).thenReturn(Map.of("your-first-name", "firstname"));
        when(writer.createResultsOnlyEntry(any())).thenReturn(other);
        FormsImportRun run = new FormsImportRun(FormsExportReaderTest.sampleReader(), writer, false, RESULTS_ALL, type -> true, s -> true, false);

        JSONObject report = run.run().toJson();

        JSONObject contact = report.getJSONArray("forms").getJSONObject(0);
        assertEquals("found", contact.getString("outcome"));
        assertEquals("/sites/x/contents/forms/contact", contact.getString("targetPath"));
        assertEquals("firstname", contact.getJSONArray("fields").getJSONObject(0).getString("name"));
        verify(writer, never()).recordMapping(any(), any(), any());
        ArgumentCaptor<ImportedSubmission> written = ArgumentCaptor.forClass(ImportedSubmission.class);
        verify(writer, org.mockito.Mockito.times(44)).writeSubmission(eq(entry), written.capture(), any());
        assertTrue(written.getAllValues().stream().allMatch(s -> s.values().containsKey("firstname")));
        assertTrue(written.getAllValues().stream().allMatch(s -> s.values().containsKey("your-last-name")));
    }

    @Test
    void anAttachmentToAFormGoneSinceTheReviewFailsTheImport() throws Exception {
        ImportWriter writer = writer();
        Map<String, FormChoice> choices = Map.of("contact-us", FormChoice.attachTo("gone", Map.of()));
        FormsImportRun run = new FormsImportRun(FormsExportReaderTest.sampleReader(), writer, false, choices, type -> true, s -> true, false);

        RepositoryException failure = org.junit.jupiter.api.Assertions.assertThrows(RepositoryException.class, run::run);

        assertTrue(failure.getMessage().contains("contact-us"));
        verify(writer, never()).writeSubmission(any(), any(), any());
    }

    @Test
    void aFileTheZipDoesNotHoldIsReportedMissingNotCounted() throws Exception {
        String export = """
                <?xml version="1.0" encoding="UTF-8"?>
                <formFactory xmlns:jcr="http://www.jcp.org/jcr/1.0" xmlns:j="http://www.jahia.org/jahia/1.0" jcr:primaryType="fcnt:formFactory">
                  <results jcr:primaryType="fcnt:resultsFolder">
                    <survey jcr:primaryType="fcnt:formResults" jcr:uuid="r0000000-0000-0000-0000-000000000009" parentForm="#/forms/survey">
                      <labels jcr:primaryType="fcnt:resultLabels">
                        <upload jcr:primaryType="fcnt:definitionOptionsTranslatable" fieldId="u0000000-0000-0000-0000-000000000001"><j:translation_en jcr:primaryType="jnt:translation" label="Your photo"/></upload>
                      </labels>
                      <submissions jcr:primaryType="fcnt:submissions">
                        <s1 jcr:primaryType="fcnt:result" jcr:created="2024-06-01T10:00:00.000Z" jcr:createdBy="guest">
                          <upload_0_1 jcr:primaryType="fcnt:resultField" label="#/results/survey/labels/upload" result="photo.png">
                            <photo.png jcr:primaryType="jnt:file"><jcr:content jcr:primaryType="jnt:resource" jcr:mimeType="image/png"/></photo.png>
                          </upload_0_1>
                        </s1>
                      </submissions>
                    </survey>
                  </results>
                  <forms jcr:primaryType="fcnt:formsFolder">
                    <survey jcr:primaryType="fcnt:form" jcr:uuid="f0000000-0000-0000-0000-000000000008">
                      <j:translation_en jcr:primaryType="jnt:translation" jcr:title="Survey"/>
                      <step-1 jcr:primaryType="fcnt:step" stepNumber="1">
                        <photo jcr:primaryType="fcnt:fileUploadDefinition" jcr:uuid="u0000000-0000-0000-0000-000000000001"><j:translation_en jcr:primaryType="jnt:translation" jcr:title="Your photo"/></photo>
                      </step-1>
                    </survey>
                  </forms>
                </formFactory>
                """;
        Path withoutBinary = FormsExportReaderTest.zipOf(Map.of(FormsExportZip.XML, export));
        JSONObject files = new FormsImportRun(FormsExportReader.open(withoutBinary), writer(), true, Map.of(), type -> true, s -> true, false)
                .run().toJson().getJSONArray("forms").getJSONObject(0).getJSONObject("files");
        assertEquals(0, files.getInt("count"));
        assertEquals(1, files.getInt("missing"));

        // the same export with the binary where the Jahia export writes it: counted, with its size
        List<FormsSubmission> submissions = new java.util.ArrayList<>();
        FormsExportReader.open(withoutBinary).readSubmissions(submissions::add);
        FormsFile photo = submissions.get(0).fields().get(0).files().get(0);
        Path withBinary = Files.createTempFile("forms-export", ".zip");
        withBinary.toFile().deleteOnExit();
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(withBinary))) {
            zip.putNextEntry(new ZipEntry(FormsExportZip.XML));
            zip.write(export.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("live-content/" + photo.path() + "/" + photo.name()));
            zip.write(new byte[321]);
            zip.closeEntry();
        }
        files = new FormsImportRun(FormsExportReader.open(withBinary), writer(), true, Map.of(), type -> true, s -> true, false)
                .run().toJson().getJSONArray("forms").getJSONObject(0).getJSONObject("files");
        assertEquals(1, files.getInt("count"));
        assertEquals(321, files.getLong("bytes"));
        assertEquals(0, files.getInt("missing"));
        assertFalse(photo.path().isEmpty());
    }

    @Test
    void aFileOfARealExportIsCountedThenWrittenFromItsBinaryInTheZip() throws Exception {
        JSONObject dryRun = new FormsImportRun(FormsExportWithFileTest.withFileReader(), writer(), true, Map.of(), type -> true, s -> true, false)
                .run().toJson();
        JSONObject files = dryRun.getJSONArray("forms").getJSONObject(0).getJSONObject("files");
        assertEquals(1, files.getInt("count"));
        assertEquals(578, files.getLong("bytes"));
        assertEquals(0, files.getInt("missing"));

        ImportWriter writer = writer();
        JCRNodeWrapper form = node("contact-us", FOLDER + "/contact-us");
        when(writer.findOrCreateForm(any())).thenReturn(new ImportWriter.FormHandle(form, true));
        when(writer.findOrCreateResultsEntry(any(), any())).thenReturn(mock(JCRNodeWrapper.class));
        new FormsImportRun(FormsExportWithFileTest.withFileReader(), writer, false, CREATE_ALL, type -> true, s -> true, false).run();

        ArgumentCaptor<ImportedSubmission> written = ArgumentCaptor.forClass(ImportedSubmission.class);
        ArgumentCaptor<ImportWriter.BinaryOpener> openers = ArgumentCaptor.forClass(ImportWriter.BinaryOpener.class);
        verify(writer, org.mockito.Mockito.times(109)).writeSubmission(any(), written.capture(), openers.capture());
        int at = 0;
        while (written.getAllValues().get(at).files().isEmpty()) {
            at++;
        }
        ImportedSubmission withFile = written.getAllValues().get(at);
        assertEquals("2dd1fe78-e095-4f47-ab08-b5c30df47a4b", withFile.sourceId());
        ImportedFile file = withFile.files().get(0);
        // the file lands under the name the created form gives its field, from the title "Your Id"
        assertEquals("your-id", file.fieldName());
        assertEquals("document.pdf", file.fileName());
        assertEquals("application/pdf", file.mimeType());
        try (java.io.InputStream binary = openers.getAllValues().get(at).open(file)) {
            assertEquals(578, binary.readAllBytes().length);
        }
    }

    @Test
    void aSkippedFormIsLeftOutWhetherNewOrFound() throws Exception {
        ImportWriter writer = writer();
        JCRNodeWrapper entry = mock(JCRNodeWrapper.class);
        when(writer.createResultsOnlyEntry(any())).thenReturn(entry);
        Map<String, FormChoice> choices = Map.of("contact-us", FormChoice.SKIP, "newsletterregistration", FormChoice.RESULTS_ONLY,
                "newsletter-registration", FormChoice.RESULTS_ONLY);

        JSONObject report = new FormsImportRun(FormsExportReaderTest.sampleReader(), writer, false, choices, type -> true, s -> true, false)
                .run().toJson();

        JSONObject contact = report.getJSONArray("forms").getJSONObject(0);
        assertEquals("skipped", contact.getString("outcome"));
        assertEquals(44, contact.getJSONObject("submissions").getInt("found"));
        assertEquals(0, contact.getJSONObject("submissions").getInt("toImport"));
        assertEquals(0, contact.getJSONObject("submissions").getInt("imported"));
        assertEquals(62, report.getJSONObject("totals").getInt("submissionsImported"));
        verify(writer, never()).findForm(eq(CONTACT_ID), any());
        verify(writer, org.mockito.Mockito.times(2)).createResultsOnlyEntry(any());
        ArgumentCaptor<ImportedSubmission> written = ArgumentCaptor.forClass(ImportedSubmission.class);
        verify(writer, org.mockito.Mockito.times(62)).writeSubmission(any(), written.capture(), any());
        assertTrue(written.getAllValues().stream().noneMatch(s -> CONTACT_ID.equals(s.sourceFormId())));

        // a found form left out takes none of its new submissions; everything left out leaves nothing to import
        ImportWriter allFound = writer();
        JCRNodeWrapper found = node("contact-us", FOLDER + "/contact-us");
        when(allFound.findForm(any(), any())).thenReturn(found);
        when(allFound.importedFields(any())).thenReturn(List.of());
        Map<String, FormChoice> skipAll = Map.of("contact-us", FormChoice.SKIP, "newsletterregistration", FormChoice.SKIP,
                "newsletter-registration", FormChoice.SKIP);
        JSONObject none = new FormsImportRun(FormsExportReaderTest.sampleReader(), allFound, true, skipAll, type -> true, s -> true, false)
                .run().toJson();
        assertEquals(0, none.getJSONObject("totals").getInt("submissionsToImport"));
        assertTrue(none.getBoolean("nothingToImport"));
    }

    @Test
    void aSubmissionThatCannotBeWrittenIsLeftBehindAndItsBatchWrittenAgainOneByOne() throws Exception {
        ImportWriter writer = writer();
        JCRNodeWrapper entry = mock(JCRNodeWrapper.class);
        when(writer.createResultsOnlyEntry(any())).thenReturn(entry);
        // the fifth submission written fails, every time it is written
        java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();
        java.util.concurrent.atomic.AtomicReference<String> failing = new java.util.concurrent.atomic.AtomicReference<>();
        when(writer.writeSubmission(any(), any(), any())).thenAnswer(invocation -> {
            ImportedSubmission submission = invocation.getArgument(1);
            if (calls.incrementAndGet() == 5) {
                failing.set(submission.sourceId());
            }
            if (submission.sourceId().equals(failing.get())) {
                throw new RepositoryException("constraint violated");
            }
            return entry;
        });

        JSONObject report = new FormsImportRun(FormsExportReaderTest.sampleReader(), writer, false, RESULTS_ALL, type -> true, s -> true, false)
                .run().toJson();

        assertEquals(105, report.getJSONObject("totals").getInt("submissionsImported"));
        assertEquals(1, report.getJSONObject("totals").getInt("submissionsFailed"));
        List<JSONObject> withFailure = new java.util.ArrayList<>();
        report.getJSONArray("forms").forEach(form -> {
            if (((JSONObject) form).getJSONObject("submissions").getInt("failed") > 0) {
                withFailure.add((JSONObject) form);
            }
        });
        assertEquals(1, withFailure.size());
        JSONObject incident = withFailure.get(0).getJSONArray("incidents").getJSONObject(0);
        assertEquals("submission.writeFailed", incident.getString("code"));
        assertEquals(failing.get(), incident.getString("submission"));
        assertEquals("constraint violated", incident.getJSONObject("params").getString("reason"));
        // the submission left behind does not count its values in the figures of its form
        String failingForm = withFailure.get(0).getString("sourceName");
        JSONObject dryRun = new FormsImportRun(FormsExportReaderTest.sampleReader(), writer(), true, Map.of(), type -> true, s -> true, false)
                .run().toJson();
        int everyValue = 0;
        for (Object form : dryRun.getJSONArray("forms")) {
            if (((JSONObject) form).getString("sourceName").equals(failingForm)) {
                everyValue = ((JSONObject) form).getJSONObject("values").getInt("converted");
            }
        }
        assertTrue(withFailure.get(0).getJSONObject("values").getInt("converted") < everyValue);
        // the batch dropped once, the four written before the failure written again and saved one by one
        verify(writer).discardSubmissions();
        verify(writer, org.mockito.Mockito.times(110)).writeSubmission(any(), any(), any());
        assertTrue(report.isNull("stopped"));
    }

    @Test
    void theImportStopsWithItsReportPastTooManyFailedSubmissions() throws Exception {
        ImportWriter writer = writer();
        when(writer.createResultsOnlyEntry(any())).thenReturn(mock(JCRNodeWrapper.class));
        when(writer.writeSubmission(any(), any(), any())).thenThrow(new RepositoryException("disk full"));
        FormsImportRun run = new FormsImportRun(FormsExportReaderTest.sampleReader(), writer, false, RESULTS_ALL, type -> true, s -> true, false);

        FormsImportRun.Stopped stopped = org.junit.jupiter.api.Assertions.assertThrows(FormsImportRun.Stopped.class, run::run);

        JSONObject totals = stopped.report().toJson().getJSONObject("totals");
        assertEquals(FormsImportRun.MAX_FAILED_SUBMISSIONS, totals.getInt("submissionsFailed"));
        assertEquals(0, totals.getInt("submissionsImported"));
        assertEquals(3, stopped.report().toJson().getJSONArray("forms").length());
    }

    @Test
    void theImportListsWhatASubmissionLostAndTheDryRunDoesNot() throws Exception {
        JSONObject dry = new FormsImportRun(FormsSyntheticExportTest.reader(), writer(), true, Map.of(), type -> true, s -> true, false)
                .run().toJson();
        JSONObject dryForm = dry.getJSONArray("forms").getJSONObject(0);
        assertEquals(1, dryForm.getJSONObject("values").getInt("dropped"));
        assertTrue(dryForm.getJSONArray("incidents").isEmpty());

        ImportWriter writer = writer();
        when(writer.createResultsOnlyEntry(any())).thenReturn(mock(JCRNodeWrapper.class));
        JSONObject report = new FormsImportRun(FormsSyntheticExportTest.reader(), writer, false, Map.of("survey", FormChoice.RESULTS_ONLY), type -> true, s -> true, false)
                .run().toJson();

        JSONObject survey = report.getJSONArray("forms").getJSONObject(0);
        assertEquals(1, survey.getJSONObject("values").getInt("dropped"));
        JSONObject incident = survey.getJSONArray("incidents").getJSONObject(0);
        assertEquals("submission.passwordDropped", incident.getString("code"));
        assertEquals("s1", incident.getString("submission"));
        assertEquals("2024-06-01T10:00:00Z", incident.getString("created"));
        assertEquals("Password", incident.getJSONObject("params").getString("field"));
    }
}
