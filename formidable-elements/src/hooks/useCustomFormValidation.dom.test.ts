// @vitest-environment jsdom
import {beforeEach, describe, expect, it, vi} from 'vitest';
import '~/utils/testSupport/cssEscape';
import {showFieldError} from '~/utils/validationUtils';
import {validateInputs} from './useCustomFormValidation';

// The hook's effect is not exercised here; React is mocked so the module loads without react-dom.
vi.mock('react', () => ({useEffect: () => undefined}));

const formOf = (html: string): HTMLFormElement => {
	document.body.innerHTML = `<form>${html}</form>`;
	return document.querySelector('form')!;
};

describe('validateInputs and an error another client drew', () => {
	beforeEach(() => {
		document.body.innerHTML = '';
	});

	it('keeps the HTML error of a control held invalid by a custom validity alone', () => {
		// A field action's refusal: the contributor's HTML under the field, the plain text in customValidity.
		const form = formOf('<div class="fmdb-form-group"><input name="email" value="ada@nowhere.test"/></div>');
		const input = form.querySelector('input')!;
		input.setCustomValidity('See the policy');
		showFieldError(input, 'See <a href="/policy">the policy</a>', {html: true});

		expect(validateInputs(form)).toBe(false);

		expect(form.querySelector('.fmdb-validation-error')!.innerHTML).toBe('See <a href="/policy">the policy</a>');
		expect(document.activeElement).toBe(input);
	});

	it('draws its own text when a constraint of the control fails, whatever was under it', () => {
		const form = formOf('<div class="fmdb-form-group"><input name="email" required data-fmdb-msg-value-missing="Please fill in your email"/></div>');
		const input = form.querySelector('input')!;
		input.setCustomValidity('See the policy');
		showFieldError(input, 'See <a href="/policy">the policy</a>', {html: true});

		expect(validateInputs(form)).toBe(false);

		expect(form.querySelector('.fmdb-validation-error')!.innerHTML).toBe('Please fill in your email');
	});

	it('draws the custom validity text when nothing was drawn yet', () => {
		const form = formOf('<div class="fmdb-form-group"><input name="email" value="x"/></div>');
		form.querySelector('input')!.setCustomValidity('Not this one');

		expect(validateInputs(form)).toBe(false);

		expect(form.querySelector('.fmdb-validation-error')!.textContent).toBe('Not this one');
	});
});
