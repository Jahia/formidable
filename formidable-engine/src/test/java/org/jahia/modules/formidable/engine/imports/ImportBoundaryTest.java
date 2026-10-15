package org.jahia.modules.formidable.engine.imports;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The import reaches the rest of the engine through {@code engine.api} only: it is to move into a module of its
 * own, which will compile against the api package alone. Every other package of the engine named by an import
 * source — a class, a static import, a qualified name — is a seam still to open.
 */
class ImportBoundaryTest {

    private static final List<Path> IMPORT_SOURCES = List.of(
            Path.of("src/main/java/org/jahia/modules/formidable/engine/imports"),
            Path.of("src/main/java/org/jahia/modules/formidable/engine/config/formsimport"));

    /** A reference to a package of the engine that is neither the api nor the import itself (its own package declarations included). */
    private static final Pattern OUTSIDE_API = Pattern.compile(
            "org\\.jahia\\.modules\\.formidable\\.engine\\.(?!api\\b|imports\\b|config\\.formsimport\\b)[a-z][\\w.]*");

    @Test
    void theImportNamesNoPackageOfTheEngineButTheApi() throws IOException {
        List<String> crossings = new ArrayList<>();
        for (Path root : IMPORT_SOURCES) {
            try (Stream<Path> files = Files.walk(root)) {
                for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                    List<String> lines = Files.readAllLines(file);
                    for (int i = 0; i < lines.size(); i++) {
                        Matcher outside = OUTSIDE_API.matcher(lines.get(i));
                        if (outside.find()) {
                            crossings.add(root.relativize(file) + ":" + (i + 1) + " " + outside.group());
                        }
                    }
                }
            }
        }
        assertEquals(List.of(), crossings, "the import names engine packages outside engine.api");
    }
}
