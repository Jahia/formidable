/**
 * Output escaping utilities for form field values (TS port of the engine's FieldEscaper).
 *
 * Formidable stores plain-text submitted values and validates them for shape, length and choice at
 * input time. XSS protection is applied at each output sink by escaping for the target context.
 */

const HTML_ESCAPES: Record<string, string> = {
	"&": "&amp;",
	"<": "&lt;",
	">": "&gt;",
	'"': "&quot;",
	"'": "&#39;",
};

/** Escapes a value for safe insertion into HTML element content or quoted attributes. */
export const escapeHtml = (value: string | null | undefined): string =>
	(value ?? "").replace(/[&<>"']/g, (char) => HTML_ESCAPES[char]);

/**
 * Normalizes a value for use in an email header (To, Subject, From, etc.). Strips carriage returns,
 * newlines and tabs to prevent header injection attacks.
 */
export const headerSafe = (value: string | null | undefined): string =>
	value == null ? "" : value.replace(/[\r\n\t]/g, " ").trim();

/** Returns the plain-text value unchanged (null-safe). */
export const plainText = (value: string | null | undefined): string => value ?? "";
