package org.jahia.modules.formidable.engine.files;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AllowedTypesTest {

    /** Allowed types in an order of their own, so that a result's order is the field's, not the list's. */
    private static Set<String> allowed(String... types) {
        return new LinkedHashSet<>(Arrays.asList(types));
    }

    @Test
    void aTokenResolvesToTheMimeTypeItStandsFor() {
        // Verifies the three spellings a token may take, trimmed and lower-cased: a MIME type and a wildcard as they
        // are, an extension — with or without its dot — as the MIME type Tika's registry gives it.
        assertEquals(Optional.of("application/pdf"), AllowedTypes.resolve(" Application/PDF "));
        assertEquals(Optional.of("image/*"), AllowedTypes.resolve(" image/* "));
        assertEquals(Optional.of("application/vnd.oasis.opendocument.text"), AllowedTypes.resolve("odt"));
        assertEquals(Optional.of("text/csv"), AllowedTypes.resolve(" .CSV "));
        assertEquals(Optional.of("video/ogg"), AllowedTypes.resolve("ogv"));
    }

    @Test
    void anAliasResolvesToTheTypeTikaDetects() {
        // Verifies that a MIME alias lets through the files it names: Tika detects a real WAV as audio/vnd.wave, so
        // audio/x-wav kept as written would match none while the editor and the island, which resolve aliases, offer it.
        assertEquals(Optional.of("audio/vnd.wave"), AllowedTypes.resolve("audio/x-wav"));
        assertEquals(Optional.of("application/xml"), AllowedTypes.resolve("text/xml"));
        assertEquals(Optional.of("application/zip"), AllowedTypes.resolve("application/x-zip-compressed"));
        assertEquals(Optional.of("application/x-custom"), AllowedTypes.resolve("application/x-custom"));
        assertTrue(AllowedTypes.forField(List.of("application/x-pdf"), Set.of("application/pdf"), "cv").contains("application/pdf"));
    }

    @Test
    void aTokenThatIsNoFileTypeResolvesToNothing() {
        // Verifies what is dropped rather than read as some type: blank, malformed, an extension Tika does not know
        // (Tika answers application/octet-stream for it, which must not become an allowed type).
        assertEquals(Optional.empty(), AllowedTypes.resolve(null));
        assertEquals(Optional.empty(), AllowedTypes.resolve("  "));
        assertEquals(Optional.empty(), AllowedTypes.resolve("not a type"));
        assertEquals(Optional.empty(), AllowedTypes.resolve("image/"));
        assertEquals(Optional.empty(), AllowedTypes.resolve("qqqqzz"));
    }

    @Test
    void aFieldWithoutTypesAcceptsTheWholeList() {
        Set<String> list = allowed("application/pdf", "image/png");

        assertEquals(List.of("application/pdf", "image/png"), List.copyOf(AllowedTypes.forField(null, list, "cv")));
        assertEquals(List.of("application/pdf", "image/png"), List.copyOf(AllowedTypes.forField(List.of(" "), list, "cv")));
    }

    @Test
    void aFieldKeepsOnlyTheTypesTheListStillAllows() {
        // Verifies the restriction: a type the administrator removed from the list is no longer honoured, whatever
        // the content still says; the others keep the field's order; an extension is compared as its MIME type.
        Set<String> list = allowed("image/png", "application/pdf");

        assertEquals(List.of("application/pdf", "image/png"),
                List.copyOf(AllowedTypes.forField(List.of("pdf", "application/msword", "image/png"), list, "cv")));
    }

    @Test
    void aWildcardIsKeptWhenAllowedAsSuchElseNarrowedToTheTypesItCovers() {
        assertEquals(List.of("image/*"), List.copyOf(AllowedTypes.forField(List.of("image/*"), allowed("image/*"), "photo")));
        assertEquals(List.of("image/jpeg", "image/png"),
                List.copyOf(AllowedTypes.forField(List.of("image/*"), allowed("image/png", "application/pdf", "image/jpeg"), "photo")));
        assertEquals(List.of(), List.copyOf(AllowedTypes.forField(List.of("video/*"), allowed("image/png"), "photo")));
    }

    @Test
    void noAllowedTypeLetsNoFileThrough() {
        // Verifies the decision taken with the product owner: an empty list refuses every file, it never means
        // "anything goes" — with or without types on the field.
        assertTrue(AllowedTypes.forField(List.of(), Set.of(), "cv").isEmpty());
        assertTrue(AllowedTypes.forField(List.of("application/pdf"), Set.of(), "cv").isEmpty());
        assertFalse(AllowedTypes.permits("application/pdf", Set.of()));
    }

    @Test
    void aTypeIsPermittedWhenListedOrCoveredByAWildcard() {
        assertTrue(AllowedTypes.permits("application/pdf", Set.of("application/pdf")));
        assertTrue(AllowedTypes.permits("image/png", Set.of("image/*")));
        // No parent type: text/plain does not let text/csv through, nor the reverse — image/svg+xml descends from text/plain
        // in Tika's registry, and allowing txt must not allow SVG.
        assertFalse(AllowedTypes.permits("text/plain", Set.of("text/csv")));
        assertFalse(AllowedTypes.permits("text/csv", Set.of("text/plain")));
        assertFalse(AllowedTypes.permits("image/*", Set.of("image/png")));
        assertFalse(AllowedTypes.permits(null, Set.of("image/*")));
    }

    @Test
    void aFileWhoseContentSpecializesTheTypeItsNameStandsForIsPermitted() {
        // Verifies the codec inside a listed container: Tika detects an Ogg video named .ogv as video/theora and an Opus
        // track named .oga as audio/opus, kinds of the video/ogg and audio/ogg the list holds.
        assertTrue(AllowedTypes.permitsFile("video/theora", "clip.ogv", Set.of("video/ogg")));
        assertTrue(AllowedTypes.permitsFile("audio/opus", "track.oga", Set.of("audio/ogg")));
        assertTrue(AllowedTypes.permitsFile("application/java-archive", "bundle.zip", Set.of("application/zip")));
        assertTrue(AllowedTypes.permitsFile("image/png", "photo.bin", Set.of("image/*")));
        assertTrue(AllowedTypes.permitsFile("video/theora", "clip.ogv", Set.of("video/*")));
    }

    @Test
    void theHierarchyNeverWidensTheList() {
        // Verifies the limits: the name's type must be listed itself (a .jar is refused where zip is allowed), share the
        // detected type's top-level type (an SVG named .xml), not be a root (HTML named .txt), and the content must be
        // a kind of it (an MP4 named .ogv); a type the field no longer accepts stays refused.
        assertFalse(AllowedTypes.permitsFile("application/java-archive", "bundle.jar", Set.of("application/zip")));
        assertFalse(AllowedTypes.permitsFile("image/svg+xml", "drawing.xml", Set.of("application/xml")));
        assertFalse(AllowedTypes.permitsFile("text/html", "page.txt", Set.of("text/plain")));
        assertFalse(AllowedTypes.permitsFile("video/mp4", "clip.ogv", Set.of("video/ogg")));
        assertFalse(AllowedTypes.permitsFile("video/theora", "clip.ogv", Set.of("application/pdf")));
        assertFalse(AllowedTypes.permitsFile("video/theora", null, Set.of("video/ogg")));
        assertFalse(AllowedTypes.permitsFile(null, "clip.ogv", Set.of("video/ogg")));
    }
}
