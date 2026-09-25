// @vitest-environment jsdom
import {type FormEvent} from 'react';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';
import '~/utils/testSupport/cssEscape';
import {SUBMITTED_EVENT, useFormSubmission} from './useFormSubmission';

/**
 * The submission hook driven end to end against a fake `XMLHttpRequest`: the minimum feedback
 * pause (#327) under fake timers, and what a rejection naming fields does (a field action's
 * refusal, FMDB-015, and its cap, FMDB-017) with the asynchronous pre-validation the field actions
 * settle in, under real timers.
 *
 * React's state is the one seam mocked: `useState` and `useRef` become plain holders, so the hook runs
 * as a function and `handleSubmit` is driven directly. Rendering it would need react-dom, which this
 * module does not depend on. `vi.mock` is hoisted above the imports, so the static import of the hook
 * already sees the mocked React.
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
	status = 0;
	responseText = '';
	withCredentials = false;
	onload: (() => void) | null = null;
	onerror: (() => void) | null = null;
	opened: {method: string; url: string} | undefined;
	readonly headers: Record<string, string> = {};

	open(method: string, url: string) {
		this.opened = {method, url};
	}

	setRequestHeader(name: string, value: string) {
		this.headers[name] = value;
	}

	send() {
		requests.push(this);
	}

	answer(status: number, body: string) {
		this.status = status;
		this.responseText = body;
		this.onload?.();
	}
}

/** The requests sent, in order; the test answers the last one. Cleared before each test. */
const requests: FakeXhr[] = [];

const labels = {
	captchaRequired: 'captcha',
	errorCode: 'Code',
	actionsProgress: (completed: number, total: number) => `${completed}/${total}`,
	maintenanceUnavailable: 'maintenance',
};

/**
 * The component the hook runs in, as it runs in Form.client.tsx — a function component in shape,
 * called directly since React is the mock above (what a hook-rendering test utility does under the hood).
 */
function SubmittingForm(options: Parameters<typeof useFormSubmission>[0]) {
	return useFormSubmission(options);
}

/** The hook mounted on a fresh form with the given fields, its setters by rank, for a test that submits more than once. */
function hookOn(html: string) {
	react.setters.length = 0;
	const hook = SubmittingForm({formId: 'form-under-test', locale: 'en', isMultiStep: false, isLastStep: true, setCurrentStep: () => undefined, labels});
	document.body.innerHTML = `<form>${html}</form>`;
	return {handleSubmit: hook.handleSubmit, setters: react.setters.slice()};
}

const eventOn = (form: HTMLFormElement) => ({preventDefault: () => undefined, currentTarget: form} as unknown as FormEvent<HTMLFormElement>);

/**
 * Submits a form and hands back the hook's state setters. The hook's `useState` calls come in a fixed
 * order — message, message type, loading, captcha — so the setters are read by rank; `onRefused` is the
 * island's callback, mocked.
 */
function submitForm(html: string, preValidate?: () => boolean | Promise<boolean>) {
	react.setters.length = 0;
	const onRefused = vi.fn();
	const {handleSubmit} = SubmittingForm({
		formId: 'form-under-test',
		locale: 'en',
		isMultiStep: false,
		isLastStep: true,
		setCurrentStep: () => undefined,
		labels,
		onRefused,
	});
	document.body.innerHTML = `<form>${html}</form>`;
	const form = document.querySelector('form')!;
	const done = handleSubmit({preventDefault: () => undefined, currentTarget: form} as unknown as FormEvent<HTMLFormElement>, preValidate);
	const [setMessage, setMessageType, setIsLoading] = react.setters;
	return {
		form,
		done,
		setMessage,
		setMessageType,
		setIsLoading,
		onRefused,
		successShown: () => setMessageType.mock.calls.some(([type]) => type === 'success'),
	};
}

describe('useFormSubmission: the minimum feedback pause is a floor', () => {
	/** Hears the accepted submission bubble up from the form; the body keeps its listeners across replaceChildren. */
	const submitted = vi.fn();

	beforeEach(() => {
		vi.useFakeTimers();
		vi.stubGlobal('XMLHttpRequest', FakeXhr);
		requests.length = 0;
		submitted.mockClear();
		document.body.addEventListener(SUBMITTED_EVENT, submitted);
	});

	afterEach(() => {
		document.body.removeEventListener(SUBMITTED_EVENT, submitted);
		vi.useRealTimers();
		vi.unstubAllGlobals();
		document.body.replaceChildren();
	});

	it('waits nothing more after an answer slower than the floor', async () => {
		const {successShown} = submitForm('');
		await vi.advanceTimersByTimeAsync(1200);
		requests.at(-1)?.answer(200, '{"success":true}');

		await vi.advanceTimersByTimeAsync(0);

		expect(successShown()).toBe(true);
		expect(vi.getTimerCount()).toBe(0);
	});

	it('completes the floor after a fast answer, counted from the spinner, not from the answer', async () => {
		const {successShown} = submitForm('');
		await vi.advanceTimersByTimeAsync(40);
		requests.at(-1)?.answer(200, '{"success":true}');

		await vi.advanceTimersByTimeAsync(459);
		expect(successShown()).toBe(false);

		await vi.advanceTimersByTimeAsync(1);
		expect(successShown()).toBe(true);
	});

	it('announces the accepted submission before the pause, not after it', async () => {
		const {successShown} = submitForm('');
		await vi.advanceTimersByTimeAsync(40);
		requests.at(-1)?.answer(200, '{"success":true}');
		await vi.advanceTimersByTimeAsync(0);

		// the page hears of the acceptance at once: a listener (the jExperience script) never pays the floor
		expect(submitted).toHaveBeenCalledOnce();
		expect(successShown()).toBe(false);

		await vi.advanceTimersByTimeAsync(460);
		expect(successShown()).toBe(true);
	});
});

