import { describe, expect, it } from "vitest";
import { interpolate } from "./interpolate";
import { escapeHtml, plainText } from "./escape";

// Port of formidable-engine's TemplateInterpolatorTest — keeps the coverage that was
// dropped when the Java TemplateInterpolator was replaced by this TS implementation.
describe("interpolate", () => {
	it("replaces placeholders with the first submitted value", () => {
		expect(
			interpolate("Hello ${firstname} ${lastname}!", {
				firstname: ["Ada", "ignored"],
				lastname: ["Lovelace"],
			}, plainText),
		).toBe("Hello Ada Lovelace!");
	});

	it("resolves unknown fields to an empty string", () => {
		expect(interpolate("Hi ${nobody}!", {}, plainText)).toBe("Hi !");
	});

	it("returns an empty string for a null template", () => {
		expect(interpolate(null, { any: ["value"] }, plainText)).toBe("");
		expect(interpolate(undefined, { any: ["value"] }, plainText)).toBe("");
	});

	it("leaves text without placeholders untouched", () => {
		expect(interpolate("No placeholders here.", { a: ["b"] }, plainText)).toBe(
			"No placeholders here.",
		);
	});

	it("applies the escaper to interpolated values only", () => {
		expect(
			interpolate("<b>${payload}</b>", { payload: ['<script>alert("x")</script>'] }, escapeHtml),
		).toBe("<b>&lt;script&gt;alert(&quot;x&quot;)&lt;/script&gt;</b>");
	});

	it("handles special replacement characters in values literally", () => {
		// '$' sequences in values must not be treated as replacement patterns.
		expect(interpolate("Amount: ${amount}", { amount: ["$100 ($'&)"] }, plainText)).toBe(
			"Amount: $100 ($'&)",
		);
	});

	it("does not interpolate recursively", () => {
		expect(interpolate("${a}", { a: ["${b}"], b: ["nested"] }, plainText)).toBe("${b}");
	});
});
