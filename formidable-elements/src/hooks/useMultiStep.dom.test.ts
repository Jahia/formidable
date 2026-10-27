// @vitest-environment jsdom
import {describe, expect, it, vi} from 'vitest';
import {renderHook} from '~/utils/testSupport/reactRenderer';
import {useMultiStep} from './useMultiStep';

/**
 * The step navigation once Next waits for an asynchronous validation — the field actions of the step.
 * React is mocked as a small renderer (utils/testSupport/reactRenderer.ts), not as inert holders: a Next
 * pending on a slow provider needs that much.
 */
vi.mock('react', async () => (await import('~/utils/testSupport/reactRenderer')).reactModule);

const ids = (count: number) => Array.from({length: count}, (unusedStep, index) => `s${index}`);

/** The steps of a form as the hook sees them: the logic wrapper carrying the node id, the step element inside. */
const stepsMarkup = (count: number, hidden: number[] = []) => ids(count).map((id, index) =>
	`<div data-fmdb-node-id="${id}"${hidden.includes(index) ? ' data-fmdb-logic-hidden="true"' : ''}><div data-fmdb-step></div></div>`
).join('');

/** The hook mounted on a form of `count` steps, some hidden by logic from the start; `latest()` is its last render. */
function mount(count: number, hidden: number[] = []) {
	document.body.innerHTML = `<form>${stepsMarkup(count, hidden)}</form>`;
	const form = document.querySelector('form')!;
	const formRef = {current: form};
	const {latest, flush, moves} = renderHook(() => useMultiStep({formRef, stepIds: ids(count)}));
	/** Logic's verdict on a step, as the visibility pass writes it, then the form event that makes the hook re-read it. */
	const logicHides = (index: number, hides: boolean) => {
		form.querySelector<HTMLElement>(`[data-fmdb-node-id="s${index}"]`)!.dataset.fmdbLogicHidden = hides ? 'true' : 'false';
		form.dispatchEvent(new Event('input'));
		flush();
	};
	return {latest, flush, logicHides, moves};
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
		const {latest, moves} = mount(3);
		const {validate, release} = pendingValidation();

		const next = latest().handleNext(validate);
		expect(moves).toEqual([]);
		release(true);
		await next;
		expect(moves).toEqual([1]);

		const refused = pendingValidation();
		const stopped = latest().handleNext(refused.validate);
		refused.release(false);
		await stopped;
		expect(moves).toEqual([1]);
	});

	it('ignores a second Next while the first still waits, and takes the next one once it is over', async () => {
		const {latest, flush, moves} = mount(3);
		const {validate, release} = pendingValidation();

		const first = latest().handleNext(validate);
		const twin = latest().handleNext(validate);
		expect(validate).toHaveBeenCalledOnce();
		release(true);
		await Promise.all([first, twin]);
		flush();
		expect(moves).toEqual([1]);

		const later = pendingValidation();
		const again = latest().handleNext(later.validate);
		later.release(true);
		await again;
		expect(later.validate).toHaveBeenCalledOnce();
		expect(moves).toEqual([1, 2]);
	});

	it('drops the move when the visitor went elsewhere while the validation ran: Previous during a slow provider', async () => {
		const {latest, flush, moves} = mount(3);
		latest().setCurrentStep(1);
		flush();
		const {validate, release} = pendingValidation();

		const next = latest().handleNext(validate);
		latest().handlePrevious();
		flush();
		release(true);
		await next;

		// step 1 → Previous → step 0; the accept that comes back is the click's, whose step is gone: no jump to 2
		expect(moves).toEqual([1, 0]);
	});

	it('drops the move when logic took the step away while the validation ran: the visitor is on the next one already', async () => {
		const {latest, logicHides, flush, moves} = mount(3);
		latest().setCurrentStep(1);
		flush();
		const {validate, release} = pendingValidation();

		const next = latest().handleNext(validate);
		// a field's change hides step 1 itself: the visibility pass moves the visitor to step 2, through the state
		logicHides(1, true);
		expect(latest().currentStep).toBe(2);
		release(true);
		await next;

		// the click's step is gone: no move — a stale ref would count from step 1, absent from [0, 2], and land on step 0
		expect(moves).toEqual([1, 2]);
	});

	it('moves on the steps as logic left them when the answer lands: the step after the current one hidden meanwhile', async () => {
		const {latest, logicHides, flush, moves} = mount(4);
		latest().setCurrentStep(1);
		flush();
		const {validate, release} = pendingValidation();

		const next = latest().handleNext(validate);
		logicHides(2, true);
		expect(latest().visibleStepIndices).toEqual([0, 1, 3]);
		release(true);
		await next;

		// the click saw step 2 as the next one; the accept skips it — the list of the first render, every step, would land on it
		expect(moves).toEqual([1, 3]);
	});

	it('counts from the step the visitor is on, not from the position the click saw: a step revealed before it', async () => {
		const {latest, logicHides, flush, moves} = mount(4, [1]);
		latest().setCurrentStep(2);
		flush();
		expect(latest().visibleStepIndices).toEqual([0, 2, 3]);
		const {validate, release} = pendingValidation();

		const next = latest().handleNext(validate);
		logicHides(1, false);
		release(true);
		await next;

		// step 2 was the second visible step at the click and is the third now: Next goes to step 3, not back to 2
		expect(moves).toEqual([2, 3]);
	});
});
