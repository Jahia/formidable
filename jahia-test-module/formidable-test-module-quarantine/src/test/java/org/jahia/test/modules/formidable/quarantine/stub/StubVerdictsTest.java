package org.jahia.test.modules.formidable.quarantine.stub;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StubVerdictsTest {

    @Test
    void eachMarkerReadsAsItsVerdict() {
        assertEquals(new StubVerdict.Pass(), StubVerdicts.parse("VERDICT:PASS"));
        assertEquals(new StubVerdict.Fail(), StubVerdicts.parse("VERDICT:FAIL"));
        assertEquals(new StubVerdict.Inconclusive(), StubVerdicts.parse("INCONCLUSIVE"));
        assertEquals(new StubVerdict.Pending(3), StubVerdicts.parse("PENDING:3"));
        assertEquals(new StubVerdict.Pending(0), StubVerdicts.parse("PENDING:0"));
        assertEquals(new StubVerdict.Down(), StubVerdicts.parse("DOWN"));
    }

    @Test
    void theFirstMarkerLineWins() {
        assertEquals(new StubVerdict.Fail(), StubVerdicts.parse("a claim\r\n  VERDICT:FAIL  \nVERDICT:PASS\n"));
    }

    @Test
    void aMarkerInsideALineIsNoMarker() {
        assertEquals(new StubVerdict.Unmarked(), StubVerdicts.parse("say VERDICT:PASS please"));
        assertEquals(new StubVerdict.Unmarked(), StubVerdicts.parse("verdict:pass"));
    }

    @Test
    void aFileWithNoMarkerIsUnmarked() {
        assertEquals(new StubVerdict.Unmarked(), StubVerdicts.parse("hello"));
        assertEquals(new StubVerdict.Unmarked(), StubVerdicts.parse(""));
        assertEquals(new StubVerdict.Unmarked(), StubVerdicts.parse(null));
    }

    @Test
    void aMalformedOrTooLongPendingIsNoMarker() {
        assertEquals(new StubVerdict.Unmarked(), StubVerdicts.parse("PENDING:"));
        assertEquals(new StubVerdict.Unmarked(), StubVerdicts.parse("PENDING:-1"));
        assertEquals(new StubVerdict.Unmarked(), StubVerdicts.parse("PENDING:x"));
        assertEquals(new StubVerdict.Unmarked(), StubVerdicts.parse("PENDING:1001"));
        assertEquals(new StubVerdict.Unmarked(), StubVerdicts.parse("PENDING:99999"));
        assertEquals(new StubVerdict.Pending(1000), StubVerdicts.parse("PENDING:1000"));
    }

    @Test
    void theStubReadsTheHeadOfTheFileOnly() throws IOException {
        String text = "a".repeat(StubVerdicts.MAX_BYTES) + "\nVERDICT:FAIL\n";
        assertEquals(new StubVerdict.Unmarked(),
                StubVerdicts.read(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8))));
        assertEquals(new StubVerdict.Pass(),
                StubVerdicts.read(new ByteArrayInputStream("VERDICT:PASS".getBytes(StandardCharsets.UTF_8))));
    }

    @Test
    void theStubsApplyToTextFilesAlone() {
        assertTrue(StubVerdicts.appliesTo("text/plain"));
        assertTrue(StubVerdicts.appliesTo("Text/Plain; charset=UTF-8"));
        assertFalse(StubVerdicts.appliesTo("image/png"));
        assertFalse(StubVerdicts.appliesTo("text/html"));
        assertFalse(StubVerdicts.appliesTo("application/pdf"));
        assertFalse(StubVerdicts.appliesTo(""));
        assertFalse(StubVerdicts.appliesTo(null));
    }
}
