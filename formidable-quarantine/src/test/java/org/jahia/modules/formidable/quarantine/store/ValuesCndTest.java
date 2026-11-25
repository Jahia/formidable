package org.jahia.modules.formidable.quarantine.store;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The store writes each field of a held submission with {@code setProperty(name, String[])} on a value node. The JCR
 * node wrapper of Jahia looks up a single-valued definition for that call and refuses the write without one, with
 * "Couldn't find definition for property". The repository then stores the property as multiple, through the
 * multiple residual. So each value type declares both residuals. A mocked session cannot see this: the CND text can.
 */
class ValuesCndTest {

    private static final Path CND = Path.of("src/main/resources/META-INF/definitions.cnd");
    private static final Pattern HEADER = Pattern.compile("^\\[");
    private static final Pattern SINGLE = Pattern.compile("^\\s*-\\s*\\*\\s*\\(string\\)(?!.*\\bmultiple\\b).*$");
    private static final Pattern MULTIPLE = Pattern.compile("^\\s*-\\s*\\*\\s*\\(string\\).*\\bmultiple\\b.*$");

    @Test
    void thePublicValuesDeclareASingleAndAMultipleStringResidual() throws IOException {
        assertBothResiduals(QuarantineTree.VALUES_TYPE);
    }

    @Test
    void theSensitiveValuesDeclareASingleAndAMultipleStringResidual() throws IOException {
        assertBothResiduals(QuarantineTree.SENSITIVE_VALUES_TYPE);
    }

    private static void assertBothResiduals(String type) throws IOException {
        List<String> properties = propertiesOf(type);

        assertTrue(properties.stream().anyMatch(line -> SINGLE.matcher(line).matches()),
                type + " declares no single string residual: " + properties);
        assertTrue(properties.stream().anyMatch(line -> MULTIPLE.matcher(line).matches()),
                type + " declares no multiple string residual: " + properties);
    }

    private static List<String> propertiesOf(String type) throws IOException {
        List<String> properties = new ArrayList<>();
        boolean found = false;
        boolean inType = false;
        for (String line : Files.readAllLines(CND)) {
            if (HEADER.matcher(line).find()) {
                inType = line.startsWith("[" + type + "]");
                found |= inType;
            } else if (inType && line.trim().startsWith("-")) {
                properties.add(line);
            }
        }
        assertTrue(found, "no type " + type + " in " + CND);
        assertFalse(properties.isEmpty(), type + " declares no property");
        return properties;
    }
}
