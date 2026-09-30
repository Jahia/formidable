package org.jahia.modules.formidable.engine.config.uploads;

import org.apache.tika.Tika;
import org.jahia.modules.formidable.engine.config.TestConfigs;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UploadsConfigServiceTest {

    private static UploadsConfigService activated(Map<String, Object> values) {
        UploadsConfigService service = new UploadsConfigService();
        service.activate(TestConfigs.of(UploadsConfig.class, values));
        return service;
    }

    @Test
    void theShippedFileAgreesWithTheDefinitionsDefaults() throws Exception {
        TestConfigs.assertShippedFileMatchesDefaults(UploadsConfig.class, UploadsConfigService.PID);
    }

    @Test
    void theAllowedTypesAreReadAsMimeTypesWhateverTheirSpelling() {
        // Verifies the reading of uploadAllowedTypes: blank entries removed, the rest trimmed and lower-cased, an
        // extension (with or without its dot) turned into its MIME type, a wildcard kept, a token that is no file type
        // dropped — so that everything downstream compares MIME types.
        UploadsConfigService service = activated(Map.of("uploadAllowedTypes", " Text/Plain , pdf ,, .DOCX, image/*, not a type "));

        assertEquals(List.of("text/plain", "application/pdf",
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "image/*"),
                List.copyOf(service.getUploadAllowedTypes()));
    }

    @Test
    void theDefaultTypesAreTheSeventeenOfBefore() {
        // Verifies that the default written as extensions stands for the same seventeen MIME types the former default
        // listed — each extension resolves to the type it replaced.
        UploadsConfigService service = activated(Map.of());

        assertEquals(Set.of("image/jpeg", "image/png", "image/gif", "image/webp", "application/pdf", "application/msword",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "application/vnd.ms-excel",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "application/vnd.oasis.opendocument.text",
                "application/vnd.oasis.opendocument.spreadsheet", "text/plain", "text/csv", "video/mp4", "video/webm",
                "video/ogg", "video/x-matroska"), service.getUploadAllowedTypes());
    }

    @Test
    void anEmptyListAllowsNoType() {
        UploadsConfigService service = activated(Map.of("uploadAllowedTypes", " , "));

        assertTrue(service.getUploadAllowedTypes().isEmpty());
    }

    @Test
    void theShippedFileAndTheConsoleLinkTheRegistryOfTheEmbeddedTika() throws Exception {
        // Verifies that the link the administrator follows to see what an extension stands for — in the console's
        // description, built on TIKA_REGISTRY, and in the shipped file — is the registry of the Tika this module runs:
        // a Tika upgrade without the link's fails here.
        String version = new Tika().toString().replace("Apache Tika ", "").trim();
        assertEquals(UploadsConfig.TIKA_REGISTRY,
                "https://github.com/apache/tika/blob/" + version + "/tika-core/src/main/resources/org/apache/tika/mime/tika-mimetypes.xml");
        try (InputStream file = getClass().getResourceAsStream("/META-INF/configurations/" + UploadsConfigService.PID + ".cfg")) {
            assertNotNull(file);
            String content = new String(file.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(content.contains(UploadsConfig.TIKA_REGISTRY), "the shipped file links Tika " + version);
        }
    }

    @Test
    void theFormerNameOnTheThemesPidIsNamedInAWarning() {
        // Verifies that a provisioning script moved to the new PID but still writing uploadAllowedMimeTypes is told:
        // the value is not read — the default stays in force — and the log says which setting to write instead.
        PrintStream previous = System.err;
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        System.setErr(new PrintStream(captured, true, StandardCharsets.UTF_8));
        UploadsConfigService service = new UploadsConfigService();
        try {
            service.configure(TestConfigs.of(UploadsConfig.class, Map.of()), Map.of("uploadAllowedMimeTypes", "application/pdf"));
        } finally {
            System.setErr(previous);
        }

        String logged = captured.toString(StandardCharsets.UTF_8);
        assertTrue(logged.contains("uploadAllowedMimeTypes is no longer read: the setting is uploadAllowedTypes"), logged);
        assertEquals(17, service.getUploadAllowedTypes().size());
    }

    @Test
    void activateExposesTheConfiguredBoundsAndFallsBackOnAZeroOrNegativeOne() {
        // Verifies the three bounds and their guard: a zero or negative limit is a mistake, not a way to lift the
        // cap — the early Content-Length guard compares against these values — so the default applies.
        UploadsConfigService configured = activated(Map.of("uploadMaxFileSizeBytes", 1024L, "uploadMaxRequestSizeBytes", 4096L, "uploadMaxFileCount", 3));
        assertEquals(1024L, configured.getUploadMaxFileSizeBytes());
        assertEquals(4096L, configured.getUploadMaxRequestSizeBytes());
        assertEquals(3, configured.getUploadMaxFileCount());

        UploadsConfigService broken = activated(Map.of("uploadMaxFileSizeBytes", 0L, "uploadMaxRequestSizeBytes", -1L, "uploadMaxFileCount", 0));
        assertEquals(UploadsConfig.DEFAULT_UPLOAD_MAX_FILE_SIZE_BYTES, broken.getUploadMaxFileSizeBytes());
        assertEquals(UploadsConfig.DEFAULT_UPLOAD_MAX_REQUEST_SIZE_BYTES, broken.getUploadMaxRequestSizeBytes());
        assertEquals(UploadsConfig.DEFAULT_UPLOAD_MAX_FILE_COUNT, broken.getUploadMaxFileCount());
    }
}
