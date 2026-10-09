package org.jahia.modules.formidable.engine.imports.jahiaforms;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The reader against the anonymised sample export: 3 forms, 9 fields, 3 results entries, 106 submissions,
 * taken as "Export Zip with live content" (repository.xml and live-repository.xml identical).
 */
class FormsExportReaderTest {

    static final String SAMPLE = "/imports/jahiaforms/formFactory-sample.zip";
    private static final String XML_HEAD = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>";
    private static final String JCR_NS = "xmlns:jcr=\"http://www.jcp.org/jcr/1.0\"";

    static FormsExportReader sampleReader() {
        try {
            return new FormsExportReader(new FormsExportZip(sampleZip()));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** The sample, copied once to a file: the zip is read through the central directory, which needs a file. */
    static Path sampleZip() throws IOException {
        Path copy = Files.createTempFile("formFactory-sample", ".zip");
        copy.toFile().deleteOnExit();
        try (InputStream resource = FormsExportReaderTest.class.getResourceAsStream(SAMPLE)) {
            Files.copy(resource, copy, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        return copy;
    }

    @Test
    void theStructureHoldsTheFormsAndTheResultsEntries() throws Exception {
        FormsExport export = sampleReader().readStructure();

        assertEquals("formFactory", export.rootName());
        assertEquals(List.of("contact-us", "newsletterregistration", "newsletter-registration"), new ArrayList<>(export.forms().keySet()));
        assertEquals(3, export.results().size());

        FormsForm contact = export.forms().get("contact-us");
        assertEquals("08246a0f-de43-4dbb-b91e-55cdd366614b", contact.uuid());
        assertEquals("formFactory/forms/contact-us", contact.path());
        assertEquals("Contact Us", contact.titles().get("en"));
        assertEquals(1, contact.steps().size());
        assertEquals(List.of("text-input_0_1", "text-input_0_1_copy_01", "email-input_0_2", "text-input_0_5", "text-area_0_4"),
                contact.fields().stream().map(FormsField::name).toList());
        assertEquals(List.of(FormsAction.SAVE_TO_JCR, FormsAction.REDIRECT_TO_PAGE),
                contact.actions().stream().map(FormsAction::type).toList());
        assertTrue(contact.settings().displaysCaptcha());
        assertTrue(contact.settings().tracksUsers());
        assertFalse(contact.settings().savable());
        assertFalse(contact.settings().constrained());
    }

    @Test
    void aFieldCarriesItsTitlesOptionsAndRules() throws Exception {
        FormsForm contact = sampleReader().readStructure().forms().get("contact-us");

        FormsField firstName = contact.fields().get(0);
        assertEquals("fcnt:inputDefinition", firstName.type());
        assertEquals("input", firstName.kind());
        assertEquals("", firstName.titles().get("en"));
        assertEquals("Your First name*", firstName.option("placeholder").in("en"));
        assertEquals("Votre prénom*", firstName.option("placeholder").in("fr"));
        assertTrue(firstName.option("helptext").isBlank());
        assertTrue(firstName.prefilled());
        assertFalse(firstName.hasLogic());

        FormsField enquiry = contact.fields().get(4);
        assertEquals("Your Enquiry", enquiry.titles().get("en"));
        assertEquals("Votre demande", enquiry.titles().get("fr"));
        assertEquals("5", enquiry.option("rows").in("en"));

        FormsField email = contact.fields().get(2);
        assertTrue(email.validation(FormsValidation.EMAIL).isPresent());
        assertEquals("Please enter a valid email address", email.validation(FormsValidation.EMAIL).get().messages().get("en"));
        assertTrue(email.validation(FormsValidation.REQUIRED).isEmpty());
    }

    @Test
    void aResultsEntryPointsAtItsFormAndHoldsItsLabels() throws Exception {
        FormsExport export = sampleReader().readStructure();
        FormsResults contact = export.results().get("contact-us");

        assertEquals("#/forms/contact-us", contact.parentFormPath());
        assertEquals("contact-us", contact.parentFormName());
        assertEquals("en", contact.buildingLang());
        assertNotNull(contact.uuid());
        assertEquals(5, contact.labels().size());
        FormsLabel enquiry = contact.label("text-area_0_4");
        assertEquals("56555cf3-abce-49e0-acdc-32e357b83c7e", enquiry.fieldId());
        assertEquals("Your Enquiry", enquiry.labels().get("en"));
        assertEquals("", contact.label("text-input_0_1").labels().get("en"));
        assertFalse(enquiry.hasChoices());

        // the label's fieldId is the jcr:uuid of the field in forms/
        assertEquals("text-area_0_4", export.formOf(contact).fieldById(enquiry.fieldId()).name());
        assertEquals(contact, export.resultsOf(export.forms().get("contact-us")));
    }

    @Test
    void theSubmissionsStreamWithTheirValuesAndDates() throws Exception {
        List<FormsSubmission> submissions = new ArrayList<>();
        sampleReader().readSubmissions(submissions::add);

        assertEquals(106, submissions.size());
        Map<String, Long> byForm = new java.util.TreeMap<>();
        submissions.forEach(s -> byForm.merge(s.formName(), 1L, Long::sum));
        assertEquals(3, byForm.size());
        assertEquals(106L, byForm.values().stream().mapToLong(Long::longValue).sum());

        FormsSubmission first = submissions.stream().filter(s -> s.uuid().equals("2c591198-6092-485b-b936-2a8cbf0213c8")).findFirst().orElseThrow();
        assertEquals("contact-us", first.formName());
        assertEquals(Instant.parse("2023-06-20T13:03:02.614Z"), first.created());
        assertEquals("https://example.com/sites/motor-retail/home.html", first.origin());
        assertEquals("guest", first.createdBy());
        assertNotNull(first.ipAddress());
        assertEquals(5, first.fields().size());
        FormsResultField email = first.field("email-input_0_2");
        assertEquals("email-input_0_2", email.labelName());
        assertTrue(email.value().endsWith("@example.com"));
        assertTrue(email.optional());
        assertTrue(email.files().isEmpty());
        assertTrue(first.path().startsWith("formFactory/results/contact-us/submissions/"));
    }

    @Test
    void aZipWithoutResultsIsRefusedWithTheProcedure() throws Exception {
        String formsOnly = XML_HEAD + "<formFactory " + JCR_NS + " jcr:primaryType=\"fcnt:formFactory\">"
                + "<forms jcr:primaryType=\"fcnt:formsFolder\"><contact-us jcr:primaryType=\"fcnt:form\"/></forms>"
                + "<results jcr:primaryType=\"fcnt:resultsFolder\"/></formFactory>";
        FormsExportReader reader = new FormsExportReader(new FormsExportZip(zipOf(Map.of(FormsExportZip.XML, formsOnly))));

        FormsExportException refused = assertThrows(FormsExportException.class, reader::readStructure);
        assertTrue(refused.getMessage().contains("no results"), refused.getMessage());
        assertTrue(refused.getMessage().contains("Export Zip with live content"), refused.getMessage());
    }

    @Test
    void aFileThatIsNoExportIsRefused() throws Exception {
        FormsExportReader noXml = new FormsExportReader(new FormsExportZip(zipOf(Map.of("readme.txt", "hello"))));
        assertTrue(assertThrows(FormsExportException.class, noXml::readStructure).getMessage().contains("no repository.xml"));

        String page = XML_HEAD + "<home " + JCR_NS + " jcr:primaryType=\"jnt:page\"/>";
        FormsExportReader notForms = new FormsExportReader(new FormsExportZip(zipOf(Map.of(FormsExportZip.XML, page))));
        assertTrue(assertThrows(FormsExportException.class, notForms::readStructure).getMessage().contains("not the export of a formFactory"));
    }

    @Test
    void anExternalEntityIsNotResolved() throws Exception {
        // the reference sits in element content, where XML allows an external entity: only the parser's
        // settings refuse it
        String xxe = "<?xml version=\"1.0\"?><!DOCTYPE formFactory [<!ENTITY xxe SYSTEM \"file:///etc/hostname\">]>"
                + "<formFactory " + JCR_NS + " jcr:primaryType=\"fcnt:formFactory\">"
                + "<results jcr:primaryType=\"fcnt:resultsFolder\"><f jcr:primaryType=\"fcnt:formResults\" parentForm=\"#/forms/f\">"
                + "<labels jcr:primaryType=\"fcnt:resultLabels\"><x jcr:primaryType=\"fcnt:definitionOptionsTranslatable\" fieldId=\"u\">"
                + "&xxe;</x></labels></f></results></formFactory>";
        FormsExportReader reader = new FormsExportReader(new FormsExportZip(zipOf(Map.of(FormsExportZip.XML, xxe))));
        FormsExportException refused = assertThrows(FormsExportException.class, reader::readStructure);
        assertTrue(refused.getMessage().contains("cannot be parsed"), refused.getMessage());
    }

    @Test
    void anEntryIsNotReadPastTheBound() throws Exception {
        String big = "x".repeat(100);
        try (FormsExportZip zip = new FormsExportZip(zipOf(Map.of("big.bin", big)), 10);
             InputStream in = zip.open("big.bin")) {
            assertThrows(IOException.class, in::readAllBytes);
        }
        try (FormsExportZip zip = new FormsExportZip(zipOf(Map.of("big.bin", big)), 100);
             InputStream in = zip.open("big.bin")) {
            assertEquals(100, in.readAllBytes().length);
        }
    }

    /** Jahia encodes the node names and the values of a multi-valued property, not a single value. */
    @Test
    void aSingleValueIsReadAsWrittenAndAMultiValueIsDecoded() throws Exception {
        String xml = XML_HEAD + "<formFactory " + JCR_NS + " xmlns:j=\"http://www.jahia.org/jahia/1.0\" jcr:primaryType=\"fcnt:formFactory\">"
                + "<results jcr:primaryType=\"fcnt:resultsFolder\"><f jcr:primaryType=\"fcnt:formResults\" jcr:uuid=\"r1\" parentForm=\"#/forms/f\">"
                + "<labels jcr:primaryType=\"fcnt:resultLabels\"><x jcr:primaryType=\"fcnt:definitionOptionsTranslatable\" fieldId=\"u\">"
                + "<j:translation_en jcr:primaryType=\"jnt:translation\" label=\"Keep_x0020_me\"/></x></labels>"
                + "<submissions jcr:primaryType=\"fcnt:submissions\"><_x0030_6 jcr:primaryType=\"fcnt:splittedResult\">"
                + "<s1 jcr:primaryType=\"fcnt:result\"><x jcr:primaryType=\"fcnt:resultField\" label=\"#/results/f/labels/x\" result=\"Very_x0020_good Fair\"/></s1>"
                + "</_x0030_6></submissions></f></results></formFactory>";
        FormsExportReader reader = new FormsExportReader(new FormsExportZip(zipOf(Map.of(FormsExportZip.XML, xml))));

        assertEquals("Keep_x0020_me", reader.readStructure().results().get("f").label("x").labels().get("en"));
        List<FormsSubmission> submissions = new ArrayList<>();
        reader.readSubmissions(submissions::add);
        assertEquals(List.of("Very good", "Fair"), submissions.get(0).field("x").values());
        assertEquals("formFactory/results/f/submissions/06/s1", submissions.get(0).path());
    }

    /**
     * A reference is the one single value Jahia encodes (DocumentViewExporter writes it through
     * JCRMultipleValueUtils.encode): a form and a field named with a space keep their results.
     */
    @Test
    void aReferenceIsDecodedSoThatASpacedNameKeepsItsResults() throws Exception {
        String xml = XML_HEAD + "<formFactory " + JCR_NS + " xmlns:j=\"http://www.jahia.org/jahia/1.0\" jcr:primaryType=\"fcnt:formFactory\">"
                + "<results jcr:primaryType=\"fcnt:resultsFolder\"><my_x0020_form jcr:primaryType=\"fcnt:formResults\" jcr:uuid=\"r1\" parentForm=\"#/forms/my_x0020_form\" buildingLang=\"en\">"
                + "<labels jcr:primaryType=\"fcnt:resultLabels\"><first_x0020_name jcr:primaryType=\"fcnt:definitionOptionsTranslatable\" fieldId=\"f1\">"
                + "<j:translation_en jcr:primaryType=\"jnt:translation\" label=\"First name\"/></first_x0020_name></labels>"
                + "<submissions jcr:primaryType=\"fcnt:submissions\"><s1 jcr:primaryType=\"fcnt:result\">"
                + "<first_x0020_name jcr:primaryType=\"fcnt:resultField\" label=\"#/results/my_x0020_form/labels/first_x0020_name\" result=\"Jane\"/></s1>"
                + "</submissions></my_x0020_form></results>"
                + "<forms jcr:primaryType=\"fcnt:formsFolder\"><my_x0020_form jcr:primaryType=\"fcnt:form\" jcr:uuid=\"f0\" buildingLang=\"en\">"
                + "<step-1 jcr:primaryType=\"fcnt:step\" stepNumber=\"1\"><first_x0020_name jcr:primaryType=\"fcnt:inputDefinition\" jcr:uuid=\"f1\"/></step-1>"
                + "</my_x0020_form></forms></formFactory>";
        FormsExportReader reader = new FormsExportReader(new FormsExportZip(zipOf(Map.of(FormsExportZip.XML, xml))));

        FormsExport export = reader.readStructure();
        FormsResults results = export.results().get("my form");
        assertEquals("my form", results.parentFormName());
        assertEquals("my form", export.formOf(results).name());
        List<FormsSubmission> submissions = new ArrayList<>();
        reader.readSubmissions(submissions::add);
        FormsResultField answer = submissions.get(0).fields().get(0);
        assertEquals("first name", answer.name());
        assertEquals("first name", answer.labelName());
        assertEquals("f1", results.label(answer.labelName()).fieldId());
        assertEquals("my form", submissions.get(0).formName());
    }

    @Test
    void theLiveXmlWinsOverTheEditOne() throws Exception {
        String edit = XML_HEAD + "<formFactory " + JCR_NS + " jcr:primaryType=\"fcnt:formFactory\">"
                + "<results jcr:primaryType=\"fcnt:resultsFolder\"/></formFactory>";
        String live = XML_HEAD + "<formFactory " + JCR_NS + " jcr:primaryType=\"fcnt:formFactory\">"
                + "<results jcr:primaryType=\"fcnt:resultsFolder\"><f jcr:primaryType=\"fcnt:formResults\" parentForm=\"#/forms/f\"/></results></formFactory>";
        FormsExportZip zip = new FormsExportZip(zipOf(Map.of(FormsExportZip.XML, edit, FormsExportZip.LIVE_XML, live)));

        assertTrue(zip.hasLiveXml());
        assertEquals(1, new FormsExportReader(zip).readStructure().results().size());
    }

    @Test
    void aBinaryIsFoundWhereJahiaWritesIt() throws Exception {
        // DocumentViewExporter.buildBinaryPathInZip: <path of the file node, relative to the parent of the
        // exported node>/<file name>, under live-content/ for the live workspace
        String node = "formFactory/results/contact-us/submissions/06/20/x/cv/cv.pdf";
        FormsExportZip zip = new FormsExportZip(zipOf(Map.of("live-content/" + node + "/cv.pdf", "PDF")));

        try (InputStream found = zip.openBinary(node, "cv.pdf")) {
            assertEquals("PDF", new String(found.readAllBytes(), StandardCharsets.UTF_8));
        }
        assertNull(zip.openBinary(node, "other.pdf"));

        FormsExportZip editOnly = new FormsExportZip(zipOf(Map.of("content/" + node + "/cv.pdf", "PDF")));
        assertNotNull(editOnly.openBinary(node, "cv.pdf"));
    }

    /** A zip of text entries, written to a temporary file. */
    static Path zipOf(Map<String, String> entries) throws IOException {
        Path file = Files.createTempFile("forms-export", ".zip");
        file.toFile().deleteOnExit();
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(file))) {
            for (Map.Entry<String, String> entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return file;
    }
}
