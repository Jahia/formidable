// @vitest-environment jsdom
import {describe, expect, it} from 'vitest';
import {fieldKindFromForm} from './messageUtils';

// jsdom does not ship the CSS namespace; the code only needs escape().
if (typeof CSS === 'undefined') {
	(globalThis as {CSS?: {escape: (value: string) => string}}).CSS = {
		escape: value => value.replace(/[^a-zA-Z0-9_-]/g, character => `\\${character}`)
	};
}

const formOf = (html: string): HTMLFormElement => {
	document.body.innerHTML = `<form>${html}</form>`;
	return document.querySelector('form')!;
};

describe('fieldKindFromForm (DOM)', () => {
	it('resolves a mirrored control through the wrapper node type', () => {
		// The slider's named control is a hidden mirror: reading the control's type
		// returned 'hidden' and made the range formatting unreachable — the wrapper's
		// data-fmdb-node-type is what must win.
		const form = formOf(`
			<div data-fmdb-node-name="budget" data-fmdb-node-type="fmdb:inputRange">
				<input type="range" id="budget-slider" value="50000"/>
				<input type="hidden" name="budget" value="50000"/>
			</div>`);

		expect(fieldKindFromForm(form)('budget')).toBe('range');
	});

	it('falls back to the control type for an unknown node type', () => {
		// A third-party field carries a node type the map does not know: the control's
		// own type keeps the formatting alive on the extension path.
		const form = formOf(`
			<div data-fmdb-node-name="rating" data-fmdb-node-type="mymod:rating">
				<input type="number" name="rating" value="4"/>
			</div>`);

		expect(fieldKindFromForm(form)('rating')).toBe('number');
	});

	it('leaves a plain text field unformatted', () => {
		const form = formOf(`
			<div data-fmdb-node-name="zip" data-fmdb-node-type="fmdb:inputText">
				<input type="text" name="zip" value="75001"/>
			</div>`);

		expect(fieldKindFromForm(form)('zip')).toBe('text');
	});
});
