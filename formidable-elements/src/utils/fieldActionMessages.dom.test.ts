// @vitest-environment jsdom
import {beforeEach, describe, expect, it} from 'vitest';
import {anchorFieldMessages, parseFieldMessages, plainText, showFieldMessages} from './fieldActionMessages';

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

const error = (field: string, html: string) => ({level: 'error' as const, html, field});
const warning = (field: string, html: string) => ({level: 'warning' as const, html, field});

describe('parseFieldMessages', () => {
	it('reads the messages array of an engine answer, an unknown level read as an error', () => {
		expect(parseFieldMessages({verdict: 'reject', messages: [
			{level: 'error', html: 'no', field: 'email'},
			{level: 'warning', html: 'hmm', field: 'email'},
			{level: 'odd', html: 'x', field: 'name'}
		]})).toEqual([error('email', 'no'), warning('email', 'hmm'), error('name', 'x')]);
	});

	it('answers nothing for any other shape, and drops an entry without a field or a text', () => {
		expect(parseFieldMessages(null)).toEqual([]);
		expect(parseFieldMessages({success: false})).toEqual([]);
		expect(parseFieldMessages({messages: 'no'})).toEqual([]);
		expect(parseFieldMessages({messages: [null, 3, {level: 'error', html: 'x'}, {field: 'a', html: 3}]})).toEqual([]);
	});
});

describe('plainText', () => {
	it('strips the markup and folds the whitespace of the contributor message', () => {
		expect(plainText('<p>The word <b>spam</b>\n is  refused.</p>')).toBe('The word spam is refused.');
		expect(plainText('')).toBe('');
	});
});

describe('showFieldMessages', () => {
	beforeEach(() => {
		document.body.innerHTML = '';
	});

	it('blocks on an error: customValidity with the text, the HTML under the field, the control marked invalid', () => {
		const form = formOf('<div class="fmdb-form-group"><input name="email"/></div>');
		const input = form.querySelector('input')!;

		showFieldMessages([input], [error('email', 'We do <b>not</b> know this address')]);

		expect(input.validity.customError).toBe(true);
		expect(input.validationMessage).toBe('We do not know this address');
		const shown = form.querySelector('.fmdb-form-group > .fmdb-validation-error')!;
		expect(shown.innerHTML).toBe('We do <b>not</b> know this address');
		expect(input.classList.contains('fmdb-invalid')).toBe(true);
		expect(input.getAttribute('aria-describedby')).toBe(shown.id);
		expect(form.querySelector('.fmdb-validation-warning')).toBeNull();
	});

	it('advises on a warning: the message shown, the control left valid', () => {
		const form = formOf('<div class="fmdb-form-group"><input name="email"/></div>');
		const input = form.querySelector('input')!;

		showFieldMessages([input], [warning('email', 'Unusual domain')]);

		expect(input.validity.valid).toBe(true);
		expect(input.classList.contains('fmdb-invalid')).toBe(false);
		expect(form.querySelector('.fmdb-validation-error')).toBeNull();
		const shown = form.querySelector('.fmdb-form-group > .fmdb-validation-warning')!;
		expect(shown.innerHTML).toBe('Unusual domain');
		expect(shown.getAttribute('role')).toBe('status');
		expect(input.getAttribute('aria-describedby')).toBe(shown.id);
	});

	it('clears both on no message, and replaces rather than stacks on a new one', () => {
		const form = formOf('<div class="fmdb-form-group"><input name="email"/></div>');
		const input = form.querySelector('input')!;
		showFieldMessages([input], [error('email', 'first'), warning('email', 'careful')]);
		showFieldMessages([input], [error('email', 'second')]);
		expect(form.querySelectorAll('.fmdb-validation-error')).toHaveLength(1);
		expect(form.querySelector('.fmdb-validation-error')!.textContent).toBe('second');
		expect(form.querySelector('.fmdb-validation-warning')).toBeNull();

		showFieldMessages([input], []);

		expect(input.validity.valid).toBe(true);
		expect(form.querySelector('.fmdb-validation-error')).toBeNull();
		expect(input.hasAttribute('aria-describedby')).toBe(false);
	});

	it('marks every control of a group and shows the message once', () => {
		const form = formOf(`<div class="fmdb-form-group">
			<input type="checkbox" name="topics" value="a"/><input type="checkbox" name="topics" value="b"/>
		</div>`);
		const boxes = Array.from(form.querySelectorAll('input'));

		showFieldMessages(boxes, [error('topics', 'not b')]);

		expect(boxes.every(box => box.validity.customError)).toBe(true);
		expect(form.querySelectorAll('.fmdb-validation-error')).toHaveLength(1);
	});

	it('still blocks when the message has no text: the validity message is never empty', () => {
		const form = formOf('<input name="email"/>');
		const input = form.querySelector('input')!;
		showFieldMessages([input], [error('email', '')]);
		expect(input.validity.customError).toBe(true);
	});
});

describe('anchorFieldMessages', () => {
	beforeEach(() => {
		document.body.innerHTML = '';
	});

	it('anchors each message on its field and hands back the first refused control', () => {
		const form = formOf(`
			<div class="fmdb-form-group"><input name="firstName"/></div>
			<div class="fmdb-form-group"><input name="email"/></div>`);
		const [firstName, email] = Array.from(form.querySelectorAll('input'));

		const refused = anchorFieldMessages(form, [warning('firstName', 'short'), error('email', 'unknown')]);

		expect(refused).toBe(email);
		expect(firstName.validity.valid).toBe(true);
		expect(form.querySelector('.fmdb-validation-warning')!.textContent).toBe('short');
		expect(email.validity.customError).toBe(true);
	});

	it('answers null when no message found its field, so the caller keeps its own message', () => {
		const form = formOf('<div class="fmdb-form-group"><input name="email"/></div>');
		expect(anchorFieldMessages(form, [error('phone', 'no such field here')])).toBeNull();
		expect(form.querySelector('.fmdb-validation-error')).toBeNull();
	});
});
