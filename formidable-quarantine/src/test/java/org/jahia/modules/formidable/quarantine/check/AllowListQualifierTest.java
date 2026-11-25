package org.jahia.modules.formidable.quarantine.check;

import org.jahia.modules.formidable.quarantine.api.CheckSettings;
import org.jahia.modules.formidable.quarantine.api.OnInconclusive;
import org.jahia.modules.formidable.quarantine.api.OnUnavailable;
import org.jahia.modules.formidable.quarantine.api.Outcome;
import org.jahia.modules.formidable.quarantine.api.QuarantinedFile;
import org.jahia.modules.formidable.quarantine.api.Scope;
import org.jahia.modules.formidable.quarantine.api.Submitted;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The allow-list check: both lists must allow a file, case aside, and it never reads a byte. */
class AllowListQualifierTest {

    private final AllowListQualifier allowList = new AllowListQualifier();

    /** A file whose bytes the check must never read. */
    private record MetadataFile(String fileName, String mimeType) implements QuarantinedFile {
        @Override
        public String sha256() {
            return "aaa";
        }

        @Override
        public long size() {
            return 42;
        }

        @Override
        public InputStream open() {
            throw new AssertionError("The allow-list check read a byte");
        }
    }

    private static CheckSettings lists(String extensions, String mimeTypes) {
        Map<String, String> properties = new LinkedHashMap<>();
        if (extensions != null) {
            properties.put(AllowListQualifier.EXTENSIONS, extensions);
        }
        if (mimeTypes != null) {
            properties.put(AllowListQualifier.MIME_TYPES, mimeTypes);
        }
        return new CheckSettings(AllowListQualifier.NODE_TYPE, "allowList", OnInconclusive.REVIEW,
                OnUnavailable.RETRY, properties);
    }

    private Outcome answer(String fileName, String mimeType, CheckSettings settings) {
        Submitted submitted = allowList.submit(new MetadataFile(fileName, mimeType), settings);
        return ((Submitted.Answered) submitted).outcome();
    }

    private static final CheckSettings PDF_AND_PNG = lists("pdf,png", "application/pdf,image/png");

    @Test
    void itIsAMetadataCheckThatAppliesToEveryFile() {
        assertEquals(Scope.METADATA, allowList.scope());
        assertEquals("fmdbq:allowListCheck", allowList.getNodeType());
        assertTrue(allowList.appliesTo(new MetadataFile("run.exe", "application/x-msdownload"), PDF_AND_PNG));
    }

    @Test
    void aFileWhoseExtensionAndTypeAreBothListedPasses() {
        assertEquals(Outcome.pass(AllowListQualifier.ALLOWED, null),
                answer("claim.pdf", "application/pdf", PDF_AND_PNG));
    }

    @Test
    void caseAndTheParametersOfTheTypeDoNotMatter() {
        assertEquals(Outcome.pass(AllowListQualifier.ALLOWED, null),
                answer("SCAN.PNG", "Image/PNG; charset=binary", lists(" .Png , PDF ", "IMAGE/png")));
    }

    @Test
    void anExtensionOffTheListFails() {
        assertEquals(Outcome.fail(AllowListQualifier.EXTENSION_REFUSED, null),
                answer("run.exe", "application/pdf", PDF_AND_PNG));
    }

    @Test
    void aTypeOffTheListFailsEvenWithAnAllowedExtension() {
        assertEquals(Outcome.fail(AllowListQualifier.MIME_TYPE_REFUSED, null),
                answer("claim.pdf", "text/html", PDF_AND_PNG));
    }

    @Test
    void aNameWithNoExtensionOrAFileWithNoTypeFails() {
        assertEquals(Outcome.fail(AllowListQualifier.EXTENSION_REFUSED, null),
                answer("README", "application/pdf", PDF_AND_PNG));
        assertEquals(Outcome.fail(AllowListQualifier.EXTENSION_REFUSED, null),
                answer("claim.", "application/pdf", PDF_AND_PNG));
        assertEquals(Outcome.fail(AllowListQualifier.MIME_TYPE_REFUSED, null),
                answer("claim.pdf", null, PDF_AND_PNG));
        assertEquals(Outcome.fail(AllowListQualifier.MIME_TYPE_REFUSED, null),
                answer("claim.pdf", " ", PDF_AND_PNG));
    }

    @Test
    void anEmptyListAllowsNothing() {
        assertEquals(Outcome.fail(AllowListQualifier.EXTENSION_REFUSED, null),
                answer("claim.pdf", "application/pdf", lists(null, "application/pdf")));
        assertEquals(Outcome.fail(AllowListQualifier.EXTENSION_REFUSED, null),
                answer("claim.pdf", "application/pdf", lists("", "application/pdf")));
        assertEquals(Outcome.fail(AllowListQualifier.MIME_TYPE_REFUSED, null),
                answer("claim.pdf", "application/pdf", lists("pdf", null)));
    }

    @Test
    void onlyTheLastExtensionCounts() {
        assertEquals(Outcome.fail(AllowListQualifier.EXTENSION_REFUSED, null),
                answer("claim.pdf.exe", "application/pdf", PDF_AND_PNG));
    }

    @Test
    void aPollFailsForGood() {
        assertEquals(new Outcome.Failed(false, AllowListQualifier.NO_TICKET), allowList.poll("t-1"));
    }

    @Test
    void theHelpersNormalizeWhatTheyRead() {
        assertEquals("pdf", AllowListQualifier.extensionOf("a.b.PDF"));
        assertNull(AllowListQualifier.extensionOf(null));
        assertEquals("text/plain", AllowListQualifier.essenceOf(" Text/Plain ;charset=UTF-8"));
        assertEquals(Set.of("pdf", "png"), AllowListQualifier.listOf(lists(".pdf,,PNG", null),
                AllowListQualifier.EXTENSIONS));
    }
}
