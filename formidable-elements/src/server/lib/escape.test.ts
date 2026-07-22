import { describe, expect, it } from "vitest";
import { escapeHtml, headerSafe, plainText } from "./escape";

// Port of the escaping expectations from formidable-engine's FieldEscaperTest that
// apply to the TS implementation used by the TypeScript form actions.
describe("escapeHtml", () => {
	it("escapes HTML metacharacters", () => {
		expect(escapeHtml('<script>alert("x") & \'y\'</script>')).toBe(
			"&lt;script&gt;alert(&quot;x&quot;) &amp; &#39;y&#39;&lt;/script&gt;",
		);
	});

	it("returns an empty string for null and undefined", () => {
		expect(escapeHtml(null)).toBe("");
		expect(escapeHtml(undefined)).toBe("");
	});

	it("leaves plain text untouched", () => {
		expect(escapeHtml("Ada Lovelace, 1815")).toBe("Ada Lovelace, 1815");
	});
});

describe("headerSafe", () => {
	it("strips header-injection characters", () => {
		// Each control character becomes one space, matching the Java FieldEscaper behavior.
		expect(headerSafe("evil@example.com\r\nBcc: victim@example.com")).toBe(
			"evil@example.com  Bcc: victim@example.com",
		);
		expect(headerSafe("subject\twith\ttabs")).toBe("subject with tabs");
	});

	it("trims surrounding whitespace", () => {
		expect(headerSafe("  padded  ")).toBe("padded");
	});

	it("returns an empty string for null and undefined", () => {
		expect(headerSafe(null)).toBe("");
		expect(headerSafe(undefined)).toBe("");
	});
});

describe("plainText", () => {
	it("passes values through unchanged, null-safely", () => {
		expect(plainText("as-is <kept>")).toBe("as-is <kept>");
		expect(plainText(null)).toBe("");
		expect(plainText(undefined)).toBe("");
	});
});
