package org.jahia.modules.formidable.formsimport;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The markers the import writes on what it creates are read by a later import and by the attachment, never by
 * a contributor: every property of every {@code fmdbmix:imported*} type of the engine's CND is {@code hidden},
 * so that the Content Editor of an imported form or field does not show it (docs/architecture/forms-import.md,
 * "Content model"). A property added to a marker without the flag fails here, not in the editor.
 */
class ImportMarkersCndTest {

    private static final Path ENGINE = Path.of("src/main/resources/META-INF/definitions.cnd");
    private static final Pattern MARKER = Pattern.compile("^\\[(fmdbmix:imported[A-Za-z]*)\\]");
    private static final Pattern HEADER = Pattern.compile("^\\[");
    private static final Pattern PROPERTY = Pattern.compile("^\\s*-\\s*([A-Za-z0-9_:]+)\\s*\\(");

    @Test
    void everyPropertyOfAnImportMarkerIsHidden() throws IOException {
        Map<String, List<String>> visible = new LinkedHashMap<>();
        String marker = null;
        for (String line : Files.readAllLines(ENGINE)) {
            Matcher header = MARKER.matcher(line);
            if (header.find()) {
                marker = header.group(1);
                visible.put(marker, new ArrayList<>());
            } else if (HEADER.matcher(line).find()) {
                marker = null;
            } else if (marker != null) {
                Matcher property = PROPERTY.matcher(line);
                if (property.find() && !line.matches(".*\\)\\s.*\\bhidden\\b.*")) {
                    visible.get(marker).add(property.group(1));
                }
            }
        }

        assertEquals(List.of("fmdbmix:importedForm", "fmdbmix:importedField", "fmdbmix:importedResults", "fmdbmix:importedSubmission"),
                List.copyOf(visible.keySet()));
        assertTrue(visible.values().stream().allMatch(List::isEmpty), "properties a contributor would see: " + visible);
    }
}