describe('useFormSubmission: a rejection that names a field', () => {
	const EMAIL_FIELD = '<div class="fmdb-form-group"><input name="email" value="ada@nowhere.test"/></div>';
	/** The request leaves a few microtasks after the call: one macrotask lets them run. */
	const settled = () => new Promise(resolve => setTimeout(resolve, 0));
	const refusal = (errorCode: string, field: string, html: string) =>
		JSON.stringify({success: false, errorCode, messages: [{level: 'error', html, field}]});

	beforeEach(() => {
		vi.useRealTimers();
		vi.stubGlobal('XMLHttpRequest', FakeXhr);
		vi.spyOn(console, 'error').mockImplementation(() => undefined);
		requests.length = 0;
	});

	afterEach(() => {
		vi.unstubAllGlobals();
		vi.restoreAllMocks();
		document.body.replaceChildren();
	});

	it('anchors a field action refusal under its field, hands the control over to the island, shows no global message', async () => {
		const {form, done, setMessage, setMessageType, setIsLoading, onRefused} = submitForm(EMAIL_FIELD);
		await settled();
		requests[0].answer(422, refusal('FMDB-015', 'email', 'We do <b>not</b> know this domain'));
		await done;

		const input = form.querySelector('input')!;
		expect(form.querySelector('.fmdb-form-group > .fmdb-validation-error')!.innerHTML).toBe('We do <b>not</b> know this domain');
		expect(input.validity.customError).toBe(true);
		// the island brings it on screen and focuses it once the form shows again (the spinner hides it)
		expect(onRefused).toHaveBeenCalledExactlyOnceWith(input);
		// the message of an earlier attempt is cleared as the request leaves; nothing is written after the refusal
		expect(setMessageType.mock.calls).toEqual([[null]]);
		expect(setMessage.mock.calls).toEqual([[null]]);
		expect(setIsLoading).toHaveBeenLastCalledWith(false);
	});

	it('anchors the message of the other field-naming rejection and keeps the global message with its code', async () => {
		const {form, done, setMessage, setMessageType} = submitForm(EMAIL_FIELD);
		await settled();
		requests[0].answer(422, refusal('FMDB-017', 'email', 'Too many answers'));
		await done;

		expect(form.querySelector('.fmdb-validation-error')!.textContent).toBe('Too many answers');
		expect(setMessageType).toHaveBeenCalledWith('error');
		expect(setMessage.mock.calls.at(-1)?.[0]).toContain('FMDB-017');
	});

	it('falls back on the global message when the refused field is not in the form', async () => {
		const {form, done, setMessageType} = submitForm(EMAIL_FIELD);
		await settled();
		requests[0].answer(422, refusal('FMDB-015', 'phone', 'no'));
		await done;

		expect(form.querySelector('.fmdb-validation-error')).toBeNull();
		expect(setMessageType).toHaveBeenCalledWith('error');
	});

	it('waits for an asynchronous pre-validation and sends nothing when it refuses', async () => {
		let release: (value: boolean) => void = () => undefined;
		const {done, setIsLoading} = submitForm(EMAIL_FIELD, () => new Promise<boolean>(resolve => {
			release = resolve;
		}));
		await settled();
		expect(requests).toHaveLength(0);
		expect(setIsLoading).not.toHaveBeenCalled();

		release(false);
		await done;

		expect(requests).toHaveLength(0);
		expect(setIsLoading).not.toHaveBeenCalled();
	});

	it('forgets the message of a failed attempt when the next one leaves: a refusal reads alone', async () => {
		const {handleSubmit, setters} = hookOn(EMAIL_FIELD);
		const [setMessage, setMessageType] = setters;
		const form = document.querySelector('form')!;
		const first = handleSubmit(eventOn(form));
		await settled();
		requests[0].answer(503, '{}');
		await first;
		expect(setMessageType).toHaveBeenLastCalledWith('error');

		const second = handleSubmit(eventOn(form));
		await settled();
		expect(requests).toHaveLength(2);
		requests[1].answer(422, refusal('FMDB-015', 'email', 'no'));
		await second;

		expect(setMessage).toHaveBeenLastCalledWith(null);
		expect(setMessageType).toHaveBeenLastCalledWith(null);
		expect(form.querySelector('.fmdb-validation-error')!.textContent).toBe('no');
	});

	it('ignores a second click while a submission is in progress, and takes the next one once it is over', async () => {
		const {handleSubmit} = hookOn(EMAIL_FIELD);
		const form = document.querySelector('form')!;
		const first = handleSubmit(eventOn(form));
		const twin = handleSubmit(eventOn(form));
		await settled();
		expect(requests).toHaveLength(1);
		requests[0].answer(503, '{}');
		await Promise.all([first, twin]);

		const next = handleSubmit(eventOn(form));
		await settled();
		expect(requests).toHaveLength(2);
		requests[1].answer(503, '{}');
		await next;
	});

	it('sends once the asynchronous pre-validation accepts, the spinner shown from then', async () => {
		const {done, setIsLoading} = submitForm(EMAIL_FIELD, () => Promise.resolve(true));
		await settled();

		expect(requests).toHaveLength(1);
		expect(setIsLoading).toHaveBeenCalledWith(true);
		requests[0].answer(200, '{"success":true}');
		await done;
	});
});
