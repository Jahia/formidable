// @vitest-environment jsdom
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';
import {renderHook} from '~/utils/testSupport/reactRenderer';
import {ADVANCE_DELAY_MS, useScreens} from './useScreens';

/**
 * The screens of a One-at-a-time form without steps, moved through by the visitor: the pause after a pick,
 * the visibility a prefill changes with no event, Enter that a field keeps, and a reset. React is mocked as a
 * small renderer (utils/testSupport/reactRenderer.ts).
 */
vi.mock('react', async () => (await import('~/utils/testSupport/reactRenderer')).reactModule);

const text = (name: string) => `<div data-fmdb-node-id="${name}" data-fmdb-node-name="${name}"><input name="${name}"></div>`;
const pickField = (name: string) => `<div data-fmdb-node-id="${name}" data-fmdb-node-name="${name}">`
	+ `<fieldset data-fmdb-advance-on-change="true"><input type="radio" name="${name}" value="1"><input type="radio" name="${name}" value="2"></fieldset></div>`;

/** A form without steps has no visible steps: one array, as useMultiStep's state is between renders. */
const NO_STEPS: number[] = [];

/** The hook mounted on a form without steps; `active()` names the screen on display. */
function mount(markup: string) {
	document.body.innerHTML = `<form>${markup}<div class="fmdb-form-actions"><button type="submit">Submit</button></div></form>`;
	const form = document.querySelector('form')!;
	// One ref for the life of the form, as useRef gives: a new object per render would re-run every effect
	const formRef = {current: form};
	const validate = vi.fn(() => true);
	const {latest, flush} = renderHook(() => useScreens({
		formRef,
		enabled: true,
		isMultiStep: false,
		currentStep: 0,
		visibleStepIndices: NO_STEPS,
		setCurrentStep: () => undefined,
		validate,
		advanceHintId: 'hint',
	}));
	const active = () => Array.from(form.querySelectorAll<HTMLElement>('[data-fmdb-screen-active]')).map(wrapper => wrapper.dataset.fmdbNodeId);
	/** A pointer pick of a radio: the press that says it is the visitor's, then the click. */
	const pick = (name: string) => {
		const radio = form.querySelector<HTMLInputElement>(`input[name="${name}"]`)!;
		radio.dispatchEvent(new Event('pointerdown', {bubbles: true}));
		radio.click();
	};
	const next = async () => {
		await latest().handleNext();
		flush();
	};
	return {form, latest, flush, active, pick, next, validate};
}

describe('useScreens', () => {
	beforeEach(() => {
		vi.useFakeTimers();
	});

	afterEach(() => {
		vi.useRealTimers();
	});

	it('moves once when Next follows a pick within its pause: the pick\'s move is dropped', async () => {
		const {active, pick, next, flush} = mount(text('a') + pickField('b') + text('c') + text('d'));
		await next();
		expect(active()).toEqual(['b']);

		pick('b');
		await next();
		expect(active()).toEqual(['c']);

		await vi.advanceTimersByTimeAsync(2 * ADVANCE_DELAY_MS);
		flush();
		expect(active()).toEqual(['c']);
	});

	it('stays where Previous took the visitor when it follows a pick within its pause', async () => {
		const {active, pick, next, latest, flush} = mount(text('a') + pickField('b') + text('c'));
		await next();
		pick('b');
		latest().handlePrevious();
		flush();
		expect(active()).toEqual(['a']);

		await vi.advanceTimersByTimeAsync(2 * ADVANCE_DELAY_MS);
		flush();
		expect(active()).toEqual(['a']);
	});

	it('moves on once the pause after a pick is over', async () => {
		const {active, pick, next, flush} = mount(text('a') + pickField('b') + text('c'));
		await next();
		pick('b');
		expect(active()).toEqual(['b']);

		await vi.advanceTimersByTimeAsync(ADVANCE_DELAY_MS);
		flush();
		expect(active()).toEqual(['c']);
	});

	it('leaves a screen a prefill hides with no event, and Next then skips nothing', async () => {
		const {form, active, next, flush} = mount(text('a') + text('b') + text('c'));
		expect(active()).toEqual(['a']);

		// what the prefill script does after its input and change events: the marker alone
		form.querySelector<HTMLElement>('[data-fmdb-node-id="a"]')!.dataset.fmdbPrefilled = 'hidden';
		await Promise.resolve();
		flush();
		expect(active()).toEqual(['b']);

		await next();
		expect(active()).toEqual(['c']);
	});

	it('leaves Enter to a field that handled it, and moves on otherwise', async () => {
		const {form, active, flush} = mount(text('a') + text('b'));
		const input = form.querySelector<HTMLInputElement>('input[name="a"]')!;
		const keeper = (event: KeyboardEvent) => event.preventDefault();
		input.addEventListener('keydown', keeper);

		input.dispatchEvent(new KeyboardEvent('keydown', {key: 'Enter', bubbles: true, cancelable: true}));
		await vi.runAllTimersAsync();
		flush();
		expect(active()).toEqual(['a']);

		input.removeEventListener('keydown', keeper);
		input.dispatchEvent(new KeyboardEvent('keydown', {key: 'Enter', bubbles: true, cancelable: true}));
		await vi.runAllTimersAsync();
		flush();
		expect(active()).toEqual(['b']);
	});

	it('starts again at the first screen after a reset', async () => {
		const {form, active, next, flush} = mount(text('a') + text('b') + text('c'));
		await next();
		await next();
		expect(active()).toEqual(['c']);

		form.dispatchEvent(new Event('reset'));
		await vi.runAllTimersAsync();
		flush();
		expect(active()).toEqual(['a']);
	});
});
