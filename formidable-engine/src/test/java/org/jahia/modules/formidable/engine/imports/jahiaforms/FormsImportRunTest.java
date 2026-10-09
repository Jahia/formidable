package org.jahia.modules.formidable.engine.imports.jahiaforms;

import org.jahia.modules.formidable.engine.imports.ImportReport;
import org.jahia.modules.formidable.engine.imports.ImportWriter;
import org.jahia.modules.formidable.engine.imports.model.ImportedSubmission;
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

    private static ImportWriter writer() throws RepositoryException {
        ImportWriter writer = mock(ImportWriter.class);
        when(writer.importedFormsPath()).thenReturn(FOLDER);
        when(writer.importedSubmissionIds()).thenReturn(Set.of());
        return writer;
    }

    private static JCRNodeWrapper node(String name, String path) throws RepositoryException {
        JCRNodeWrapper node = mock(JCRNodeWrapper.class);
        when(node.getName()).thenReturn(name);
        when(node.getPath()).thenReturn(path);
        return node;
    }

    @Test
    void theDryRunReportsEveryFormAndSubmissionAndWritesNothing() throws Exception {
        ImportWriter writer = writer();
        FormsImportRun run = new FormsImportRun(FormsExportReaderTest.sampleReader(), writer, true, type -> true, source -> true, false);

        JSONObject report = run.run().toJson();

        assertTrue(report.getBoolean("dryRun"));
        assertEquals(3, report.getJSONObject("totals").getInt("forms"));
        assertEquals(106, report.getJSONObject("totals").getInt("submissionsFound"));
        assertEquals(106, report.getJSONObject("totals").getInt("submissionsToImport"));
        assertEquals(0, report.getJSONObject("totals").getInt("submissionsImported"));
        JSONObject contact = report.getJSONArray("forms").getJSONObject(0);
        assertEquals("created", contact.getString("outcome"));
        assertEquals(FOLDER + "/contact-us", contact.getString("targetPath"));
        assertEquals(5, contact.getJSONArray("fields").length());
        assertEquals(44, contact.getJSONObject("submissions").getInt("found"));
        verify(writer, never()).findOrCreateForm(any());
        verify(writer, never()).findOrCreateResultsEntry(any(), any());
        verify(writer, never()).writeSubmission(any(), any(), any());
        verify(writer, never()).save();
    }

    @Test
    void theImportSavesAFormBeforeItsEntryAndWritesEverySubmissionByBatches() throws Exception {
        ImportWriter writer = writer();
        JCRNodeWrapper form = node("contact-us", FOLDER + "/contact-us");
        JCRNodeWrapper entry = mock(JCRNodeWrapper.class);
        when(writer.findOrCreateForm(any())).thenReturn(new ImportWriter.FormHandle(form, true));
        when(writer.findOrCreateResultsEntry(any(), any())).thenReturn(entry);
        FormsImportRun run = new FormsImportRun(FormsExportReaderTest.sampleReader(), writer, false, type -> true, source -> true, false);

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
        // the form as the contributor left it: the first name renamed, the enquiry deleted
        when(writer.importedFields(found)).thenReturn(List.of(
                new ImportWriter.FoundField("firstname", "fmdb:inputText", source.fields().get(0).uuid(), "text-input_0_1"),
                new ImportWriter.FoundField("your-last-name", "fmdb:inputText", source.fields().get(1).uuid(), "text-input_0_1_copy_01"),
                new ImportWriter.FoundField("your-email-address", "fmdb:inputEmail", source.fields().get(2).uuid(), "email-input_0_2"),
                new ImportWriter.FoundField("your-telephone-number", "fmdb:inputText", source.fields().get(3).uuid(), "text-input_0_5")));
        JCRNodeWrapper other = node("other", FOLDER + "/other");
        when(writer.findOrCreateForm(any())).thenReturn(new ImportWriter.FormHandle(other, true));
        when(writer.findOrCreateResultsEntry(any(), any())).thenReturn(entry);
        FormsImportRun run = new FormsImportRun(FormsExportReaderTest.sampleReader(), writer, false, type -> true, s -> true, false);

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
        assertTrue(ofContact.stream().noneMatch(s -> s.values().containsKey("your-first-name")));
        assertTrue(ofContact.stream().noneMatch(s -> s.values().containsKey("your-enquiry")));
        assertTrue(ofContact.stream().allMatch(s -> s.values().containsKey("text-area_0_4")));
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
        JSONObject files = new FormsImportRun(FormsExportReader.open(withoutBinary), writer(), true, type -> true, s -> true, false)
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
        files = new FormsImportRun(FormsExportReader.open(withBinary), writer(), true, type -> true, s -> true, false)
                .run().toJson().getJSONArray("forms").getJSONObject(0).getJSONObject("files");
        assertEquals(1, files.getInt("count"));
        assertEquals(321, files.getLong("bytes"));
        assertEquals(0, files.getInt("missing"));
        assertFalse(photo.path().isEmpty());
    }
}
