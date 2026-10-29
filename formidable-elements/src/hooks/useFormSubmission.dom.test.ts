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

describe('useFormSubmission: what an action answers', () => {
	const assign = vi.fn();
	/** The listener of the submitted event a test installs, removed after it. */
	let listener: ((event: Event) => void) | undefined;
	/** Installs a listener that hands the event's `waitUntil` the promise `promise()` gives. */
	const listenWith = (promise: () => Promise<unknown>) => {
		listener = event => (event as CustomEvent<{waitUntil: (p: Promise<unknown>) => void}>).detail.waitUntil(promise());
		document.body.addEventListener(SUBMITTED_EVENT, listener);
	};
	/** The success message is held back by the feedback floor, and a redirect leaves in a task of its own: fake timers move both. */
	beforeEach(() => {
		vi.useFakeTimers();
		vi.stubGlobal('XMLHttpRequest', FakeXhr);
		vi.stubGlobal('location', {assign, href: 'https://www.example.com/fr/connexion'});
		vi.spyOn(console, 'error').mockImplementation(() => undefined);
		requests.length = 0;
		assign.mockClear();
	});

	afterEach(() => {
		if (listener) document.body.removeEventListener(SUBMITTED_EVENT, listener);
		listener = undefined;
		vi.useRealTimers();
		vi.unstubAllGlobals();
		vi.restoreAllMocks();
		document.body.replaceChildren();
	});

	const accepted = async (body: string) => {
		const result = submitForm('');
		await vi.advanceTimersByTimeAsync(0);
		requests.at(-1)?.answer(200, body);
		await vi.advanceTimersByTimeAsync(600);
		return result;
	};

	it('navigates to the redirect instead of showing the success message, the spinner left on', async () => {
		const {successShown, setMessage, setIsLoading} = await accepted('{"success":true,"redirect":"/sites/mysite/account.html","userMessage":"Welcome."}');

		expect(assign).toHaveBeenCalledExactlyOnceWith('/sites/mysite/account.html');
		expect(successShown()).toBe(false);
		// the action's own sentence beside a redirect is not shown either
		expect(setMessage).not.toHaveBeenCalledWith('Welcome.');
		expect(setIsLoading).toHaveBeenLastCalledWith(true);
	});

	it('navigates in a task of its own, without waiting for the feedback pause, when no listener asked to wait', async () => {
		const result = submitForm('');
		await vi.advanceTimersByTimeAsync(0);
		requests.at(-1)?.answer(200, '{"success":true,"redirect":"/home.html"}');
		await vi.advanceTimersByTimeAsync(1);

		expect(assign).toHaveBeenCalledExactlyOnceWith('/home.html');
		expect(result.successShown()).toBe(false);
	});

	it('holds the redirect until the promise a listener handed to waitUntil settles, the spinner left on', async () => {
		let release: () => void = () => undefined;
		listenWith(() => new Promise<void>(resolve => {
			release = resolve;
		}));
		const {setIsLoading} = submitForm('');
		await vi.advanceTimersByTimeAsync(0);
		requests.at(-1)?.answer(200, '{"success":true,"redirect":"/home.html"}');
		await vi.advanceTimersByTimeAsync(500);
		expect(assign).not.toHaveBeenCalled();
		expect(setIsLoading).toHaveBeenLastCalledWith(true);

		release();
		await vi.advanceTimersByTimeAsync(1);

		expect(assign).toHaveBeenCalledExactlyOnceWith('/home.html');
	});

	it('navigates after one second when the promise of a listener never settles', async () => {
		listenWith(() => new Promise(() => undefined));
		submitForm('');
		await vi.advanceTimersByTimeAsync(0);
		requests.at(-1)?.answer(200, '{"success":true,"redirect":"/home.html"}');
		await vi.advanceTimersByTimeAsync(999);
		expect(assign).not.toHaveBeenCalled();

		await vi.advanceTimersByTimeAsync(2);

		expect(assign).toHaveBeenCalledExactlyOnceWith('/home.html');
	});

	it('navigates when the promise of a listener rejects', async () => {
		listenWith(() => Promise.reject(new Error('jCustomer is down')));
		submitForm('');
		await vi.advanceTimersByTimeAsync(0);
		requests.at(-1)?.answer(200, '{"success":true,"redirect":"/home.html"}');
		await vi.advanceTimersByTimeAsync(1);

		expect(assign).toHaveBeenCalledExactlyOnceWith('/home.html');
	});

	it('waits for nothing without a redirect, whatever a listener handed to waitUntil', async () => {
		listenWith(() => new Promise(() => undefined));
		const {successShown} = await accepted('{"success":true}');

		expect(successShown()).toBe(true);
	});

	it('ends at once as a submission without redirect when the target only changes the fragment', async () => {
		const {form, successShown, setIsLoading} = submitForm('<input name="city" value="Paris"/>');
		const reset = vi.fn();
		form.addEventListener('reset', reset);
		await vi.advanceTimersByTimeAsync(0);
		requests.at(-1)?.answer(200, '{"success":true,"redirect":"/fr/connexion#merci"}');
		await vi.advanceTimersByTimeAsync(1);

		expect(assign).toHaveBeenCalledExactlyOnceWith('/fr/connexion#merci');
		expect(successShown()).toBe(true);
		expect(setIsLoading).toHaveBeenLastCalledWith(false);
		expect(reset).toHaveBeenCalledOnce();
		// no fallback timer left to run
		expect(vi.getTimerCount()).toBe(0);
	});

	it('stops the spinner and shows the success message when the redirect leaves the page in place, the form kept as it is', async () => {
		const {form, successShown, setIsLoading} = submitForm('<input name="city" value="Paris"/>');
		const reset = vi.fn();
		form.addEventListener('reset', reset);
		form.querySelector('input')!.value = 'Lyon';
		await vi.advanceTimersByTimeAsync(0);
		requests.at(-1)?.answer(200, '{"success":true,"redirect":"/files/brochure.zip"}');
		await vi.advanceTimersByTimeAsync(600);
		expect(assign).toHaveBeenCalledExactlyOnceWith('/files/brochure.zip');
		expect(successShown()).toBe(false);

		await vi.advanceTimersByTimeAsync(3000);

		expect(successShown()).toBe(true);
		expect(setIsLoading).toHaveBeenLastCalledWith(false);
		// a next page slow to answer may still replace this one: nothing it would show is undone
		expect(reset).not.toHaveBeenCalled();
		expect(form.querySelector('input')!.value).toBe('Lyon');
	});

	it('shows the success outcome when the browser brings the page back from its back-forward cache', async () => {
		const {successShown, setIsLoading} = await accepted('{"success":true,"redirect":"/home.html"}');
		const restored = new Event('pageshow');
		Object.defineProperty(restored, 'persisted', {value: true});

		globalThis.dispatchEvent(restored);

		expect(successShown()).toBe(true);
		expect(setIsLoading).toHaveBeenLastCalledWith(false);
	});

	it('navigates to an absolute https URL, the second kind of redirect', async () => {
		await accepted('{"success":true,"redirect":"https://www.example.com/merci"}');

		expect(assign).toHaveBeenCalledExactlyOnceWith('https://www.example.com/merci');
	});

	it('refuses a redirect that is neither a path of this server nor an absolute http(s) URL, whatever the body says', async () => {
		for (const hostile of ['//evil.example', String.raw`/\\evil.example`, 'javascript:alert(1)', 'https://user@evil.example/', 'home.html', '/a b', '/a\uFEFFb', '']) {
			await accepted(JSON.stringify({success: true, redirect: hostile}));
		}
		await accepted('{"success":true,"redirect":42}');

		expect(assign).not.toHaveBeenCalled();
	});

	it('shows the action\'s own sentence instead of the form\'s submission message', async () => {
		const {setMessage} = await accepted('{"success":true,"userMessage":"Check your mailbox to activate your account."}');

		expect(setMessage).toHaveBeenLastCalledWith('Check your mailbox to activate your account.');
	});

	it('keeps the form\'s message when the action said nothing', async () => {
		const {setMessage} = await accepted('{"success":true}');

		expect(setMessage).toHaveBeenLastCalledWith('Form submitted successfully!');
		expect(assign).not.toHaveBeenCalled();
	});
});
