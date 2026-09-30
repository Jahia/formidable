package org.jahia.modules.formidable.engine.files;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
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
        assertTrue(AllowedTypes.permitsFile("image/png", "photo.bin", Set.of("image/*")));
        assertTrue(AllowedTypes.permitsFile("video/theora", "clip.ogv", Set.of("video/*")));
    }

    @Test
    void theHierarchyNeverWidensTheList() {
        // Verifies the limits: audio and video only — an XHTML page named .xml is a kind of what its name stands for in
        // Tika's registry, and stays refused, as would a jar or a macro-enabled workbook named .zip, were a container
        // detector ever to report them —; the name's type must be
        // listed itself, share the detected type's top-level type, and the content must be a kind of it (an MP4 named
        // .ogv); a type the field no longer accepts stays refused.
        assertFalse(AllowedTypes.permitsFile("application/xhtml+xml", "page.xml", Set.of("application/xml")));
        assertFalse(AllowedTypes.permitsFile("application/java-archive", "bundle.zip", Set.of("application/zip")));
        assertFalse(AllowedTypes.permitsFile("application/vnd.ms-excel.sheet.macroenabled.12", "book.zip", Set.of("application/zip")));
        assertFalse(AllowedTypes.permitsFile("application/java-archive", "bundle.jar", Set.of("application/zip")));
        assertFalse(AllowedTypes.permitsFile("image/svg+xml", "drawing.xml", Set.of("application/xml")));
        assertFalse(AllowedTypes.permitsFile("text/html", "page.txt", Set.of("text/plain")));
        assertFalse(AllowedTypes.permitsFile("video/mp4", "clip.ogv", Set.of("video/ogg")));
        assertFalse(AllowedTypes.permitsFile("video/theora", "clip.ogv", Set.of("application/pdf")));
        assertFalse(AllowedTypes.permitsFile("video/theora", null, Set.of("video/ogg")));
        assertFalse(AllowedTypes.permitsFile(null, "clip.ogv", Set.of("video/ogg")));
    }

    @Test
    void anyFileLetsEveryTypeThroughWhileAFieldMayStillNarrowIt() {
        // Verifies */*, the administrator's "any file": read as itself, it lets any detected type through — an e-mail
        // message and a 3D model, top-level types past the usual five —, a field without types keeps it (the view then
        // restricts nothing), a field with types keeps its own.
        assertEquals(Optional.of("*/*"), AllowedTypes.resolve(" */* "));
        assertTrue(AllowedTypes.permits("message/rfc822", Set.of("*/*")));
        assertTrue(AllowedTypes.permits("model/x.stl-binary", Set.of("*/*")));
        assertTrue(AllowedTypes.permitsFile("message/rfc822", "note.eml", Set.of("*/*")));
        assertEquals(Set.of("*/*"), AllowedTypes.forField(List.of(), Set.of("*/*"), "cv"));
        assertEquals(List.of("application/pdf", "image/*"),
                List.copyOf(AllowedTypes.forField(List.of("pdf", "image/*"), Set.of("*/*"), "cv")));
        assertFalse(AllowedTypes.permits("message/rfc822", Set.of("application/*", "text/*")));
    }

    @Test
    void theEditorOffersTheTopLevelWildcardsInPlaceOfAnyFile() {
        // Verifies the Accept setting's choices: */* itself is never offered — a field without types already accepts
        // anything —, each top-level type of Tika's registry is, beside the other allowed types; a list without */*
        // is offered as it is, sorted.
        Set<String> wildcards = Set.of("application/*", "audio/*", "chemical/*", "image/*", "message/*", "model/*",
                "multipart/*", "text/*", "video/*", "x-conference/*");
        assertEquals(wildcards, AllowedTypes.choices(Set.of("*/*")));
        Set<String> withPdf = new HashSet<>(wildcards);
        withPdf.add("application/pdf");
        assertEquals(withPdf, AllowedTypes.choices(allowed("*/*", "application/pdf", "image/*")));
        assertEquals(List.of("application/pdf", "image/png"), List.copyOf(AllowedTypes.choices(allowed("image/png", "application/pdf"))));
    }

    @Test
    void theWarningSaysWhetherATokenIsNoFileTypeOrOneNoLongerAllowed() {
        // Verifies the field's warning tells the two cases apart: a stored token that is no file type at all, and a
        // type the administrator's list no longer allows — an administrator looks for the second in the list only.
        PrintStream previous = System.err;
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        System.setErr(new PrintStream(captured, true, StandardCharsets.UTF_8));
        try {
            AllowedTypes.forField(List.of("no such thing", "application/zip"), Set.of("application/pdf"), "/form/cv");
        } finally {
            System.setErr(previous);
        }
        String logged = captured.toString(StandardCharsets.UTF_8);
        assertTrue(logged.contains("accepts 'no such thing', which is not a file type"), logged);
        assertTrue(logged.contains("accepts 'application/zip', which the uploads configuration does not allow"), logged);
    }

    @Test
    void anOggVideoIsDetectedAsItsCodecAndStillAccepted() {
        // Verifies the case the rule exists for, on real bytes rather than type names: Tika reads the first page of an
        // Ogg Theora stream as video/theora even with the .ogv name, which the listed video/ogg must let through.
        byte[] ogg = new byte[64];
        System.arraycopy("OggS".getBytes(StandardCharsets.US_ASCII), 0, ogg, 0, 4);
        ogg[28] = (byte) 0x80;
        System.arraycopy("theora".getBytes(StandardCharsets.US_ASCII), 0, ogg, 29, 6);
        String detected = new org.apache.tika.Tika().detect(ogg, "clip.ogv");

        assertEquals("video/theora", detected);
        assertFalse(AllowedTypes.permits(detected, Set.of("video/ogg")));
        assertTrue(AllowedTypes.permitsFile(detected, "clip.ogv", Set.of("video/ogg")));
    }
}
