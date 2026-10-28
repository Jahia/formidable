// @vitest-environment jsdom
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';
import {renderHook} from '~/utils/testSupport/reactRenderer';
import {ADVANCE_DELAY_MS, useScreens} from './useScreens';

/**
 * The screens of a One-at-a-time form, moved through by the visitor: the pause after a pick, the visibility a
 * prefill changes with no event, Enter and its guards, a reset, and the steps with nothing to show. React is mocked as a
 * small renderer (utils/testSupport/reactRenderer.ts).
 */
vi.mock('react', async () => (await import('~/utils/testSupport/reactRenderer')).reactModule);

const text = (name: string) => `<div data-fmdb-node-id="${name}" data-fmdb-node-name="${name}"><input name="${name}"></div>`;
const pickField = (name: string, attributes = '') => `<div data-fmdb-node-id="${name}" data-fmdb-node-name="${name}" ${attributes}>`
	+ `<fieldset data-fmdb-advance-on-change="true"><input type="radio" name="${name}" value="1"><input type="radio" name="${name}" value="2"></fieldset></div>`;
const confirmField = (name: string) => `<div data-fmdb-node-id="${name}" data-fmdb-node-name="${name}">`
	+ `<label data-fmdb-advance-on-change="true"><input type="checkbox" name="${name}" value="true"></label></div>`;
const hiddenField = (name: string) => `<div data-fmdb-node-id="${name}" data-fmdb-node-type="fmdb:inputHidden"><input type="hidden" name="${name}"></div>`;
const fieldset = (name: string, children: string) => `<div data-fmdb-node-id="${name}"><fieldset>${children}</fieldset></div>`;
const step = (name: string, children: string) => `<div data-fmdb-node-id="${name}"><div data-fmdb-step>${children}</div></div>`;

/** A form without steps has no visible steps: one array, as useMultiStep's state is between renders. */
const NO_STEPS: number[] = [];

interface MountOptions {
	/** The visible steps of a form with steps, whose markup holds `step()` elements; none for a form without. */
	steps?: number[];
	validate?: (element: HTMLElement) => boolean | Promise<boolean>;
}

