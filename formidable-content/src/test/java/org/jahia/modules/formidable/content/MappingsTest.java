package org.jahia.modules.formidable.content;

import org.jahia.modules.formidable.content.mapping.ConfigException;
import org.jahia.modules.formidable.content.mapping.Mappings;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MappingsTest {

    @Test
    void splitsAtTheFirstEqualsAndTrimsBothSides() throws ConfigException {
        List<Mappings.Entry> entries = Mappings.parse("fixedValues", List.of(" submissionStatus = submitted ", "note=a=b"));
        assertEquals(new Mappings.Entry("submissionStatus", "submitted"), entries.get(0));
        assertEquals(new Mappings.Entry("note", "a=b"), entries.get(1));
    }

    @Test
    void ignoresBlankLinesAndSplitsAValueThatHoldsSeveralLines() throws ConfigException {
        List<Mappings.Entry> entries = Mappings.parse("fieldMappings", List.of("a=b\n\n  \r\nc=d", "  ", "e=f"));
        assertEquals(List.of(new Mappings.Entry("a", "b"), new Mappings.Entry("c", "d"), new Mappings.Entry("e", "f")), entries);
    }

    @Test
    void nothingToParseIsNoEntry() throws ConfigException {
        assertTrue(Mappings.parse("fieldMappings", null).isEmpty());
        assertTrue(Mappings.parse("fieldMappings", List.of()).isEmpty());
    }

    @Test
    void aLineThatIsNotLeftEqualsRightIsAConfigurationError() {
        for (String line : List.of("noequals", "=right", "left=", " = ")) {
            ConfigException error = assertThrows(ConfigException.class, () -> Mappings.parse("fieldMappings", List.of(line)), line);
            assertTrue(error.getMessage().contains("fieldMappings"), "the message names the setting");
            assertTrue(error.getMessage().contains(line.trim()), "the message shows the line");
        }
    }
}
