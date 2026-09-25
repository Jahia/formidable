// @vitest-environment jsdom
import {beforeEach, describe, expect, it} from 'vitest';
import '~/utils/testSupport/cssEscape';
import {anchorFieldMessages, clearFieldActionValidity, fieldControlsIn, fieldControlsOf, parseFieldMessages, plainText, showFieldMessages} from './fieldActionMessages';

const formOf = (html: string): HTMLFormElement => {
	document.body.innerHTML = `<form>${html}</form>`;
	return document.querySelector('form')!;
};

const error = (field: string, html: string) => ({level: 'error' as const, html, field});
const warning = (field: string, html: string) => ({level: 'warning' as const, html, field});

/** The controls of the one field of a form, by name. */
const controlsOf = (form: HTMLFormElement, field = 'email') => fieldControlsOf(form, field);

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

describe('fieldControlsIn / fieldControlsOf', () => {
	beforeEach(() => {
		document.body.innerHTML = '';
	});

	it('names the controls carrying the value, the first visible one being the anchor', () => {
		const form = formOf('<div data-fmdb-node-name="email"><input name="email"/><input name="other"/></div>');
		const {named, anchor} = controlsOf(form);
		expect(named.map(control => control.name)).toEqual(['email']);
		expect(anchor).toBe(named[0]);
	});

	it('anchors a range field on its slider, its named control being a hidden mirror', () => {
		const form = formOf(`<div data-fmdb-node-name="budget">
			<input type="range" id="budget-slider"/><input type="hidden" name="budget" value="50"/>
		</div>`);
		const {named, anchor} = fieldControlsIn(form.querySelector('div')!, 'budget');
		expect(named.map(control => control.type)).toEqual(['hidden']);
		expect(anchor?.id).toBe('budget-slider');
	});

	it('finds the controls by name when the form renders no wrapper for the field', () => {
		const form = formOf('<input name="email"/>');
		const {named, anchor} = controlsOf(form);
		expect(named).toHaveLength(1);
		expect(anchor).toBe(named[0]);
		expect(controlsOf(form, 'phone')).toEqual({named: [], anchor: null});
	});
});

describe('showFieldMessages', () => {
	beforeEach(() => {
		document.body.innerHTML = '';
	});

	it('blocks on an error: customValidity with the text, the HTML under the field, the control marked invalid', () => {
		const form = formOf('<div class="fmdb-form-group"><input name="email"/></div>');
		const input = form.querySelector('input')!;

		showFieldMessages(controlsOf(form), [error('email', 'We do <b>not</b> know this address')]);

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

		showFieldMessages(controlsOf(form), [warning('email', 'Unusual domain')]);

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
		showFieldMessages(controlsOf(form), [error('email', 'first'), warning('email', 'careful')]);
		showFieldMessages(controlsOf(form), [error('email', 'second')]);
		expect(form.querySelectorAll('.fmdb-validation-error')).toHaveLength(1);
		expect(form.querySelector('.fmdb-validation-error')!.textContent).toBe('second');
		expect(form.querySelector('.fmdb-validation-warning')).toBeNull();

		showFieldMessages(controlsOf(form), []);

		expect(input.validity.valid).toBe(true);
		expect(form.querySelector('.fmdb-validation-error')).toBeNull();
		expect(input.hasAttribute('aria-describedby')).toBe(false);
	});

	it('marks every control of a group and shows the message once', () => {
		const form = formOf(`<div class="fmdb-form-group">
			<input type="checkbox" name="topics" value="a"/><input type="checkbox" name="topics" value="b"/>
		</div>`);
		const boxes = Array.from(form.querySelectorAll('input'));

		showFieldMessages(controlsOf(form, 'topics'), [error('topics', 'not b')]);

		expect(boxes.every(box => box.validity.customError)).toBe(true);
		expect(form.querySelectorAll('.fmdb-validation-error')).toHaveLength(1);
	});

	it('still blocks when the message has no text: the validity message is never empty', () => {
		const form = formOf('<input name="email"/>');
		const input = form.querySelector('input')!;
		showFieldMessages(controlsOf(form), [error('email', '')]);
		expect(input.validity.customError).toBe(true);
	});

	it('lifts its own validity only: a required group keeps its "select at least one"', () => {
		const form = formOf(`<div class="fmdb-form-group">
			<input type="checkbox" name="topics" value="a"/><input type="checkbox" name="topics" value="b"/>
		</div>`);
		const boxes = Array.from(form.querySelectorAll('input'));
		boxes.forEach(box => box.setCustomValidity('Select at least one'));

		showFieldMessages(controlsOf(form, 'topics'), []);
		clearFieldActionValidity(controlsOf(form, 'topics'));

		expect(boxes.every(box => box.validationMessage === 'Select at least one')).toBe(true);

		// its own refusal, then lifted: the group's message is not restored (the group's client sets it again on change)
		showFieldMessages(controlsOf(form, 'topics'), [error('topics', 'not a')]);
		expect(boxes[0].validationMessage).toBe('not a');
		clearFieldActionValidity(controlsOf(form, 'topics'));
		expect(boxes.every(box => box.validity.valid)).toBe(true);
	});

	it('marks the slider of a range field and describes it, the hidden mirror carrying the validity too', () => {
		const form = formOf(`<div class="fmdb-form-group" data-fmdb-node-name="budget">
			<input type="range" id="budget-slider"/><input type="hidden" name="budget" value="50"/>
		</div>`);
		const slider = form.querySelector<HTMLInputElement>('#budget-slider')!;
		const mirror = form.querySelector<HTMLInputElement>('input[type=hidden]')!;

		showFieldMessages(controlsOf(form, 'budget'), [error('budget', 'Too much')]);

		expect(slider.validity.customError).toBe(true);
		expect(mirror.validity.customError).toBe(true);
		expect(slider.getAttribute('aria-invalid')).toBe('true');
		expect(slider.getAttribute('aria-describedby')).toBe(form.querySelector('.fmdb-validation-error')!.id);
		expect(mirror.hasAttribute('aria-invalid')).toBe(false);
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

	it('hands back the slider of a refused range field, not its hidden mirror', () => {
		const form = formOf(`<div class="fmdb-form-group" data-fmdb-node-name="budget">
			<input type="range" id="budget-slider"/><input type="hidden" name="budget" value="50"/>
		</div>`);
		expect(anchorFieldMessages(form, [error('budget', 'Too much')])?.id).toBe('budget-slider');
	});

	it('answers null when no message found its field, so the caller keeps its own message', () => {
		const form = formOf('<div class="fmdb-form-group"><input name="email"/></div>');
		expect(anchorFieldMessages(form, [error('phone', 'no such field here')])).toBeNull();
		expect(form.querySelector('.fmdb-validation-error')).toBeNull();
	});
});
