import {beforeEach, describe, expect, it, vi} from "vitest";
import type {ChoiceFieldNode} from "./optionsSource.js";

// The GraalVM bridge does not exist under Node: the engine services and the cache are doubles.
const {getService, addCacheDependency} = vi.hoisted(() => ({getService: vi.fn(), addCacheDependency: vi.fn()}));
vi.mock("@jahia/javascript-modules-library", () => ({
	server: {osgi: {getService}, render: {addCacheDependency}}
}));

const {escapeRegExp, resolveFieldOptions} = await import("./optionsSource.js");

const renderContext = {} as Parameters<typeof resolveFieldOptions>[2];
const option = (value: string, label: string) => JSON.stringify({value, label});

interface FieldDouble {
	mixins?: string[];
	properties?: Record<string, string>;
	site?: object | null;
}

const field = ({mixins = [], properties = {}, site = {}}: FieldDouble = {}): ChoiceFieldNode => ({
	isNodeType: (type: string) => mixins.includes(type),
	hasProperty: (name: string) => name in properties,
	getProperty: (name: string) => ({getNode: () => ({getPath: () => properties[name]})}),
	getResolveSite: () => site,
	getLanguage: () => "fr",
	getPath: () => "/sites/s/contents/form/fields/country"
});

beforeEach(() => {
	getService.mockReset();
	addCacheDependency.mockReset();
	vi.spyOn(console, "error").mockImplementation(() => undefined);
});

describe("resolveFieldOptions, manual mode", () => {
	it("renders the options the engine aligns on the default language, and depends on the site", () => {
		const site = {};
		const forDisplay = vi.fn(() => [option("fr", "France")]);
		getService.mockReturnValue({forDisplay});

		const options = resolveFieldOptions(field({site}), [option("stale", "Stale")], renderContext);

		expect(options).toEqual({choices: [{value: "fr", label: "France", selected: false}], sourceError: false});
		expect(getService).toHaveBeenCalledWith("org.jahia.modules.formidable.engine.options.ManualOptionsDisplayService");
		expect(forDisplay).toHaveBeenCalledWith(expect.anything(), "fr");
		expect(addCacheDependency).toHaveBeenCalledWith({node: site}, renderContext);
	});

	it("falls back to the stored options when the engine cannot align them", () => {
		getService.mockImplementation(() => {
			throw new Error("no service");
		});

		const options = resolveFieldOptions(field(), [option("de", "Germany")], renderContext);

		expect(options).toEqual({choices: [{value: "de", label: "Germany", selected: false}], sourceError: false});
	});
});

describe("resolveFieldOptions, sourced modes", () => {
	it("renders what the engine resolves, and flushes on any change under the picked root", () => {
		const resolveForField = vi.fn(() => [option("paris", "Paris")]);
		getService.mockReturnValue({resolveForField});

		const options = resolveFieldOptions(
			field({mixins: ["fmdbmix:contentOptions"], properties: {optionsRootNode: "/sites/s/contents/cities (1)"}}),
			[],
			renderContext
		);

		expect(options).toEqual({choices: [{value: "paris", label: "Paris", selected: false}], sourceError: false});
		expect(getService).toHaveBeenCalledWith("org.jahia.modules.formidable.engine.options.FormidableOptionsSourceService");
		expect(addCacheDependency).toHaveBeenCalledWith(
			{flushOnPathMatchingRegexp: String.raw`/sites/s/contents/cities \(1\)(/.*)?`},
			renderContext
		);
	});

	it("reports a source that cannot deliver, so the field renders an error and not an empty list", () => {
		getService.mockReturnValue({
			resolveForField: () => {
				throw new Error("initializer missing");
			}
		});

		expect(resolveFieldOptions(field({mixins: ["fmdbmix:sourcedOptions"]}), [], renderContext))
			.toEqual({choices: [], sourceError: true});
	});
});

describe("escapeRegExp", () => {
	it("escapes every character a path regexp would read as syntax", () => {
		expect(escapeRegExp("a.b*c+d?e^f$g{h}i(j)k|l[m]n\\o")).toBe(String.raw`a\.b\*c\+d\?e\^f\$g\{h\}i\(j\)k\|l\[m\]n\\o`);
	});
});
