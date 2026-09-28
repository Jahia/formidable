package org.jahia.modules.formidable.engine.files;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileTypeServiceTest {

    private final FileTypeService types = new FileTypeService(() -> Set.of("image/png", "image/jpeg", "application/pdf", "video/mp4"));

    @Test
    void aMimeTypeShowsItsPreferredExtensionAndIsRecognisedByAllOfThem() {
        // Verifies the two readings of Tika's registry: the extension shown to a visitor, and every extension a
        // file of that type may carry, for the check made when the browser gives no type.
        assertArrayEquals(new String[] {".docx"}, types.shownExtensions("application/vnd.openxmlformats-officedocument.wordprocessingml.document"));
        assertArrayEquals(new String[] {".jpg"}, types.shownExtensions(" IMAGE/JPEG "));
        assertTrue(List.of(types.recognisedExtensions("image/jpeg")).containsAll(List.of(".jpg", ".jpeg")));
    }

    @Test
    void aWildcardCoversTheAllowedTypesItNamesAndAnExtensionIsItself() {
        // Verifies the two other tokens: a wildcard reads the allowed types (not every image Tika knows), sorted; an
        // extension is shown and recognised as it is; an unknown type gives nothing, and the caller stays permissive.
        assertArrayEquals(new String[] {".jpg", ".png"}, types.shownExtensions("image/*"));
        assertArrayEquals(new String[] {".csv"}, types.recognisedExtensions(".CSV"));
        assertArrayEquals(new String[0], types.shownExtensions("application/x-not-a-type"));
        assertArrayEquals(new String[0], types.shownExtensions(""));
    }

    @Test
    void theDefaultLabelIsTheAcronymOrTheExtensionFollowedByTheExtension() {
        // Verifies the label a type gets without a bundle entry: readable for the types Tika describes badly or not
        // at all (webp, mp4, docx), and the subtype for a type Tika does not know.
        assertEquals("PDF (.pdf)", types.label("application/pdf"));
        assertEquals("JPEG (.jpg)", types.label("image/jpeg"));
        assertEquals("WEBP (.webp)", types.label("image/webp"));
        assertEquals("MP4 (.mp4)", types.label("video/mp4"));
        assertEquals("DOCX (.docx)", types.label("application/vnd.openxmlformats-officedocument.wordprocessingml.document"));
        assertEquals("X-NOT-A-TYPE", types.label("application/x-not-a-type"));
    }
}
