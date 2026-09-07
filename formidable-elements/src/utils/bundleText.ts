/**
 * Text helpers for what a module declares for the Content Editor and the actions zone
 * reuses: a value read from a resource bundle, and a rich-text tooltip shown as one plain line.
 */

// Java-style escapes of a .properties value: \uXXXX, and the handful of single-character ones.
const unescapeProperty = (raw: string): string =>
	raw.replace(/\\(u[0-9a-fA-F]{4}|.)/g, (_, escaped: string) => {
		if (escaped.startsWith("u")) return String.fromCharCode(parseInt(escaped.slice(1), 16));
		return {n: "\n", t: "\t", r: "\r", f: "\f"}[escaped] ?? escaped;
	});

/**
 * The value of one key of a .properties file read as text. The two kinds of bundle differ in
 * encoding — a Java module's is ISO-8859-1 with \u escapes, a JavaScript module's is raw UTF-8
 * (the loader reads `settings/resources` that way) — and Java's Properties.load(InputStream)
 * would turn the latter's accents into mojibake. The text arrives decoded as UTF-8, which
 * leaves ASCII escapes intact, and both are resolved here: comments, `key=value` / `key: value`,
 * line continuations and escapes, as the Properties format defines them.
 */
export const propertyValue = (text: string, key: string): string | undefined => {
	const lines = text.split(/\r?\n/);
	for (let i = 0; i < lines.length; i++) {
		let line = lines[i].replace(/^\s+/, "");
		if (line === "" || line.startsWith("#") || line.startsWith("!")) continue;
		// A trailing backslash (an odd number of them) continues the value on the next line.
		while (/(^|[^\\])(\\\\)*\\$/.test(line) && i + 1 < lines.length) {
			line = line.slice(0, -1) + lines[++i].replace(/^\s+/, "");
		}
		// The key ends at the first unescaped '=', ':' or blank.
		let end = 0;
		while (end < line.length && !/[=:\s]/.test(line[end])) {
			end += line[end] === "\\" ? 2 : 1;
		}
		if (unescapeProperty(line.slice(0, end)) !== key) continue;
		return unescapeProperty(line.slice(end).replace(/^\s*[=:]?\s*/, ""));
	}
	return undefined;
};

// The named and numeric entities a Content Editor tooltip may carry; the text is rendered as
// React text, so they must be decoded here or the reader sees them literally.
const NAMED_ENTITIES: Record<string, string> = {amp: "&", lt: "<", gt: ">", quot: '"', apos: "'", nbsp: " "};
const decodeEntities = (text: string): string =>
	text.replace(/&(#x[0-9a-fA-F]+|#\d+|[a-zA-Z]+);/g, (whole, entity: string) => {
		if (entity.startsWith("#x")) return String.fromCodePoint(parseInt(entity.slice(2), 16));
		if (entity.startsWith("#")) return String.fromCodePoint(parseInt(entity.slice(1), 10));
		return NAMED_ENTITIES[entity] ?? whole;
	});

/**
 * A Content Editor tooltip (rich text, light formatting allowed) as one plain line: tags are
 * dropped — each replaced by a space, so text that markup separated ("First.<br/>Second.")
 * does not run together — entities decoded, whitespace collapsed.
 */
export const tooltipText = (html: string): string =>
	decodeEntities(html.replace(/<[^>]+>/g, " ")).replace(/\s+/g, " ").trim();
