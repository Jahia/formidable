package org.jahia.modules.formidable.engine.config.uploads;

import org.jahia.modules.formidable.engine.config.TestConfigs;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
    void activateParsesAndTrimsConfiguredUploadMimeTypes() {
        // Verifies parsing of the fallback upload MIME allowlist: blank entries are removed and the rest trimmed.
        UploadsConfigService service = activated(Map.of("uploadAllowedMimeTypes", " text/plain , application/pdf ,, image/png "));

        assertEquals(Set.of("text/plain", "application/pdf", "image/png"), service.getUploadAllowedMimeTypes());
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
