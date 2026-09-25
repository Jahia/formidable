// @vitest-environment jsdom
import {type FormEvent} from 'react';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';
import {useFormSubmission} from './useFormSubmission';

/**
 * What the submission does with a rejection that names fields — a field action's refusal (FMDB-015)
 * and its cap (FMDB-017) — and with the asynchronous pre-validation the field actions settle in.
 * React's state is the one seam mocked, as in useFormSubmission.dom.test.ts: the hook runs as a
 * function, `handleSubmit` is driven against a fake `XMLHttpRequest`.
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

// jsdom does not ship the CSS namespace; the code only needs escape().
if (typeof CSS === 'undefined') {
	(globalThis as {CSS?: {escape: (value: string) => string}}).CSS = {
		escape: value => value.replace(/[^a-zA-Z0-9_-]/g, character => `\\${character}`)
	};
}

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

	answer(status: number, body: unknown) {
		this.status = status;
		this.responseText = JSON.stringify(body);
		this.onload?.();
	}
}

const requests: FakeXhr[] = [];
const settled = () => new Promise(resolve => setTimeout(resolve, 0));

const labels = {
	captchaRequired: 'captcha',
	errorCode: 'Code',
	actionsProgress: (completed: number, total: number) => `${completed}/${total}`,
	maintenanceUnavailable: 'maintenance',
};

/** A form with an email field, submitted through the hook; the useState calls come in a fixed order — message, type, loading, captcha, refused control. */
function submit(preValidate?: () => boolean | Promise<boolean>) {
	react.setters.length = 0;
	const {handleSubmit} = useFormSubmission({
		formId: 'form-under-test',
		locale: 'en',
		isMultiStep: false,
		isLastStep: true,
		setCurrentStep: () => undefined,
		labels,
	});
	document.body.innerHTML = '<form><div class="fmdb-form-group"><input name="email" value="ada@nowhere.test"/></div></form>';
	const form = document.querySelector('form')!;
	const done = handleSubmit({preventDefault: () => undefined, currentTarget: form} as unknown as FormEvent<HTMLFormElement>, preValidate);
	const [setMessage, setMessageType, setIsLoading, , setRefusedControl] = react.setters;
	return {form, done, setMessage, setMessageType, setIsLoading, setRefusedControl};
}

describe('useFormSubmission: a rejection that names a field', () => {
	beforeEach(() => {
		vi.stubGlobal('XMLHttpRequest', FakeXhr);
		vi.spyOn(console, 'error').mockImplementation(() => undefined);
		requests.length = 0;
	});

	afterEach(() => {
		vi.unstubAllGlobals();
		vi.restoreAllMocks();
		document.body.replaceChildren();
	});

	it('anchors a field action refusal under its field, focused, with no global message', async () => {
		const {form, done, setMessage, setMessageType, setIsLoading, setRefusedControl} = submit();
		await settled();
		requests[0].answer(422, {success: false, errorCode: 'FMDB-015', messages: [
			{level: 'error', html: 'We do <b>not</b> know this domain', field: 'email'},
		]});
		await done;

		const input = form.querySelector('input')!;
		expect(form.querySelector('.fmdb-form-group > .fmdb-validation-error')!.innerHTML).toBe('We do <b>not</b> know this domain');
		expect(input.validity.customError).toBe(true);
		// the island focuses it once the form shows again (the spinner hides it): handed over as state, cleared at the start
		expect(setRefusedControl.mock.calls).toEqual([[null], [input]]);
		expect(setMessageType).not.toHaveBeenCalled();
		expect(setMessage).not.toHaveBeenCalled();
		expect(setIsLoading).toHaveBeenLastCalledWith(false);
	});

	it('anchors the message of the other field-naming rejection and keeps the global message with its code', async () => {
		const {form, done, setMessage, setMessageType} = submit();
		await settled();
		requests[0].answer(422, {success: false, errorCode: 'FMDB-017', messages: [
			{level: 'error', html: 'Too many answers', field: 'email'},
		]});
		await done;

		expect(form.querySelector('.fmdb-validation-error')!.textContent).toBe('Too many answers');
		expect(setMessageType).toHaveBeenCalledWith('error');
		expect(setMessage.mock.calls.at(-1)?.[0]).toContain('FMDB-017');
	});

	it('falls back on the global message when the refused field is not in the form', async () => {
		const {form, done, setMessageType} = submit();
		await settled();
		requests[0].answer(422, {success: false, errorCode: 'FMDB-015', messages: [
			{level: 'error', html: 'no', field: 'phone'},
		]});
		await done;

		expect(form.querySelector('.fmdb-validation-error')).toBeNull();
		expect(setMessageType).toHaveBeenCalledWith('error');
	});

	it('waits for an asynchronous pre-validation and sends nothing when it refuses', async () => {
		let release: (value: boolean) => void = () => undefined;
		const {done, setIsLoading} = submit(() => new Promise<boolean>(resolve => {
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

	it('sends once the asynchronous pre-validation accepts, the spinner shown from then', async () => {
		const {done, setIsLoading} = submit(() => Promise.resolve(true));
		await settled();

		expect(requests).toHaveLength(1);
		expect(setIsLoading).toHaveBeenCalledWith(true);
		requests[0].answer(200, {success: true});
		await done;
	});
});
