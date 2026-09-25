import {describe, expect, it} from "vitest";
import {FIELD_ACTION_REQUEST_ATTRIBUTE, fieldActionResult, readFieldActionRequest} from "./fieldActions.js";

const request = {formId: "3f1c6d2e-0b6a-4a2e-9d1c-5b7e8f9a0b1c", fieldName: "email", value: "ada@example.com", locale: "fr"};

/** A render context whose request carries one attribute, the engine's — or nothing. */
const contextWith = (attribute: unknown) => ({
	getRequest: () => ({
		getAttribute: (name: string) => (name === FIELD_ACTION_REQUEST_ATTRIBUTE ? attribute : null)
	})
});

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
	it("is exactly one JSON object with a lower-case verdict, nothing around it", () => {
		for (const text of [fieldActionResult.accept(), fieldActionResult.reject(), fieldActionResult.unavailable()]) {
			expect(text.trim()).toBe(text);
			expect(text.startsWith("{") && text.endsWith("}")).toBe(true);
		}
		expect(JSON.parse(fieldActionResult.accept())).toEqual({verdict: "accept"});
		expect(JSON.parse(fieldActionResult.reject())).toEqual({verdict: "reject"});
		expect(JSON.parse(fieldActionResult.unavailable())).toEqual({verdict: "unavailable"});
	});

	it("carries the detail only when one is given", () => {
		expect(JSON.parse(fieldActionResult.reject("unknown customer"))).toEqual({verdict: "reject", detail: "unknown customer"});
		expect(JSON.parse(fieldActionResult.unavailable("provider 503"))).toEqual({verdict: "unavailable", detail: "provider 503"});
		expect(Object.keys(JSON.parse(fieldActionResult.reject("")))).toEqual(["verdict"]);
	});

	it("never carries a detail on an accept: there is nothing to log", () => {
		expect(Object.keys(JSON.parse(fieldActionResult.accept()))).toEqual(["verdict"]);
	});
});
