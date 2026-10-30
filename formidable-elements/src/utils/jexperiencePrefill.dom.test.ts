// @vitest-environment jsdom
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';
import {applyConditionalLogicVisibility, parseConditionalLogicRules} from './conditionalLogic';
import source from '../../../formidable-jexperience-engine/src/main/resources/javascript/formidable-jxp.js?raw';

/**
 * The jExperience prefill script against the conditional logic, on the markup the views render: a field
 * mapped to a profile property and prefilled, and a field rule on it that shows "Company size"
 * (docs/architecture/jexperience-integration.md, "Driving conditional logic from the profile"). The script
 * belongs to formidable-jexperience-engine, which has no JS toolchain; it is read from there, so a rename
 * breaks this test. The tracker is a stub: the CI lab has no jExperience.
 */

const FORM_ID = 'a1b2c3d4-0000-4000-8000-000000000422';

interface Fixture {
	/** How the source field renders: a select, radios, or a text input. */
	shape?: 'select' | 'radio' | 'text';
	/** What follows the write, as the block carries it; absent is "editable". */
	then?: 'readOnly' | 'hidden';
	/** The loaded profile's properties; undefined is no tracker on the page. */
	profile?: Record<string, unknown>;
	/** The tracker is there but has not run its callbacks yet: the context arrives later. */
	contextLater?: boolean;
	/** The visitor's "disable tracking". */
	activateWem?: boolean;
}

const sourceMarkup = (shape: Fixture['shape']) => {
	switch (shape) {
		case 'radio':
			return '<div role="radiogroup">'
				+ '<input type="radio" name="country" value="FR"><input type="radio" name="country" value="DE"></div>';
		case 'text':
			return '<input type="text" name="country">';
		default:
			return '<select name="country"><option value=""></option><option value="FR">France</option><option value="DE">Germany</option></select>';
	}
};

/** The rule as the author stores it, rendered on the target the way LogicAwareRender does. */
const ruleFor = (shape: Fixture['shape']) => JSON.stringify(parseConditionalLogicRules([JSON.stringify(
	shape === 'text'
		? {logicId: 'l1', sourceFieldName: 'country', valueKind: 'text', operator: 'equals', value: 'FR'}
		: {logicId: 'l1', sourceFieldName: 'country', valueKind: 'choice', operator: 'in', values: ['FR']}
)]));

/** The document listeners each script instance adds; removed after the test, or a stale instance would prefill the next form. */
let documentListeners: Array<[string, EventListenerOrEventListenerObject]> = [];

function mount({shape = 'select', then, profile, contextLater = false, activateWem}: Fixture = {}) {
	const block = {formId: FORM_ID, name: 'Contact', path: '/sites/mysite/contents/contact', prefill: {country: {property: 'country', ...(then ? {then} : {})}}};
	document.body.innerHTML = `<script type="application/json" data-formidable-jxp="${FORM_ID}">${JSON.stringify(block)}</script>`
		+ `<form name="${FORM_ID}">`
		+ `<div data-fmdb-node-id="n-country" data-fmdb-node-name="country">${sourceMarkup(shape)}</div>`
		+ '<div class="fmdb-logic-target" style="display: none" aria-hidden="true" data-fmdb-logic-hidden="true" data-fmdb-node-id="n-size" data-fmdb-node-name="companySize">'
		+ '<input type="text" name="companySize"></div>'
		+ '</form>';
	const form = document.querySelector('form')!;
	form.querySelector<HTMLElement>('[data-fmdb-node-name="companySize"]')!.dataset.fmdbLogics = ruleFor(shape);

	// The tracker jExperience puts in the head; its callbacks are kept, so that a test can load the context.
	const callbacks: Array<() => void> = [];
	if (profile) {
		Object.assign(window, {
			wem: {
				getLoadedContext: () => ({profileId: 'p1', profileProperties: profile}),
				_registerCallback: (callback: () => void) => callbacks.push(callback)
			},
			wemLoaded: !contextLater
		});
	}
	Object.assign(window, {digitalDataOverrides: [], digitalData: activateWem === undefined ? undefined : {wemInitConfig: {activateWem}}});

	// The logic wired as useMultiStep wires it.
	const syncVisibility = () => applyConditionalLogicVisibility(form);
	syncVisibility();
	form.addEventListener('input', syncVisibility);
	form.addEventListener('change', syncVisibility);
	form.addEventListener('reset', () => setTimeout(syncVisibility, 0));

	const addEventListener = vi.spyOn(document, 'addEventListener');
	window.eval(source);
	documentListeners = addEventListener.mock.calls.map(([type, listener]) => [type, listener]);
	addEventListener.mockRestore();

	// The island takes the form over, then says so.
	form.noValidate = true;
	form.dispatchEvent(new CustomEvent('formidable:ready', {bubbles: true, detail: {formId: FORM_ID}}));

	const wrapper = (name: string) => form.querySelector<HTMLElement>(`[data-fmdb-node-name="${name}"]`)!;
	return {
		form,
		source: wrapper('country'),
		target: wrapper('companySize'),
		control: form.elements.namedItem('country') as HTMLSelectElement | HTMLInputElement,
		loadContext: () => {
			Object.assign(window, {wemLoaded: true});
			callbacks.forEach(callback => callback());
		}
	};
}

const shown = (wrapper: HTMLElement) => wrapper.style.display !== 'none' && wrapper.dataset.fmdbLogicHidden === 'false';

/** What a script does: a change no browser marks as the visitor's. */
const pick = (control: HTMLSelectElement | HTMLInputElement, value: string) => {
	control.value = value;
	control.dispatchEvent(new Event('change', {bubbles: true}));
};

