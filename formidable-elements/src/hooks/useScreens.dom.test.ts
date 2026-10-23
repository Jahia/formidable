// @vitest-environment jsdom
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';
import {ADVANCE_DELAY_MS, useScreens} from './useScreens';

/**
 * The screens of a One-at-a-time form without steps, moved through by the visitor: the pause after a pick,
 * the visibility a prefill changes with no event, Enter that a field keeps, and a reset. React is mocked as
 * the small renderer of useMultiStep.dom.test.ts: state and refs kept per slot, an effect run once its
 * dependencies changed, a setter that marks the tree dirty and `flush()` that renders again.
 */
const react = vi.hoisted(() => ({
	states: [] as unknown[],
	refs: [] as Array<{current: unknown}>,
	effects: [] as Array<{deps: unknown[] | undefined; cleanup: void | (() => void)}>,
	memos: [] as Array<{deps: unknown[] | undefined; value: unknown}>,
	cursor: {state: 0, ref: 0, effect: 0, memo: 0},
	queued: [] as Array<() => void>,
	dirty: false,
	changed: (deps: unknown[] | undefined, previous: unknown[] | undefined): boolean => {
		if (deps === undefined || previous === undefined) return true;
		return deps.length !== previous.length || deps.some((dep, i) => !Object.is(dep, previous[i]));
	},
}));
vi.mock('react', () => ({
	useState: <T,>(initial: T | (() => T)) => {
		const slot = react.cursor.state++;
		if (!(slot in react.states)) react.states[slot] = typeof initial === 'function' ? (initial as () => T)() : initial;
		const set = (next: T | ((previous: T) => T)) => {
			const previous = react.states[slot] as T;
			const value = typeof next === 'function' ? (next as (previous: T) => T)(previous) : next;
			if (Object.is(value, previous)) return;
			react.states[slot] = value;
			react.dirty = true;
		};
		return [react.states[slot] as T, set];
	},
	useRef: <T,>(initial: T) => {
		const slot = react.cursor.ref++;
		react.refs[slot] ??= {current: initial};
		return react.refs[slot];
	},
	useEffect: (effect: () => void | (() => void), deps?: unknown[]) => {
		const slot = react.cursor.effect++;
		const previous = react.effects[slot];
		if (previous && !react.changed(deps, previous.deps)) return;
		react.queued.push(() => {
			previous?.cleanup?.();
			react.effects[slot] = {deps, cleanup: effect()};
		});
	},
	useCallback: <T,>(callback: T, deps?: unknown[]) => {
		const slot = react.cursor.memo++;
		const previous = react.memos[slot];
		if (previous && !react.changed(deps, previous.deps)) return previous.value as T;
		react.memos[slot] = {deps, value: callback};
		return callback;
	},
}));

const text = (name: string) => `<div data-fmdb-node-id="${name}" data-fmdb-node-name="${name}"><input name="${name}"></div>`;
const pickField = (name: string) => `<div data-fmdb-node-id="${name}" data-fmdb-node-name="${name}">`
	+ `<fieldset data-fmdb-advance-on-change="true"><input type="radio" name="${name}" value="1"><input type="radio" name="${name}" value="2"></fieldset></div>`;

/** A form without steps has no visible steps: one array, as useMultiStep's state is between renders. */
const NO_STEPS: number[] = [];

/** The hook mounted on a form without steps; `active()` names the screen on display. */
function mount(markup: string) {
	Object.assign(react, {states: [], refs: [], effects: [], memos: [], queued: [], dirty: false});
	document.body.innerHTML = `<form>${markup}<div class="fmdb-form-actions"><button type="submit">Submit</button></div></form>`;
	const form = document.querySelector('form')!;
	// One ref for the life of the form, as useRef gives: a new object per render would re-run every effect
	const formRef = {current: form};
	const validate = vi.fn(() => true);
	let hook!: ReturnType<typeof useScreens>;
	const render = () => {
		react.cursor = {state: 0, ref: 0, effect: 0, memo: 0};
		react.dirty = false;
		hook = useScreens({
			formRef,
			enabled: true,
			isMultiStep: false,
			currentStep: 0,
			visibleStepIndices: NO_STEPS,
			setCurrentStep: () => undefined,
			validate,
			advanceHintId: 'hint',
		});
		react.queued.splice(0).forEach(run => run());
	};
	const flush = () => {
		for (let round = 0; round < 10; round++) {
			render();
			if (!react.dirty) return;
		}
		throw new Error('the hook keeps setting state');
	};
	flush();
	const active = () => Array.from(form.querySelectorAll<HTMLElement>('[data-fmdb-screen-active]')).map(wrapper => wrapper.dataset.fmdbNodeId);
	/** A pointer pick of a radio: the press that says it is the visitor's, then the click. */
	const pick = (name: string) => {
		const radio = form.querySelector<HTMLInputElement>(`input[name="${name}"]`)!;
		radio.dispatchEvent(new Event('pointerdown', {bubbles: true}));
		radio.click();
	};
	const next = async () => {
		await hook.handleNext();
		flush();
	};
	return {form, latest: () => hook, flush, active, pick, next, validate};
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
