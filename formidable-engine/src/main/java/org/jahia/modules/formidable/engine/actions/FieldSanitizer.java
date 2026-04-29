package org.jahia.modules.formidable.engine.actions;

/**
 * Output sanitization utilities for form field values.
 *
 * Input-level sanitization (HTML tag stripping) is handled by {@link FormDataParser}
 * before values enter the parameters map. This class handles output encoding:
 * adapting a clean value to the specific output context of each action.
 *
 * Usage by actions:
 *   - HTML output (email body, future HTML rendering) → {@link #htmlEncode(String)}
 *   - Plain-text output (email subject/to, API forwarding, JCR storage) → {@link #plainText(String)}
 */
public final class FieldSanitizer {

    private FieldSanitizer() {}

    /**
     * HTML-encodes a field value for safe insertion into an HTML context.
     * Encodes the five characters that have special meaning in HTML:
     * {@code &}, {@code <}, {@code >}, {@code "}, {@code '}.
     *
     * Use this when building HTML email bodies, HTML templates, or any output
     * that will be rendered as HTML.
     */
    public static String htmlEncode(String value) {
        if (value == null) return "";
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    /**
     * Sanitizes a value for use in an email header (To, Subject, From, etc.).
     * Strips carriage returns, newlines and tabs to prevent header injection attacks.
     * The value has already been stripped of HTML tags at input time by {@link FormDataParser}.
     */
    public static String headerSafe(String value) {
        if (value == null) return "";
        return value.replaceAll("[\r\n\t]", " ").trim();
    }

    /**
     * Returns the value as-is for plain-text output contexts (API forwarding, JCR storage).
     * The value has already been stripped of HTML tags at input time by {@link FormDataParser}.
     */
    public static String plainText(String value) {
        return value != null ? value : "";
    }
}

