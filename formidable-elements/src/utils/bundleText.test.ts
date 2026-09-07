import {describe, expect, it} from "vitest";
import {propertyValue, tooltipText} from "./bundleText";

describe("propertyValue", () => {
	it("reads a JavaScript module's bundle as it is written: raw UTF-8", () => {
		const text = "fmdb_form=Formulaire\nfmdb_form.ui.tooltip=Écrit chaque soumission « à la volée ».\n";
		expect(propertyValue(text, "fmdb_form.ui.tooltip")).toEqual("Écrit chaque soumission « à la volée ».");
	});

	it("reads a Java module's bundle with its \\u escapes", () => {
		const text = "fmdbsample_logSubmissionAction.ui.tooltip=\\u00c9crit chaque soumission.\\nDeux lignes.\n";
		expect(propertyValue(text, "fmdbsample_logSubmissionAction.ui.tooltip")).toEqual("Écrit chaque soumission.\nDeux lignes.");
	});

	it("follows the Properties format: comments, separators, continuations, escaped keys", () => {
		const text = [
			"# a comment",
			"! another",
			"first: with a colon",
			"second   spaced value",
			"third=one \\",
			"    two",
			"a\\=b=escaped key",
			"empty=",
		].join("\n");
		expect(propertyValue(text, "first")).toEqual("with a colon");
		expect(propertyValue(text, "second")).toEqual("spaced value");
		expect(propertyValue(text, "third")).toEqual("one two");
		expect(propertyValue(text, "a=b")).toEqual("escaped key");
		expect(propertyValue(text, "empty")).toEqual("");
		expect(propertyValue(text, "missing")).toBeUndefined();
	});

	it("matches the whole key only", () => {
		expect(propertyValue("fmdb_form.ui.tooltip=long\nfmdb_form=short\n", "fmdb_form")).toEqual("short");
	});
});

describe("tooltipText", () => {
	it("keeps words apart where markup separated them", () => {
		expect(tooltipText("First.<br/>Second.")).toEqual("First. Second.");
		expect(tooltipText("<p>Stores each submission</p><p>in Jahia.</p>")).toEqual("Stores each submission in Jahia.");
	});

	it("decodes the entities the editor writes", () => {
		expect(tooltipText("Send to A &amp; B, &lt;3 &#233;t&#xe9;&nbsp;!")).toEqual("Send to A & B, <3 été !");
		// An entity this decoder does not know stays visible rather than vanishing.
		expect(tooltipText("&eacute;")).toEqual("&eacute;");
	});

	it("collapses whitespace", () => {
		expect(tooltipText("  Sends\n\n an   email  ")).toEqual("Sends an email");
	});
});
