// @vitest-environment jsdom
import {describe, expect, it} from 'vitest';
import {advancesOnPick, collectScreens, firstFocusable, resolveScreenIndex, screenIndexOf} from './screens';

/** An element wrapper as LogicAwareRender renders it, around a text input unless `inner` says otherwise. */
const field = (id: string, attributes = '', inner = `<input name="${id}">`) =>
	`<div data-fmdb-node-id="${id}" ${attributes}>${inner}</div>`;
const content = (id: string, attributes = '') =>
	`<div data-fmdb-node-id="${id}" data-fmdb-content="true" ${attributes}><p>text</p></div>`;
const fieldset = (id: string, children: string) =>
	`<div data-fmdb-node-id="${id}"><fieldset>${children}</fieldset></div>`;
const step = (id: string, children: string) =>
	`<div data-fmdb-node-id="${id}"><div data-fmdb-step>${children}</div></div>`;

const formOf = (markup: string): HTMLFormElement => {
	document.body.innerHTML = `<form>${markup}</form>`;
	return document.querySelector('form')!;
};

const ids = (form: HTMLElement, container: HTMLElement = form) =>
	collectScreens(container).map(screen => screen.wrappers.map(wrapper => wrapper.dataset.fmdbNodeId).join('+'));

describe('collectScreens', () => {
	it('makes one screen of each field at the top of a form without steps', () => {
		const form = formOf(field('a') + field('b') + field('c'));
		expect(ids(form)).toEqual(['a', 'b', 'c']);
	});

	it('makes one screen of a fieldset, its fields included', () => {
		const form = formOf(field('a') + fieldset('set', field('x') + field('y')) + field('b'));
		expect(ids(form)).toEqual(['a', 'set', 'b']);
	});

	it('reads the screens of a step from the step\'s own children only', () => {
		const form = formOf(step('s1', field('a') + fieldset('set', field('x'))) + step('s2', field('b')));
		const [first, second] = Array.from(form.querySelectorAll<HTMLElement>('[data-fmdb-step]'));
		expect(ids(form, first)).toEqual(['a', 'set']);
		expect(ids(form, second)).toEqual(['b']);
	});

	it('shows content with the element after it, and the content ending a step with the element before it', () => {
		const form = formOf(content('intro') + field('a') + content('c1') + content('c2') + field('b') + content('outro'));
		expect(ids(form)).toEqual(['intro+a', 'c1+c2+b+outro']);
	});

	it('makes one screen of content alone', () => {
		const form = formOf(content('only'));
		expect(ids(form)).toEqual(['only']);
	});

	it('skips what the logic or a prefill hides, and a hidden field', () => {
		const form = formOf(
			field('a')
			+ field('logic', 'data-fmdb-logic-hidden="true"')
			+ field('prefilled', 'data-fmdb-prefilled="hidden"')
			+ field('hidden', 'data-fmdb-node-type="fmdb:inputHidden"', '<input type="hidden" name="h" value="1">')
			+ content('hiddenContent', 'data-fmdb-logic-hidden="true"')
			+ field('b')
		);
		expect(ids(form)).toEqual(['a', 'b']);
	});

	it('keeps a custom field that posts through a hidden input only', () => {
		const form = formOf(field('a') + field('slider', 'data-fmdb-node-type="acme:slider"', '<div role="slider" tabindex="0"></div><input type="hidden" name="slider">'));
		expect(ids(form)).toEqual(['a', 'slider']);
	});

	it('skips a field whose view declared it shows nothing, and a fieldset holding only such fields', () => {
		const capture = '<span data-fmdb-no-screen="true"><input type="hidden" name="utm"></span>';
		const form = formOf(field('a') + field('utm', 'data-fmdb-node-type="acme:utm"', capture) + fieldset('set', field('ref', '', capture)) + field('b'));
		expect(ids(form)).toEqual(['a', 'b']);
	});

	it('skips a fieldset whose fields are all hidden, and keeps one with a field shown', () => {
		const form = formOf(
			fieldset('empty', field('x', 'data-fmdb-logic-hidden="true"') + field('y', 'data-fmdb-node-type="fmdb:inputHidden"', '<input type="hidden" name="y">'))
			+ fieldset('kept', field('z', 'data-fmdb-logic-hidden="true"') + field('w'))
		);
		expect(ids(form)).toEqual(['kept']);
	});

	it('keeps a field the logic shows again', () => {
		const form = formOf(field('a') + field('b', 'data-fmdb-logic-hidden="false"'));
		expect(ids(form)).toEqual(['a', 'b']);
	});
});

describe('resolveScreenIndex', () => {
	it('stays on the screen of the anchor while it is shown', () => {
		const form = formOf(field('a') + field('b') + field('c'));
		const screens = collectScreens(form);
		expect(resolveScreenIndex(screens, form.querySelector('[data-fmdb-node-id="b"]'))).toBe(1);
	});

	it('moves to the next screen once the logic hides the one on display', () => {
		const form = formOf(field('a') + field('b', 'data-fmdb-logic-hidden="true"') + field('c'));
		const screens = collectScreens(form);
		expect(resolveScreenIndex(screens, form.querySelector('[data-fmdb-node-id="b"]'))).toBe(1);
	});

	it('falls back on the last screen when nothing follows the hidden one', () => {
		const form = formOf(field('a') + field('b') + field('c', 'data-fmdb-logic-hidden="true"'));
		const screens = collectScreens(form);
		expect(resolveScreenIndex(screens, form.querySelector('[data-fmdb-node-id="c"]'))).toBe(1);
	});

	it('starts on the first screen without an anchor, and nowhere without screens', () => {
		const form = formOf(field('a') + field('b'));
		expect(resolveScreenIndex(collectScreens(form), null)).toBe(0);
		expect(resolveScreenIndex([], null)).toBe(-1);
	});
});

describe('advancesOnPick', () => {
	const radios = '<fieldset data-fmdb-advance-on-change="true"><input type="radio" name="r" value="1"><input type="radio" name="r" value="2"></fieldset>';

	it('is true for a screen showing one field whose view declared it', () => {
		const form = formOf(content('intro') + field('choice', '', radios));
		expect(advancesOnPick(collectScreens(form)[0])).toBe(true);
	});

	it('is false for a field that did not declare it', () => {
		const form = formOf(field('text'));
		expect(advancesOnPick(collectScreens(form)[0])).toBe(false);
	});

	it('is false for a fieldset, even one holding a field that declared it', () => {
		const form = formOf(fieldset('set', field('choice', '', radios) + field('text')));
		expect(advancesOnPick(collectScreens(form)[0])).toBe(false);
	});
});

describe('screenIndexOf and firstFocusable', () => {
	it('finds the screen holding a control, and the first control a screen can focus', () => {
		const form = formOf(field('a') + content('intro') + field('b', '', '<input type="hidden" name="h"><input disabled name="d"><select name="s"></select>'));
		const screens = collectScreens(form);
		const select = form.querySelector('select')!;
		expect(screenIndexOf(screens, select)).toBe(1);
		expect(firstFocusable(screens[1])).toBe(select);
	});
});
