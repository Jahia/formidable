package org.jahia.modules.formidable.content;

import org.jahia.modules.formidable.content.names.NodeNames;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NodeNamesTest {

    @Test
    void aTitleBecomesALowerCaseAsciiSlug() {
        assertEquals("summer-launch-2026", NodeNames.slug("Summer Launch 2026!", "x"));
        assertEquals("cafe-creme", NodeNames.slug("  Caf\u00e9 -- Cr\u00e8me  ", "x"));
        assertEquals("a-b", NodeNames.slug("a/b", "x"));
    }

    @Test
    void nothingThatCanBreakAPathSurvives() {
        for (String hostile : new String[]{"../../etc/passwd", "a:b[1]|*", "x\u0000y", "jcr:content", "<script>", "a\\b", "..", "."}) {
            String slug = NodeNames.slug(hostile, "fallback");
            assertTrue(slug.matches("[a-z0-9]+(-[a-z0-9]+)*"), hostile + " -> " + slug);
        }
        assertEquals("fallback", NodeNames.slug("...", "fallback"));
        assertEquals("fallback", NodeNames.slug(null, "fallback"));
        assertEquals("fallback", NodeNames.slug("\u4e2d\u6587", "fallback"));
    }

    @Test
    void aLongTitleIsCutAtTheLimitWithoutATrailingHyphen() {
        String slug = NodeNames.slug("word ".repeat(100), "x");
        assertTrue(slug.length() <= NodeNames.MAX_LENGTH);
        assertFalse(slug.endsWith("-"));
    }

    @Test
    void aFileNameKeepsItsExtensionInLowerCase() {
        assertEquals("my-video-final.mp4", NodeNames.fileName("My Video (final).MP4"));
        assertEquals("archive-tar.gz", NodeNames.fileName("archive.tar.gz"));
        assertEquals("photo.jpeg", NodeNames.fileName("C:\\Users\\me\\Photo.JPEG"));
        assertEquals("photo.jpeg", NodeNames.fileName("/tmp/uploads/photo.jpeg"));
    }

    @Test
    void aFileNameWithoutAUsableBaseOrExtensionStillGetsAName() {
        assertEquals("file", NodeNames.fileName(null));
        assertEquals("file", NodeNames.fileName(""));
        assertEquals("file.txt", NodeNames.fileName("....txt"));
        assertEquals("readme", NodeNames.fileName("README"));
        assertTrue(NodeNames.fileName(".htaccess").matches("[a-z0-9.-]+"));
        assertEquals("file", NodeNames.fileName("..."));
    }

    @Test
    void aHostileExtensionIsDroppedNotKept() {
        String name = NodeNames.fileName("report.p/h\u0000p");
        assertTrue(name.matches("[a-z0-9.-]+"), name);
        assertTrue(NodeNames.fileName("x." + "a".repeat(40)).length() <= NodeNames.MAX_LENGTH);
        assertEquals("x-" + "a".repeat(40), NodeNames.fileName("x." + "a".repeat(40)));
    }

    @Test
    void aNameIsMadeUniqueWithANumericSuffixBeforeTheExtension() {
        Set<String> taken = new HashSet<>(Set.of("clip.mp4", "clip-1.mp4"));
        assertEquals("clip-2.mp4", NodeNames.unique("clip.mp4", taken::contains));
        assertEquals("other.mp4", NodeNames.unique("other.mp4", taken::contains));
        assertEquals("note-1", NodeNames.unique("note", Set.of("note")::contains));
    }

    @Test
    void whenEveryNumberIsTakenARandomSuffixIsUsed() {
        String name = NodeNames.unique("clip.mp4", candidate -> !candidate.matches("clip-[0-9a-f]{8}\\.mp4"));
        assertTrue(name.matches("clip-[0-9a-f]{8}\\.mp4"), name);
    }

    @Test
    void randomizedKeepsTheExtension() {
        assertTrue(NodeNames.randomized("clip.mp4").matches("clip-[0-9a-f]{8}\\.mp4"));
        assertTrue(NodeNames.randomized("folder").matches("folder-[0-9a-f]{8}"));
    }
}
