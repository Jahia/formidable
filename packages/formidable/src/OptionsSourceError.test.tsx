import {act} from "react";
import {createRoot, type Root} from "react-dom/client";
import {afterEach, beforeEach, describe, expect, it} from "vitest";
import {OptionsSourceError} from "./OptionsSourceError.js";

(globalThis as {IS_REACT_ACT_ENVIRONMENT?: boolean}).IS_REACT_ACT_ENVIRONMENT = true;

let container: HTMLDivElement;
let root: Root;

const mount = (element: React.ReactElement): HTMLDivElement | null => {
	act(() => {
		root.render(element);
	});
	return container.querySelector("div");
};

beforeEach(() => {
	container = document.createElement("div");
	document.body.appendChild(container);
	root = createRoot(container);
});

afterEach(() => {
	act(() => {
		root.unmount();
	});
	container.remove();
});

describe("OptionsSourceError", () => {
	it("blocks the submission of a required field: the Form island reads data-fmdb-source-error", () => {
		const block = mount(<OptionsSourceError label="Country" required message="Unavailable, cannot send"/>);
		expect(block?.className).toBe("fmdb-form-group fmdb-options-source-error");
		expect(block?.getAttribute("data-fmdb-source-error")).toBe("blocking");
		expect(block?.querySelector(".fmdb-form-label")?.textContent).toBe("Country*");
		expect(block?.querySelector(".fmdb-required-indicator")?.getAttribute("aria-hidden")).toBe("true");
		const message = block?.querySelector("p.fmdb-field-error");
		expect(message?.getAttribute("role")).toBe("alert");
		expect(message?.textContent).toBe("Unavailable, cannot send");
	});

	it("leaves the form usable for an optional field", () => {
		const block = mount(<OptionsSourceError label="Country" message="Unavailable"/>);
		expect(block?.getAttribute("data-fmdb-source-error")).toBe("optional");
		expect(block?.querySelector(".fmdb-required-indicator")).toBeNull();
	});

	it("renders no label without a title", () => {
		const block = mount(<OptionsSourceError message="Unavailable"/>);
		expect(block?.querySelector(".fmdb-form-label")).toBeNull();
		expect(block?.querySelector("p.fmdb-field-error")?.textContent).toBe("Unavailable");
	});
});
