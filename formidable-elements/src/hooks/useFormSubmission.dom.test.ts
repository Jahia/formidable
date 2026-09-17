// @vitest-environment jsdom
import {type FormEvent} from 'react';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';
import {SUBMITTED_EVENT, useFormSubmission} from './useFormSubmission';

/**
 * The wiring of the minimum feedback pause (#327), which the arithmetic tests of
 * `remainingFeedbackPause` cannot see: the clock starts when the spinner appears, and the success
 * message waits for what is left of the floor — nothing after a slow answer, the remainder after a
 * fast one.
 *
 * React's state is the one seam mocked: `useState` and `useRef` become plain holders, so the hook runs
 * as a function and `handleSubmit` is driven end to end against a fake `XMLHttpRequest` under fake
 * timers. Rendering it would need react-dom, which this module does not depend on. `vi.mock` is hoisted
 * above the imports, so the static import of the hook already sees the mocked React.
 */
const react = vi.hoisted(() => ({setters: [] as Array<ReturnType<typeof vi.fn>>}));
vi.mock('react', () => ({
	useState: <T,>(initial: T) => {
		const set = vi.fn();
		react.setters.push(set);
		return [initial, set];
	},
	useRef: <T,>(initial: T) => ({current: initial}),
}));

/** The request the hook sends, answered by the test when it decides the server has taken long enough. */
class FakeXhr {
	static last: FakeXhr | undefined;
	status = 0;
	responseText = '';
	withCredentials = false;
	onload: (() => void) | null = null;
	onerror: (() => void) | null = null;
	open() {}
	setRequestHeader() {}
	send() {
		FakeXhr.last = this;
	}

	answer(status: number, body: string) {
		this.status = status;
		this.responseText = body;
		this.onload?.();
	}
}

const labels = {
	captchaRequired: 'captcha',
	errorCode: 'code',
	actionsProgress: (completed: number, total: number) => `${completed}/${total}`,
	maintenanceUnavailable: 'maintenance',
};

describe('useFormSubmission: the minimum feedback pause is a floor', () => {
	/** Hears the accepted submission bubble up from the form; the body keeps its listeners across replaceChildren. */
	const submitted = vi.fn();

	beforeEach(() => {
		vi.useFakeTimers();
		vi.stubGlobal('XMLHttpRequest', FakeXhr);
		react.setters.length = 0;
		FakeXhr.last = undefined;
		submitted.mockClear();
		document.body.addEventListener(SUBMITTED_EVENT, submitted);
	});

	afterEach(() => {
		document.body.removeEventListener(SUBMITTED_EVENT, submitted);
		vi.useRealTimers();
		vi.unstubAllGlobals();
		document.body.replaceChildren();
	});

	/** Runs `handleSubmit` on a fresh form; the hook's `useState` calls are message, message type, loading, captcha. */
	function submit() {
		const {handleSubmit} = useFormSubmission({
			formId: 'form-under-test',
			locale: 'en',
			isMultiStep: false,
			isLastStep: true,
			setCurrentStep: () => {},
			labels,
		});
		const form = document.createElement('form');
		document.body.append(form);
		const event = {preventDefault() {}, currentTarget: form} as unknown as FormEvent<HTMLFormElement>;
		const done = handleSubmit(event);
		const [, setMessageType] = react.setters;
		return {done, setMessageType, successShown: () => setMessageType.mock.calls.some(([type]) => type === 'success')};
	}

	it('waits nothing more after an answer slower than the floor', async () => {
		const {done, successShown} = submit();
		await vi.advanceTimersByTimeAsync(1200);
		FakeXhr.last?.answer(200, '{"success":true}');

		await vi.advanceTimersByTimeAsync(0);

		expect(successShown()).toBe(true);
		expect(vi.getTimerCount()).toBe(0);
		await done;
	});

	it('completes the floor after a fast answer, counted from the spinner, not from the answer', async () => {
		const {done, successShown} = submit();
		await vi.advanceTimersByTimeAsync(40);
		FakeXhr.last?.answer(200, '{"success":true}');

		await vi.advanceTimersByTimeAsync(459);
		expect(successShown()).toBe(false);

		await vi.advanceTimersByTimeAsync(1);
		expect(successShown()).toBe(true);
		await done;
	});

	it('announces the accepted submission before the pause, not after it', async () => {
		const {done, successShown} = submit();
		await vi.advanceTimersByTimeAsync(40);
		FakeXhr.last?.answer(200, '{"success":true}');
		await vi.advanceTimersByTimeAsync(0);

		// the page hears of the acceptance at once: a listener (the jExperience script) is never made to wait for the floor
		expect(submitted).toHaveBeenCalledOnce();
		expect(successShown()).toBe(false);
		await vi.advanceTimersByTimeAsync(460);
		await done;
	});
});
