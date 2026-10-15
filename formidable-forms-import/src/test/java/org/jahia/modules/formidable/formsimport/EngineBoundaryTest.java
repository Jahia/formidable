package org.jahia.modules.formidable.formsimport;

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
 * The module reaches the engine through {@code org.jahia.modules.formidable.engine.api} only, the one package the
 * engine exports: the compile classpath holds the whole engine jar, so a reference to any other package compiles
 * here and fails to resolve at runtime. This reads the sources and refuses such a reference first.
 */
class EngineBoundaryTest {

    private static final Path SOURCES = Path.of("src/main/java");

    /** A reference to a package of the engine other than its api. */
    private static final Pattern OUTSIDE_API = Pattern.compile("org\\.jahia\\.modules\\.formidable\\.engine\\.(?!api\\b)[a-z][\\w.]*");

    @Test
    void theModuleNamesNoPackageOfTheEngineButTheApi() throws IOException {
        List<String> crossings = new ArrayList<>();
        try (Stream<Path> files = Files.walk(SOURCES)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                List<String> lines = Files.readAllLines(file);
                for (int i = 0; i < lines.size(); i++) {
                    Matcher outside = OUTSIDE_API.matcher(lines.get(i));
                    if (outside.find()) {
                        crossings.add(SOURCES.relativize(file) + ":" + (i + 1) + " " + outside.group());
                    }
                }
            }
        }
        assertEquals(List.of(), crossings, "the module names engine packages outside engine.api");
    }
}
