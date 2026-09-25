// jsdom does not ship the CSS namespace; the code under test only needs escape(). Imported by the
// DOM tests for its effect, once, rather than pasted into each of them.
if (typeof CSS === 'undefined') {
	(globalThis as {CSS?: {escape: (value: string) => string}}).CSS = {
		escape: value => value.replaceAll(/[^a-zA-Z0-9_-]/g, character => `\\${character}`)
	};
}
