package org.jahia.modules.formidable.engine.migration.v05;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FormerListLinesTest {

    @Test
    void theLinesOfEarlierBuildsDescribeOneTargetFileEach() {
        // Verifies the conversion's reading: id|label|url per line, the development list's marked, a malformed line skipped.
        List<Map<String, String>> entries = FormerListLines.forwardTargetEntries("crm|CRM|https://crm.example.com\ntwo|parts",
                "local|Local|http://localhost:3000/hook");

        assertEquals(List.of(
                Map.of("id", "crm", "label", "CRM", "url", "https://crm.example.com", "development", "false"),
                Map.of("id", "local", "label", "Local", "url", "http://localhost:3000/hook", "development", "true")), entries);
    }

    @Test
    void theLinesOfEarlierBuildsDescribeOneSourceFileEach() {
        // Verifies the conversion's reading: 3- and 4-part lines, a malformed line and a blank id or initializer skipped.
        assertEquals(List.of(
                Map.of("id", "countries", "label", "Countries", "initializerKey", "country", "param", ""),
                Map.of("id", "tags", "label", "Tags", "initializerKey", "categoryTree", "param", "/sites/systemsite/categories")),
                FormerListLines.optionsSourceEntries("countries|Countries|country\nonly-two|parts\n|Blank|country\nx|X|\ntags|Tags|categoryTree|/sites/systemsite/categories"));
    }
}
