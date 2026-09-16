// @vitest-environment jsdom
import {describe, expect, it} from 'vitest';
import {dispatchOnForm, formAttribute, resetForm} from './formDom';

/**
 * Every read of a form, against the control that shadows it. `HTMLFormElement` is
 * `[LegacyOverrideBuiltIns]`, so a field named `action`, `reset` — or `getAttribute`, or
 * `dispatchEvent` — becomes an own property of the form and hides the real one.
 *
 * jsdom does not implement that exposure, so it is reproduced here the way a browser presents it:
 * the property answers the control. Without it these tests would pass whatever the helpers read.
 */
const formWith = (fieldName: string, markup = ''): HTMLFormElement => {
	document.body.innerHTML = `
		<form action="/submit" ${markup}>
			<input name="${fieldName}" value="whatever"/>
		</form>`;
	const form = document.querySelector('form') as HTMLFormElement;
	const shadowing = form.querySelector('input') as HTMLInputElement;
	Object.defineProperty(form, fieldName, {configurable: true, get: () => shadowing});
	return form;
};

describe('formDom', () => {
	it('reads an attribute through a control named getAttribute', () => {
		expect(formAttribute(formWith('getAttribute'), 'action')).toBe('/submit');
	});

	it('reads an attribute through a control named like that attribute', () => {
		expect(formAttribute(formWith('action'), 'action')).toBe('/submit');
	});

	it('answers null for no form and for a missing attribute', () => {
		expect(formAttribute(null, 'action')).toBeNull();
		expect(formAttribute(undefined, 'action')).toBeNull();
		expect(formAttribute(document.createElement('form'), 'action')).toBeNull();
	});

	it('fires an event through a control named dispatchEvent', () => {
		const form = formWith('dispatchEvent');
		const seen: string[] = [];
		form.addEventListener('formidable:submitted', event => seen.push((event as CustomEvent).type));

		dispatchOnForm(form, new CustomEvent('formidable:submitted', {bubbles: true}));

		expect(seen).toEqual(['formidable:submitted']);
	});

	it('empties the controls through a control named reset', () => {
		const form = formWith('reset');
		const field = form.querySelector('input') as HTMLInputElement;
		field.value = 'typed by the visitor';

		resetForm(form);

		expect(field.value).toBe('whatever');
	});
});