beforeEach(() => {
	vi.spyOn(console, 'warn').mockImplementation(() => undefined);
});

afterEach(() => {
	documentListeners.forEach(([type, listener]) => document.removeEventListener(type, listener));
	documentListeners = [];
	for (const key of ['formidableJxp', 'wem', 'wemLoaded', 'digitalData', 'digitalDataOverrides']) {
		Reflect.deleteProperty(window, key);
	}
	document.body.innerHTML = '';
	vi.restoreAllMocks();
});

describe('a rule on a prefilled field', () => {
	it('shows its target when the profile names the value, the source out of sight but still submitted', () => {
		const {form, source, target, control} = mount({profile: {country: 'FR'}, then: 'hidden'});

		expect((window as unknown as {digitalDataOverrides: unknown[]}).digitalDataOverrides)
			.toEqual([{wemInitConfig: {requiredProfileProperties: ['country']}}]);
		expect(control.value).toBe('FR');
		expect(source.dataset.fmdbPrefilled).toBe('hidden');
		expect(source.style.display).toBe('none');
		expect(shown(target)).toBe(true);
		// hidden by the prefill, not by the logic: enabled, so the value is posted and the server reads it
		expect(source.dataset.fmdbLogicHidden).not.toBe('true');
		expect(control.disabled).toBe(false);
		expect(new FormData(form).get('country')).toBe('FR');
	});

	it('keeps its target hidden when the profile names another value', () => {
		const {control, target} = mount({profile: {country: 'DE'}, then: 'hidden'});

		expect(control.value).toBe('DE');
		expect(shown(target)).toBe(false);
	});

	it('leaves the source to the visitor when the profile has no value, and follows the answer', () => {
		const {form, source, target, control} = mount({profile: {firstName: 'Ada'}, then: 'hidden'});

		expect(control.value).toBe('');
		expect(source.style.display).not.toBe('none');
		expect('fmdbPrefilled' in source.dataset).toBe(false);
		expect('fmdbPrefilled' in form.dataset).toBe(false);
		expect(shown(target)).toBe(false);

		pick(control, 'FR');
		expect(shown(target)).toBe(true);
	});

	it.each([
		['no tracker on the page', {}],
		['tracking disabled by the visitor', {profile: {country: 'FR'}, activateWem: false}]
	])('writes nothing with %s, and follows the answer', (unusedCase, fixture: Fixture) => {
		const {form, source, target, control} = mount({...fixture, then: 'hidden'});

		expect(control.value).toBe('');
		expect('fmdbPrefilled' in source.dataset).toBe(false);
		expect('fmdbPrefilled' in form.dataset).toBe(false);
		expect(shown(target)).toBe(false);

		pick(control, 'FR');
		expect(shown(target)).toBe(true);
	});

	it('keeps the answer the visitor gave before the context arrived', () => {
		// Radios: a click is the one gesture jsdom marks as trusted (its `input` and `change` come from the
		// activation behaviour), and trust is what the touched guard reads. A select gets no such event here.
		const {form, target, loadContext} = mount({shape: 'radio', profile: {country: 'FR'}, contextLater: true});
		const radio = (value: string) => form.querySelector<HTMLInputElement>(`input[value="${value}"]`)!;

		radio('DE').click();
		expect(shown(target)).toBe(false);

		loadContext();
		expect(radio('DE').checked).toBe(true);
		expect(radio('FR').checked).toBe(false);
		expect(shown(target)).toBe(false);
	});

	it('prefills the source once the context arrives, when the visitor has not answered', () => {
		const {form, target, loadContext} = mount({shape: 'radio', profile: {country: 'FR'}, contextLater: true});

		expect(shown(target)).toBe(false);
		loadContext();
		expect(form.querySelector<HTMLInputElement>('input[value="FR"]')!.checked).toBe(true);
		expect(shown(target)).toBe(true);
	});

	it('read-only: the target follows the prefilled value, not a change the lock puts back', () => {
		const {source, target, control} = mount({profile: {country: 'FR'}, then: 'readOnly'});

		expect(source.dataset.fmdbPrefilled).toBe('readonly');
		expect(control.getAttribute('aria-readonly')).toBe('true');
		expect(shown(target)).toBe(true);

		pick(control, 'DE');
		expect(control.value).toBe('FR');
		expect(shown(target)).toBe(true);
	});

	it('a reset gives the source back empty, then the prefill runs again and the target follows', async () => {
		// Radios: jsdom keeps a select's selectedOptions from before a reset, which the logic reads.
		const {form, source, target} = mount({shape: 'radio', profile: {country: 'FR'}, then: 'hidden'});
		const france = form.querySelector<HTMLInputElement>('input[value="FR"]')!;
		expect(france.checked).toBe(true);
		expect(shown(target)).toBe(true);

		form.reset();
		await new Promise(resolve => setTimeout(resolve, 0));
		expect(france.checked).toBe(false);
		expect('fmdbPrefilled' in source.dataset).toBe(false);
		expect(source.style.display).not.toBe('none');
		expect(shown(target)).toBe(false);

		// what the island says once the values are back
		form.dispatchEvent(new CustomEvent('formidable:reset', {bubbles: true, detail: {formId: FORM_ID}}));
		expect(france.checked).toBe(true);
		expect(source.dataset.fmdbPrefilled).toBe('hidden');
		expect(shown(target)).toBe(true);
	});

	it('works the same on a text source', () => {
		const {control, target} = mount({shape: 'text', profile: {country: 'FR'}});

		expect(control.value).toBe('FR');
		expect(shown(target)).toBe(true);

		pick(control, 'DE');
		expect(shown(target)).toBe(false);
	});
});
