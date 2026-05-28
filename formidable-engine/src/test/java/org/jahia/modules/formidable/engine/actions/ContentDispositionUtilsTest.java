package org.jahia.modules.formidable.engine.actions;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ContentDispositionUtilsTest {

    @Test
    void escapeFormFieldNameEncodesQuotesAndStripsCrlf() {
        // Verifies multipart field-name hardening for forwarded Content-Disposition headers.
        String escaped = ContentDispositionUtils.escapeFormFieldName("field\"\r\nX-Test: yes");

        // Expected outcome: quotes are percent-encoded and CR/LF are removed.
        assertEquals("field%22X-Test: yes", escaped);
    }

    @Test
    void escapeFormFieldNameFallsBackWhenBlank() {
        // Verifies the fallback used when the field name is absent or becomes blank after sanitization.
        assertEquals("field", ContentDispositionUtils.escapeFormFieldName(null));
        assertEquals("field", ContentDispositionUtils.escapeFormFieldName(" \r\n "));
    }
}
