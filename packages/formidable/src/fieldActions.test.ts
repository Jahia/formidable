import {createElement} from "react";
import {renderToStaticMarkup, renderToString} from "react-dom/server";
import {describe, expect, it} from "vitest";
import {FIELD_ACTION_REQUEST_ATTRIBUTE, fieldActionResult, readFieldActionRequest} from "./fieldActions.js";

const request = {formId: "3f1c6d2e-0b6a-4a2e-9d1c-5b7e8f9a0b1c", fieldName: "email", value: "ada@example.com", locale: "fr"};

/** A render context whose request carries one attribute, the engine's — or nothing. */
const contextWith = (attribute: unknown) => ({
	getRequest: () => ({
		getAttribute: (name: string) => (name === FIELD_ACTION_REQUEST_ATTRIBUTE ? attribute : null)
	})
});

/**
 * What the JavaScript modules engine does with a rendered view before the Java side sees it
 * (`javascript-modules-engine/src/server/init-react.tsx`): `renderToString`, then the `jsm-raw-html`
 * tags stripped. The strict reader of the engine then wants exactly one JSON object.
 */
const asTheEngineEmitsIt = (element: Parameters<typeof renderToString>[0]) =>
	renderToString(element).replaceAll(/<\/?jsm-raw-html>/g, "");

describe("readFieldActionRequest", () => {
	it("reads the request the engine set", () => {
		expect(readFieldActionRequest(contextWith(JSON.stringify(request)))).toEqual(request);
	});

	it("reads a Java string, which arrives as a host object and not a JavaScript string", () => {
		expect(readFieldActionRequest(contextWith({toString: () => JSON.stringify(request)}))).toEqual(request);
	});

	it("answers null on a direct hit of the view: no attribute", () => {
		expect(readFieldActionRequest(contextWith(null))).toBeNull();
		expect(readFieldActionRequest(contextWith(undefined))).toBeNull();
	});

	it("answers null on an attribute that is not the engine's JSON object", () => {
		expect(readFieldActionRequest(contextWith("not json"))).toBeNull();
		expect(readFieldActionRequest(contextWith("42"))).toBeNull();
		expect(readFieldActionRequest(contextWith("null"))).toBeNull();
		expect(readFieldActionRequest(contextWith("[]"))).toBeNull();
	});

	it("answers null when one of the four fields is missing or is not a string", () => {
		const withoutLocale = {formId: request.formId, fieldName: request.fieldName, value: request.value};
		expect(readFieldActionRequest(contextWith(JSON.stringify(withoutLocale)))).toBeNull();
		expect(readFieldActionRequest(contextWith(JSON.stringify({...request, value: 3})))).toBeNull();
		expect(readFieldActionRequest(contextWith(JSON.stringify({...request, formId: null})))).toBeNull();
	});
});

describe("fieldActionResult", () => {
	it("reaches the engine as exactly one JSON object with a lower-case verdict: React escapes nothing of it", () => {
		expect(asTheEngineEmitsIt(fieldActionResult.accept())).toBe('{"verdict":"accept"}');
		expect(asTheEngineEmitsIt(fieldActionResult.reject())).toBe('{"verdict":"reject"}');
		expect(asTheEngineEmitsIt(fieldActionResult.unavailable())).toBe('{"verdict":"unavailable"}');
	});

	it("carries the detail only when one is given, quotes and apostrophes intact", () => {
		expect(JSON.parse(asTheEngineEmitsIt(fieldActionResult.reject("it's \"unknown\""))))
			.toEqual({verdict: "reject", detail: "it's \"unknown\""});
		expect(JSON.parse(asTheEngineEmitsIt(fieldActionResult.unavailable("provider 503"))))
			.toEqual({verdict: "unavailable", detail: "provider 503"});
		expect(Object.keys(JSON.parse(asTheEngineEmitsIt(fieldActionResult.reject(""))))).toEqual(["verdict"]);
		expect(Object.keys(JSON.parse(asTheEngineEmitsIt(fieldActionResult.accept())))).toEqual(["verdict"]);
	});

	it("is the raw-html element the engine strips, and a plain string would not have been: React escapes text", () => {
		expect(renderToStaticMarkup(fieldActionResult.accept())).toBe('<jsm-raw-html>{"verdict":"accept"}</jsm-raw-html>');
		// the trap the helpers exist for: a view written as a plain string, the way the first draft of the README had it
		expect(renderToStaticMarkup(createElement(() => '{"verdict":"accept"}'))).toBe("{&quot;verdict&quot;:&quot;accept&quot;}");
	});
});
