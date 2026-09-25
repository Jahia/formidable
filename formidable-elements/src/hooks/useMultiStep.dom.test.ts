// @vitest-environment jsdom
import {describe, expect, it, vi} from 'vitest';
import {useMultiStep} from './useMultiStep';

/**
 * The step navigation once Next waits for an asynchronous validation — the field actions of the step.
 * React is mocked as in the other hook tests: `useState` a holder whose setter is recorded, `useRef` a
 * holder, `useEffect` run at once, `useCallback` the function itself; the hook runs as a function on a
 * form-less ref (the effects that read the form do nothing), which is all the navigation needs.
 */
const react = vi.hoisted(() => ({setters: [] as Array<ReturnType<typeof vi.fn>>}));
vi.mock('react', () => ({
	useState: <T,>(initial: T | (() => T)) => {
		const set = vi.fn();
		react.setters.push(set);
		return [typeof initial === 'function' ? (initial as () => T)() : initial, set];
	},
	useRef: <T,>(initial: T) => ({current: initial}),
	useEffect: (effect: () => void | (() => void)) => {
		effect();
	},
	useCallback: <T,>(callback: T) => callback,
}));

/** The hook on a three-step form; the current step's setter is the first `useState`. */
function threeSteps() {
	react.setters.length = 0;
	const hook = useMultiStep({formRef: {current: null}, stepIds: ['s1', 's2', 's3']});
	const [setCurrentStep] = react.setters;
	return {hook, movesTo: () => setCurrentStep.mock.calls.map(([step]) => step)};
}

/** A validation the test answers when it decides the provider has taken long enough. */
function pendingValidation() {
	let release: (value: boolean) => void = () => undefined;
	const validate = vi.fn(() => new Promise<boolean>(resolve => {
		release = resolve;
	}));
	return {validate, release: (value: boolean) => release(value)};
}

describe('useMultiStep: Next waits for the validation of the step', () => {
	it('moves on once the validation accepts, not before, and not at all when it refuses', async () => {
		const {hook, movesTo} = threeSteps();
		const {validate, release} = pendingValidation();

		const next = hook.handleNext(validate);
		expect(movesTo()).toEqual([]);
		release(true);
		await next;
		expect(movesTo()).toEqual([1]);

		const refused = pendingValidation();
		const stopped = hook.handleNext(refused.validate);
		refused.release(false);
		await stopped;
		expect(movesTo()).toEqual([1]);
	});

	it('ignores a second Next while the first still waits, and takes the next one once it is over', async () => {
		const {hook, movesTo} = threeSteps();
		const {validate, release} = pendingValidation();

		const first = hook.handleNext(validate);
		const twin = hook.handleNext(validate);
		expect(validate).toHaveBeenCalledOnce();
		release(true);
		await Promise.all([first, twin]);
		expect(movesTo()).toEqual([1]);

		const later = pendingValidation();
		const again = hook.handleNext(later.validate);
		later.release(true);
		await again;
		expect(later.validate).toHaveBeenCalledOnce();
	});

	it('drops the move when the visitor went elsewhere while the validation ran: Previous during a slow provider', async () => {
		const {hook, movesTo} = threeSteps();
		hook.setCurrentStep(1);
		const {validate, release} = pendingValidation();

		const next = hook.handleNext(validate);
		hook.handlePrevious();
		release(true);
		await next;

		// step 1 → Previous → step 0; the accept that comes back is the click's, whose step is gone: no jump to 2
		expect(movesTo()).toEqual([1, 0]);
	});
});
