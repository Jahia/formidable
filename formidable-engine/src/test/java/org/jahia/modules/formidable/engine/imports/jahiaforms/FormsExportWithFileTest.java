package org.jahia.modules.formidable.engine.imports.jahiaforms;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * The reader against a real Forms export holding an uploaded file: the anonymised Q2 export taken again
 * after a visitor sent a PDF through the file field "Your Id" of contact-us (3 forms, 109 submissions).
 */
class FormsExportWithFileTest {

    static final String WITH_FILE = "/imports/jahiaforms/formFactory-with-file.zip";
    private static final String SUBMISSION = "2dd1fe78-e095-4f47-ab08-b5c30df47a4b";
    private static final String FILE_FIELD = "file-upload_0_6";

    static FormsExportReader withFileReader() throws Exception {
        return FormsExportReader.open(FormsExportReaderTest.resourceZip(WITH_FILE));
    }

    @Test
    void theFileFieldIsReadAsAFileField() throws Exception {
        FormsForm contact = withFileReader().readStructure().forms().get("contact-us");

        FormsField upload = contact.fields().stream().filter(f -> f.name().equals(FILE_FIELD)).findFirst().orElseThrow();
        assertEquals("fcnt:fileUploadDefinition", upload.type());
        assertEquals("Your Id", upload.titles().get("en"));
        assertEquals(FormsFieldTypes.INPUT_FILE, FormsFieldTypes.of(upload, type -> true).nodeType());
    }

    @Test
    void theSubmissionCarriesTheFileAndTheZipHoldsItsBinary() throws Exception {
        FormsExportReader reader = withFileReader();
        List<FormsSubmission> submissions = new ArrayList<>();
        reader.readSubmissions(submissions::add);

        assertEquals(109, submissions.size());
        FormsSubmission withFile = submissions.stream().filter(s -> s.uuid().equals(SUBMISSION)).findFirst().orElseThrow();
        FormsResultField answer = withFile.field(FILE_FIELD);
        assertEquals(1, answer.files().size());
        FormsFile file = answer.files().get(0);
        assertEquals("document.pdf", file.name());
        assertEquals("application/pdf", file.mimeType());
        // the node path decodes the split folders (_x0031_0 → 10) to match the entry Jahia writes in the zip
        assertEquals("formFactory/results/contact-us/submissions/10/13/09/19/32/" + SUBMISSION + "/" + FILE_FIELD + "/document.pdf",
                file.path());

        assertEquals(578, reader.binarySize(file));
        try (InputStream binary = reader.openBinary(file)) {
            assertNotNull(binary);
            byte[] bytes = binary.readAllBytes();
            assertEquals(578, bytes.length);
            assertArrayEquals("%PDF".getBytes(StandardCharsets.US_ASCII), java.util.Arrays.copyOf(bytes, 4));
        }
    }
}
