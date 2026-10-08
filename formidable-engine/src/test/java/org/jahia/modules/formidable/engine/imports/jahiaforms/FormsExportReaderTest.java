package org.jahia.modules.formidable.engine.imports.jahiaforms;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
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

    static FormsExportReader sampleReader() {
        return new FormsExportReader(new FormsExportZip(name -> FormsExportReaderTest.class.getResourceAsStream(SAMPLE)));
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
        String formsOnly = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<formFactory xmlns:jcr=\"http://www.jcp.org/jcr/1.0\" jcr:primaryType=\"fcnt:formFactory\">"
                + "<forms jcr:primaryType=\"fcnt:formsFolder\"><contact-us jcr:primaryType=\"fcnt:form\"/></forms>"
                + "<results jcr:primaryType=\"fcnt:resultsFolder\"/></formFactory>";
        FormsExportReader reader = new FormsExportReader(new FormsExportZip(name -> zipOf(Map.of(FormsExportZip.XML, formsOnly))));

        FormsExportException refused = assertThrows(FormsExportException.class, reader::readStructure);
        assertTrue(refused.getMessage().contains("no results"), refused.getMessage());
        assertTrue(refused.getMessage().contains("Export Zip with live content"), refused.getMessage());
    }

    @Test
    void aFileThatIsNoExportIsRefused() {
        FormsExportReader noXml = new FormsExportReader(new FormsExportZip(name -> zipOf(Map.of("readme.txt", "hello"))));
        assertTrue(assertThrows(FormsExportException.class, noXml::readStructure).getMessage().contains("no repository.xml"));

        String page = "<?xml version=\"1.0\"?><home xmlns:jcr=\"http://www.jcp.org/jcr/1.0\" jcr:primaryType=\"jnt:page\"/>";
        FormsExportReader notForms = new FormsExportReader(new FormsExportZip(name -> zipOf(Map.of(FormsExportZip.XML, page))));
        assertTrue(assertThrows(FormsExportException.class, notForms::readStructure).getMessage().contains("not the export of a formFactory"));
    }

    @Test
    void anExternalEntityIsNotResolved() {
        String xxe = "<?xml version=\"1.0\"?><!DOCTYPE formFactory [<!ENTITY xxe SYSTEM \"file:///etc/hostname\">]>"
                + "<formFactory xmlns:jcr=\"http://www.jcp.org/jcr/1.0\" jcr:primaryType=\"fcnt:formFactory\" jcr:title=\"&xxe;\">"
                + "<results jcr:primaryType=\"fcnt:resultsFolder\"/></formFactory>";
        FormsExportReader reader = new FormsExportReader(new FormsExportZip(name -> zipOf(Map.of(FormsExportZip.XML, xxe))));
        FormsExportException refused = assertThrows(FormsExportException.class, reader::readStructure);
        assertTrue(refused.getMessage().contains("cannot be parsed"), refused.getMessage());
    }

    @Test
    void theLiveXmlWinsOverTheEditOne() throws Exception {
        String edit = "<?xml version=\"1.0\"?><formFactory xmlns:jcr=\"http://www.jcp.org/jcr/1.0\" jcr:primaryType=\"fcnt:formFactory\">"
                + "<results jcr:primaryType=\"fcnt:resultsFolder\"/></formFactory>";
        String live = "<?xml version=\"1.0\"?><formFactory xmlns:jcr=\"http://www.jcp.org/jcr/1.0\" jcr:primaryType=\"fcnt:formFactory\">"
                + "<results jcr:primaryType=\"fcnt:resultsFolder\"><f jcr:primaryType=\"fcnt:formResults\" parentForm=\"#/forms/f\"/></results></formFactory>";
        FormsExportZip zip = new FormsExportZip(name -> zipOf(Map.of(FormsExportZip.XML, edit, FormsExportZip.LIVE_XML, live)));

        assertTrue(zip.hasLiveXml());
        assertEquals(1, new FormsExportReader(zip).readStructure().results().size());
    }

    @Test
    void aBinaryIsFoundByTheTailOfItsPath() throws Exception {
        FormsExportZip zip = new FormsExportZip(name -> zipOf(Map.of(
                "live-content/sites/motor-retail/formFactory/results/contact-us/submissions/06/20/x/cv/cv.pdf", "PDF")));

        try (InputStream found = zip.openBinary("formFactory/results/contact-us/submissions/06/20/x/cv/cv.pdf")) {
            assertEquals("PDF", new String(found.readAllBytes(), StandardCharsets.UTF_8));
        }
        assertNull(zip.openBinary("formFactory/results/contact-us/submissions/06/20/x/cv/other.pdf"));
    }

    static InputStream zipOf(Map<String, String> entries) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            for (Map.Entry<String, String> entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return new ByteArrayInputStream(bytes.toByteArray());
    }
}
