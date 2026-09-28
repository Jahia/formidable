package org.jahia.modules.formidable.engine.config.common;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class ConfigurationValuesTest {

    @Test
    void linesAreTheTrimmedNonBlankLinesOfAValueWhateverTheLineEnding() {
        // Verifies the split every multi-line setting goes through: either line ending, blank lines dropped, each
        // line trimmed, and nothing for a null or blank value.
        assertEquals(List.of("a|A|x", "b|B|y"), ConfigurationValues.lines(" a|A|x \r\n\n  b|B|y\n"));
        assertEquals(List.of(), ConfigurationValues.lines(null));
        assertEquals(List.of(), ConfigurationValues.lines("  \n "));
    }

    @Test
    void commaSeparatedTokensAreTrimmedAndTheBlankOnesDropped() {
        assertEquals(List.of("text/plain", "image/png"), ConfigurationValues.commaSeparated(" text/plain ,, image/png "));
        assertEquals(List.of(), ConfigurationValues.commaSeparated(null));
    }

    @Test
    void aZeroOrNegativeValueFallsBackToItsDefaultTheWayEachKindSays() {
        // Verifies the three guards: a timeout and a bound fall back with a warning, a count silently — and a
        // positive value passes through untouched in every case.
        assertEquals(Duration.ofSeconds(5), ConfigurationValues.timeoutSeconds("x", 0, 5));
        assertEquals(Duration.ofSeconds(7), ConfigurationValues.timeoutSeconds("x", 7, 5));
        assertEquals(10L, ConfigurationValues.positiveLong("x", -1, 10));
        assertEquals(3L, ConfigurationValues.positiveLong("x", 3, 10));
        assertEquals(100, ConfigurationValues.positiveOrDefault(0, 100));
        assertEquals(20, ConfigurationValues.positiveOrDefault(20, 100));
    }

    @Test
    void anHttpClientIsBuiltOnTheConnectTimeout() {
        assertNotNull(ConfigurationValues.httpClient(Duration.ofSeconds(5)));
        assertEquals(Duration.ofSeconds(5), ConfigurationValues.httpClient(Duration.ofSeconds(5)).connectTimeout().orElseThrow());
    }

    @Test
    void theEndpointRuleAcceptsHttpsAndRefusesEverythingElseUnlessDevelopmentOnALocalHost() {
        // Verifies the one rule of every outbound endpoint: HTTPS without credentials for a standard entry; plain
        // HTTP on localhost or host.docker.internal, and nothing else, for a development entry.
        assertNull(EndpointRule.unsupportedReason(URI.create("https://api.example.com/x"), false));
        assertEquals("URI must use HTTPS.", EndpointRule.unsupportedReason(URI.create("http://api.example.com/x"), false));
        assertEquals("URI must not include embedded user credentials.", EndpointRule.unsupportedReason(URI.create("https://u:p@api.example.com/x"), false));
        assertNull(EndpointRule.unsupportedReason(URI.create("http://localhost:8080/x"), true));
        assertNull(EndpointRule.unsupportedReason(URI.create("http://host.docker.internal:8080/x"), true));
        assertEquals("URI must use HTTP on localhost or host.docker.internal.", EndpointRule.unsupportedReason(URI.create("http://example.com/x"), true));
        assertEquals("URI must use HTTP on localhost or host.docker.internal.", EndpointRule.unsupportedReason(URI.create("https://localhost/x"), true));
        assertEquals("URI must not include embedded user credentials.", EndpointRule.unsupportedReason(URI.create("http://u:p@localhost/x"), true));
    }
}