/** The hook mounted on a form; `active()` names the screen on display, `currentStep()` the step useMultiStep shows. */
function mount(markup: string, {steps, validate: check = () => true}: MountOptions = {}) {
	document.body.innerHTML = `<form>${markup}<div class="fmdb-form-actions"><button type="submit">Submit</button></div></form>`;
	const form = document.querySelector('form')!;
	// One ref for the life of the form, as useRef gives: a new object per render would re-run every effect
	const formRef = {current: form};
	const validate = vi.fn(check);
	// useMultiStep's state, and its setter, stable as React's (useMultiStep.ts): the next render reads the step
	let step = steps?.[0] ?? 0;
	const setCurrentStep = (next: number) => {
		step = next;
	};
	const {latest, flush} = renderHook(() => useScreens({
		formRef,
		enabled: true,
		isMultiStep: steps !== undefined,
		currentStep: step,
		visibleStepIndices: steps ?? NO_STEPS,
		setCurrentStep,
		validate,
		advanceHintId: 'hint',
	}));
	// A step change is a new render: the renderer renders once more, with the step it was given
	flush();
	const active = () => Array.from(form.querySelectorAll<HTMLElement>('[data-fmdb-screen-active]')).map(wrapper => wrapper.dataset.fmdbNodeId);
	const input = (name: string) => form.querySelector<HTMLInputElement>(`input[name="${name}"]`)!;
	/** A pointer pick: the press that says it is the visitor's, then the click. */
	const pick = (name: string) => {
		const control = input(name);
		control.dispatchEvent(new Event('pointerdown', {bubbles: true}));
		control.click();
	};
	const press = (target: HTMLElement, key: string, init: KeyboardEventInit = {}) =>
		target.dispatchEvent(new KeyboardEvent('keydown', {key, bubbles: true, cancelable: true, ...init}));
	const next = async () => {
		await latest().handleNext();
		flush();
	};
	/** Lets every timer run, then renders what they changed. */
	const settle = async () => {
		await vi.runAllTimersAsync();
		flush();
		flush();
	};
	return {form, latest, flush, active, input, pick, press, next, settle, validate, currentStep: () => step};
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
	it('moves on after a pick on the last screen that opens a screen after it', async () => {
		const {form, active, input, pick, next, settle} = mount(text('a') + pickField('b') + text('c').replace('<div', '<div data-fmdb-logic-hidden="true"'));
		await next();
		expect(active()).toEqual(['b']);
		// what the logic does on the change that follows the click
		input('b').addEventListener('change', () => {
			form.querySelector<HTMLElement>('[data-fmdb-node-id="c"]')!.dataset.fmdbLogicHidden = 'false';
		});

		pick('b');
		await settle();
		expect(active()).toEqual(['c']);
	});

	it('stays after a pick on the last screen that opens nothing', async () => {
		const {active, pick, next, settle, validate} = mount(text('a') + pickField('b'));
		await next();
		validate.mockClear();

		pick('b');
		await settle();
		expect(active()).toEqual(['b']);
		expect(validate).not.toHaveBeenCalled();
	});

	it('stays when a click unchecks the box, and moves on when it checks it', async () => {
		const {active, input, pick, settle} = mount(confirmField('ok') + text('b'));
		input('ok').checked = true;
		pick('ok');
		await settle();
		expect(active()).toEqual(['ok']);

		pick('ok');
		await settle();
		expect(active()).toEqual(['b']);
	});

	it('counts a click after Space as a pick, and a click with no press as none (an arrow key)', async () => {
		// a clock at zero, as a test's frozen clock reads: "no press yet" must not read as a press at zero
		vi.setSystemTime(0);
		const {active, input, press, settle} = mount(pickField('a') + text('b'));
		input('a').click();
		await settle();
		expect(active()).toEqual(['a']);

		press(input('a'), ' ');
		input('a').click();
		await settle();
		expect(active()).toEqual(['b']);
	});

	it('does not move on with a pick in a fieldset', async () => {
		const {active, pick, settle} = mount(fieldset('set', pickField('a') + text('x')) + text('b'));
		pick('a');
		await settle();
		expect(active()).toEqual(['set']);
	});

	it('points at the hint from the zone of the screen that moves on with a pick, and from no other', async () => {
		const {form, next} = mount(pickField('a') + fieldset('set', pickField('inner')) + pickField('last'));
		const zoneOf = (name: string) => form.querySelector<HTMLElement>(`input[name="${name}"]`)!.closest('fieldset')!;
		expect(zoneOf('a').getAttribute('aria-describedby')).toBe('hint');
		expect(zoneOf('inner').hasAttribute('aria-describedby')).toBe(false);
		expect(zoneOf('last').hasAttribute('aria-describedby')).toBe(false);

		await next();
		await next();
		// the last screen: a pick there submits nothing and moves nowhere
		expect(zoneOf('a').hasAttribute('aria-describedby')).toBe(false);
		expect(zoneOf('last').hasAttribute('aria-describedby')).toBe(false);
	});

	it('writes no marker on a keystroke that changes no screen', async () => {
		const {form, input} = mount(text('a') + text('b'));
		const writes: MutationRecord[] = [];
		const observer = new MutationObserver(records => writes.push(...records));
		observer.observe(form, {subtree: true, attributes: true, attributeFilter: ['data-fmdb-screen', 'data-fmdb-screen-active', 'enterkeyhint']});

		input('a').value = 'x';
		input('a').dispatchEvent(new Event('input', {bubbles: true}));
		await Promise.resolve();
		observer.disconnect();
		expect(writes).toEqual([]);
	});

	describe('Enter', () => {
		it('sends the form from the last screen, unless Submit is disabled', async () => {
			const {form, input, press, next, settle} = mount(text('a') + text('b'));
			const submit = form.querySelector<HTMLButtonElement>('button[type="submit"]')!;
			// a disabled button dispatches no click event: the call itself is what is asserted
			const clicks = vi.spyOn(submit, 'click').mockImplementation(() => undefined);
			await next();

			submit.disabled = true;
			press(input('b'), 'Enter');
			await settle();
			expect(clicks).not.toHaveBeenCalled();

			submit.disabled = false;
			press(input('b'), 'Enter');
			await settle();
			expect(clicks).toHaveBeenCalledTimes(1);
		});

		it('is left to a button, and to a composition in progress', async () => {
			const {form, active, input, press, settle} = mount(text('a') + text('b') + '<button type="button" class="previous">Previous</button>');
			press(form.querySelector<HTMLElement>('.previous')!, 'Enter');
			await settle();
			expect(active()).toEqual(['a']);

			press(input('a'), 'Enter', {isComposing: true});
			await settle();
			expect(active()).toEqual(['a']);
		});
	});

	describe('Next', () => {
		it('moves once while its validation waits, whatever is asked meanwhile', async () => {
			let release!: (valid: boolean) => void;
			const {active, latest, flush} = mount(text('a') + text('b') + text('c'), {validate: () => new Promise<boolean>(resolve => {
				release = resolve;
			})});
			const first = latest().handleNext();
			const second = latest().handleNext();
			release(true);
			await Promise.all([first, second]);
			flush();
			expect(active()).toEqual(['b']);
		});

		it('is dropped when Previous moved the visitor while its validation waited', async () => {
			let release: (valid: boolean) => void = () => undefined;
			let deferred = false;
			const {active, latest, flush, next} = mount(text('a') + text('b') + text('c'), {validate: () => deferred
				? new Promise<boolean>(resolve => {
					release = resolve;
				})
				: true});
			await next();
			deferred = true;
			const move = latest().handleNext();
			latest().handlePrevious();
			flush();
			release(true);
			await move;
			flush();
			expect(active()).toEqual(['a']);
		});
	});

	it('shows the screen holding a control, and says when it is already on screen', () => {
		const {active, input, latest, flush} = mount(text('a') + text('b'));
		expect(latest().showScreenOf(input('a'))).toBe(false);
		expect(latest().showScreenOf(input('b'))).toBe(true);
		flush();
		expect(active()).toEqual(['b']);
	});

	it('gives the focus to the first screen after a reset', async () => {
		const {form, input, next, settle} = mount(text('a') + text('b'));
		await next();
		form.querySelector<HTMLButtonElement>('button[type="submit"]')!.focus();

		form.dispatchEvent(new Event('reset'));
		await settle();
		expect(document.activeElement).toBe(input('a'));
	});

	describe('with steps', () => {
		const STEPS = [0, 1];

		it('passes a step with nothing to show, at the start and after a reset', async () => {
			const {form, active, currentStep, settle} = mount(step('s1', hiddenField('h')) + step('s2', text('b') + text('c')), {steps: STEPS});
			expect(currentStep()).toBe(1);
			expect(active()).toEqual(['b']);

			form.dispatchEvent(new Event('reset'));
			await settle();
			expect(currentStep()).toBe(1);
			expect(active()).toEqual(['b']);
		});

		it('starts again at the first screen of the first step after a reset on that step', async () => {
			const {form, active, next, settle} = mount(step('s1', text('a') + text('b')) + step('s2', text('c')), {steps: STEPS});
			await next();
			expect(active()).toEqual(['b']);

			form.dispatchEvent(new Event('reset'));
			await settle();
			expect(active()).toEqual(['a']);
		});
	});
});
