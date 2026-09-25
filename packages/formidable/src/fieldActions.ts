// The contract between the engine and a `hidden.execute` view — the JavaScript way to write a
// field action (docs/architecture/field-actions.md, "A field action in JavaScript"). The engine
// renders the view with the candidate value in a request attribute and reads the view's whole
// output as one JSON object. Both halves are spelled here, so that a view written outside this
// repository cannot misspell the attribute or the verdict.

/** What the engine hands a `hidden.execute` view: the form, the field, the candidate value, the visitor's locale. */
export interface FieldActionRequest {
	formId: string;
	fieldName: string;
	value: string;
	locale: string;
}

/** The request attribute the engine sets before rendering the view; absent on a direct hit of the view. */
export const FIELD_ACTION_REQUEST_ATTRIBUTE = "formidable.fieldAction";

/**
 * The slice of the render context a view needs here — structural, so that this package keeps no
 * dependency on `@jahia/javascript-modules-library`; the real `RenderContext` satisfies it.
 */
export interface FieldActionRenderContext {
	getRequest(): {getAttribute(name: string): unknown};
}

/**
 * The request the engine set, or null when the engine is not the caller — a direct hit of the view
 * through the render servlet, which no URL can turn into a check, or an attribute that is not the
 * engine's JSON. On null the view must answer nothing (`return null`): the view is not an endpoint.
 */
export function readFieldActionRequest(renderContext: FieldActionRenderContext): FieldActionRequest | null {
	const raw = renderContext.getRequest().getAttribute(FIELD_ACTION_REQUEST_ATTRIBUTE);
	if (raw === null || raw === undefined) {
		return null;
	}
	let parsed: unknown;
	try {
		// String(): the attribute is a Java string when the engine set it
		parsed = JSON.parse(String(raw));
	} catch {
		return null;
	}
	if (typeof parsed !== "object" || parsed === null) {
		return null;
	}
	const {formId, fieldName, value, locale} = parsed as Record<string, unknown>;
	if (typeof formId !== "string" || typeof fieldName !== "string" || typeof value !== "string" || typeof locale !== "string") {
		return null;
	}
	return {formId, fieldName, value, locale};
}

/**
 * The three answers a `hidden.execute` view may return, as the exact strings the engine's strict
 * reader accepts. The view's whole output must be this one object — nothing before it, nothing after
 * it, never the candidate value — so the view returns one of these and nothing else. `detail` is for
 * the server logs, never for the visitor: what the visitor reads is the contributor's rejection
 * message, which the engine renders.
 */
export const fieldActionResult = {
	accept: (): string => JSON.stringify({verdict: "accept"}),
	reject: (detail?: string): string => JSON.stringify(detail ? {verdict: "reject", detail} : {verdict: "reject"}),
	unavailable: (detail?: string): string =>
		JSON.stringify(detail ? {verdict: "unavailable", detail} : {verdict: "unavailable"}),
} as const;
