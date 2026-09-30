package org.jahia.modules.formidable.engine.files;

import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRPropertyWrapper;
import org.jahia.services.content.JCRValueWrapper;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FileTypeServiceTest {

    // Inserted out of order on purpose (png before jpeg): a wildcard's extensions come out sorted only if the service
    // sorts them — a Set.of fixture iterates in an order of its own and would let a missing sort through.
    private final FileTypeService types = new FileTypeService(
            () -> new LinkedHashSet<>(Arrays.asList("image/png", "image/jpeg", "application/pdf", "video/mp4")));

    @Test
    void aMimeTypeShowsItsOwnExtensionAndIsRecognisedByAllOfThem() {
        // Verifies the two readings of Tika's registry: the extension shown to a visitor, and every extension a
        // file of that type may carry, for the check made when the browser gives no type.
        assertArrayEquals(new String[] {".docx"}, types.shownExtensions("application/vnd.openxmlformats-officedocument.wordprocessingml.document"));
        assertArrayEquals(new String[] {".jpg"}, types.shownExtensions("image/jpeg"));
        assertTrue(List.of(types.recognisedExtensions("image/jpeg")).containsAll(List.of(".jpg", ".jpeg")));
    }

    @Test
    void theShownExtensionIsTheAcronymsWhenTikasPreferredOneIsNoAbbreviationOfIt() {
        // Verifies the acronym rule: Tika prefers .mpga for audio/mpeg, known as MP3 — .mp3 is shown; it prefers
        // .jpg for image/jpeg, an abbreviation of JPEG — .jpg stays; a type without acronym keeps Tika's (.qt).
        assertArrayEquals(new String[] {".mp3"}, types.shownExtensions("audio/mpeg"));
        assertArrayEquals(new String[] {".mid"}, types.shownExtensions("audio/midi"));
        assertArrayEquals(new String[] {".qt"}, types.shownExtensions("video/quicktime"));
        assertEquals("MP3 (.mp3)", types.label("audio/mpeg"));
    }

    @Test
    void aWildcardCoversTheAllowedTypesItNamesAndAnExtensionIsItself() {
        // Verifies the two other tokens, trimmed: a wildcard reads the allowed types (not every image Tika knows),
        // sorted; an extension is shown and recognised as it is; an unknown type gives nothing.
        assertArrayEquals(new String[] {".jpg", ".png"}, types.shownExtensions(" image/* "));
        assertArrayEquals(new String[] {".csv"}, types.recognisedExtensions(" .CSV "));
        assertArrayEquals(new String[0], types.shownExtensions("application/x-not-a-type"));
        assertArrayEquals(new String[0], types.shownExtensions(""));
    }

    @Test
    void theDefaultLabelIsTheAcronymOrTheExtensionFollowedByTheExtension() {
        // Verifies the label a type gets without a bundle entry: readable for the types Tika describes badly or not
        // at all (webp, mp4, docx), and the whole type for a wildcard or a type Tika does not know — two of them never
        // share a label.
        assertEquals("PDF (.pdf)", types.label("application/pdf"));
        assertEquals("JPEG (.jpg)", types.label("image/jpeg"));
        assertEquals("WEBP (.webp)", types.label("image/webp"));
        assertEquals("MP4 (.mp4)", types.label("video/mp4"));
        assertEquals("DOCX (.docx)", types.label("application/vnd.openxmlformats-officedocument.wordprocessingml.document"));
        assertEquals("application/x-not-a-type", types.label("application/x-not-a-type"));
        assertEquals("image/*", types.label("image/*"));
    }

    @Test
    void aFieldIsOfferedWhatTheListAllows() throws Exception {
        // Verifies what the field's view builds on, read from the node it hands over: its accept values restricted to
        // the allowed types, or all of them when it has none.
        JCRNodeWrapper withAccept = mock(JCRNodeWrapper.class);
        when(withAccept.hasProperty("accept")).thenReturn(true);
        JCRPropertyWrapper accept = mock(JCRPropertyWrapper.class);
        JCRValueWrapper pdf = mock(JCRValueWrapper.class);
        when(pdf.getString()).thenReturn("pdf");
        JCRValueWrapper word = mock(JCRValueWrapper.class);
        when(word.getString()).thenReturn("application/msword");
        when(accept.getValues()).thenReturn(new JCRValueWrapper[] {pdf, word});
        when(withAccept.getProperty("accept")).thenReturn(accept);
        JCRNodeWrapper withoutAccept = mock(JCRNodeWrapper.class);

        assertArrayEquals(new String[] {"application/pdf"}, types.allowedFor(withAccept));
        assertArrayEquals(new String[] {"image/png", "image/jpeg", "application/pdf", "video/mp4"}, types.allowedFor(withoutAccept));
    }

    @Test
    void anyFileGivesNoExtensionToShowAndFailsNothing() {
        // Verifies what the file field's view asks when the list holds */*: the token itself and a wildcard it covers
        // answer no extension — the island then restricts nothing, or shows the wildcard as it is — and nothing throws.
        FileTypeService service = new FileTypeService(() -> Set.of("*/*"));
        assertEquals(0, service.shownExtensions("*/*").length);
        assertEquals(0, service.recognisedExtensions("*/*").length);
        assertEquals(0, service.shownExtensions("image/*").length);
    }
}
