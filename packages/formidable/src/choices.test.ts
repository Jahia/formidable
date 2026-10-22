import {afterEach, describe, expect, it, vi} from "vitest";
import {parseChoices} from "./choices.js";

afterEach(() => {
	vi.restoreAllMocks();
});

describe("parseChoices", () => {
	it("reads value, label and default selection from each stored entry", () => {
		expect(parseChoices([
			JSON.stringify({value: "s", label: "Small", selected: true}),
			JSON.stringify({value: "m", label: "Medium"})
		])).toEqual([
			{value: "s", label: "Small", selected: true},
			{value: "m", label: "Medium", selected: false}
		]);
	});

	it("counts only a boolean true as a default selection", () => {
		expect(parseChoices([JSON.stringify({value: "s", label: "Small", selected: "true"})])[0].selected).toBe(false);
	});

	it("reads no options as an empty list", () => {
		expect(parseChoices()).toEqual([]);
		expect(parseChoices([])).toEqual([]);
	});

	it("keeps a broken entry visible to the contributor, with an empty value", () => {
		vi.spyOn(console, "error").mockImplementation(() => undefined);
		const [invalid, malformed] = parseChoices(["{not json", JSON.stringify({value: 1, label: "One"})]);
		expect(invalid).toEqual({value: "", label: "Invalid JSON: {not json", selected: false});
		expect(malformed.value).toBe("");
		expect(malformed.label).toContain("Malformed option structure");
	});
});
