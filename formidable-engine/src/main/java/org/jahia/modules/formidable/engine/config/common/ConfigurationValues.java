package org.jahia.modules.formidable.engine.config.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;

/**
 * How every configuration theme reads its values: the lines of a multi-line setting, a timeout or a bound
 * that falls back to its default when the file says zero or less, and the HTTP client built on a connect
 * timeout. A zero or negative limit is a configuration mistake, not a way to disable a cap — the message
 * names the setting so the administrator finds it.
 */
public final class ConfigurationValues {

    public static final long DEFAULT_HTTP_CONNECT_TIMEOUT_SECONDS = 5L;
    public static final long DEFAULT_HTTP_REQUEST_TIMEOUT_SECONDS = 10L;

    /** How the multi-line configuration values are split: one entry per line, either line ending. */
    private static final String LINE_BREAKS = "[\n\r]+";

    private static final Logger log = LoggerFactory.getLogger(ConfigurationValues.class);

    private ConfigurationValues() {
    }

    /** The non-blank lines of a multi-line value, trimmed, in order; none for a null or blank value. */
    public static List<String> lines(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return Arrays.stream(raw.split(LINE_BREAKS))
                .map(String::trim)
                .filter(line -> !line.isEmpty())
                .toList();
    }

    /** The comma-separated tokens of a value, trimmed, the blank ones dropped. */
    public static List<String> commaSeparated(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(token -> !token.isEmpty())
                .toList();
    }

    /** A timeout in seconds, or the default with a warning when the configured value is zero or less. */
    public static Duration timeoutSeconds(String propertyName, long seconds, long defaultSeconds) {
        if (seconds <= 0) {
            log.warn("[Formidable configuration] Invalid {}={}s, falling back to {}s.", propertyName, seconds, defaultSeconds);
            return Duration.ofSeconds(defaultSeconds);
        }
        return Duration.ofSeconds(seconds);
    }

    /** A positive bound, or the default with a warning when the configured value is zero or less. */
    public static long positiveLong(String propertyName, long value, long defaultValue) {
        if (value <= 0) {
            log.warn("[Formidable configuration] Invalid {}={}, falling back to {}.", propertyName, value, defaultValue);
            return defaultValue;
        }
        return value;
    }

    /** A positive count, or the default silently: a bound whose zero the documentation does not promise. */
    public static int positiveOrDefault(int value, int defaultValue) {
        return value > 0 ? value : defaultValue;
    }

    /** One HTTP client per configuration snapshot, built on the connect timeout; the request timeout goes on each request. */
    public static HttpClient httpClient(Duration connectTimeout) {
        return HttpClient.newBuilder().connectTimeout(connectTimeout).build();
    }
}
