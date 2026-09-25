// @vitest-environment jsdom
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';
import '~/utils/testSupport/cssEscape';
import {showFieldError} from '~/utils/validationUtils';
import {useCustomFormValidation} from './useCustomFormValidation';
import {useFieldActions} from './useFieldActions';

/**
 * The hook driven as a function against a real DOM and a fake `XMLHttpRequest`, React's three seams
 * mocked — `useRef` a holder, `useEffect` run at once — since rendering it would need react-dom,
 * which this module does not depend on. `vi.mock` is hoisted above the imports.
 */
vi.mock('react', () => ({
	useRef: <T,>(initial: T) => ({current: initial}),
	useEffect: (effect: () => void | (() => void)) => {
		effect();
	},
}));

/** A request the hook sent, answered by the test. */
class FakeXhr {
	status = 0;
	responseText = '';
	withCredentials = false;
	timeout = 0;
	onload: (() => void) | null = null;
	onerror: (() => void) | null = null;
	ontimeout: (() => void) | null = null;
	opened: {method: string; url: string} | undefined;
	sent: unknown;
	readonly headers: Record<string, string> = {};

	open(method: string, url: string) {
		this.opened = {method, url};
	}

	setRequestHeader(name: string, value: string) {
		this.headers[name] = value;
	}

	send(body: string) {
		this.sent = JSON.parse(body);
		requests.push(this);
	}

	answer(status: number, body: unknown) {
		this.status = status;
		this.responseText = typeof body === 'string' ? body : JSON.stringify(body);
		this.onload?.();
	}

	fail() {
		this.onerror?.();
	}

	expire() {
		this.ontimeout?.();
	}
}

const requests: FakeXhr[] = [];
const URL = '/modules/formidable-engine/field-action?fid=f0&lang=en';

const reject = (field: string, html: string) => ({verdict: 'reject', messages: [{level: 'error', html, field}]});
const advice = (field: string, html: string) => ({verdict: 'advice', messages: [{level: 'warning', html, field}]});
const accept = {verdict: 'accept', messages: []};

/** The settle and the answers chain a few microtasks: one macrotask lets them all run. */
const settled = () => new Promise(resolve => setTimeout(resolve, 0));

const bubbling = (type: string) => new Event(type, {bubbles: true});

/** A form as the server renders it — the wrappers with their markers — with the hook attached. */
function formWith(html: string, options: {enabled?: boolean; url?: string; withConstraintClient?: boolean} = {}) {
	document.body.innerHTML = `<form>${html}</form>`;
	const form = document.querySelector('form')!;
	if (options.withConstraintClient) {
		useCustomFormValidation({formRef: {current: form}});
	}
	const {settleFieldActions} = useFieldActions({
		formRef: {current: form},
		fieldActionUrl: 'url' in options ? options.url : URL,
		enabled: options.enabled ?? true,
		labels: {checking: 'Checking…'},
	});
	return {form, settleFieldActions};
}

const textField = (name: string, trigger = 'blur', extra = '') => `
	<div data-fmdb-node-name="${name}" data-fmdb-field-action="${trigger}" ${extra}>
		<div class="fmdb-form-group"><input type="text" name="${name}"/></div>
	</div>`;

const wrapperOf = (form: HTMLFormElement, name: string) => form.querySelector<HTMLElement>(`[data-fmdb-node-name="${name}"]`)!;

describe('useFieldActions', () => {
	beforeEach(() => {
		vi.stubGlobal('XMLHttpRequest', FakeXhr);
		vi.spyOn(console, 'warn').mockImplementation(() => undefined);
		requests.length = 0;
	});

	afterEach(() => {
		vi.unstubAllGlobals();
		vi.restoreAllMocks();
		document.body.replaceChildren();
	});

	it('asks the engine about a blur field as the visitor leaves it: one JSON request with credentials', () => {
		const {form} = formWith(textField('firstName'));
		const input = form.querySelector('input')!;
		input.value = ' spam ';

		input.dispatchEvent(bubbling('focusout'));

		expect(requests).toHaveLength(1);
		expect(requests[0].opened).toEqual({method: 'POST', url: URL});
		expect(requests[0].headers['Content-Type']).toBe('application/json');
		expect(requests[0].withCredentials).toBe(true);
		expect(requests[0].sent).toEqual({field: 'firstName', value: 'spam', trigger: 'blur'});
		expect(wrapperOf(form, 'firstName').getAttribute('aria-busy')).toBe('true');
	});

	it('says under the field what the form waits for while a check runs, and no longer once it is answered', async () => {
		const {form} = formWith(textField('firstName'));
		const input = form.querySelector('input')!;
		input.value = 'spam';
		input.dispatchEvent(bubbling('focusout'));

		const checking = form.querySelector('.fmdb-form-group > .fmdb-field-action-checking')!;
		expect(checking.getAttribute('role')).toBe('status');
		expect(checking.textContent).toBe('Checking…');
		expect(checking.querySelector('svg.fmdb-field-action-checking-glyph')).not.toBeNull();
		expect(form.querySelectorAll('.fmdb-field-action-checking')).toHaveLength(1);

		requests[0].answer(200, accept);
		await settled();

		expect(form.querySelector('.fmdb-field-action-checking')).toBeNull();
	});

	it('settles the fields of one root only — a step before the visitor leaves it', async () => {
		const {form, settleFieldActions} = formWith(`<div data-fmdb-step>${textField('first')}</div><div data-fmdb-step>${textField('second')}</div>`);
		form.querySelectorAll('input').forEach(input => {
			input.value = 'x';
		});

		const outcome = settleFieldActions(form.querySelector<HTMLElement>('[data-fmdb-step]')!);
		expect(requests.map(request => (request.sent as {field: string}).field)).toEqual(['first']);
		requests[0].answer(200, accept);

		expect(await outcome).toBeNull();
	});

	it('leaves a submit-only field alone until the submission, and never asks a field without the marker', () => {
		const {form} = formWith(`${textField('later', 'submit')}<div data-fmdb-node-name="plain"><input name="plain"/></div>`);
		form.querySelectorAll('input').forEach(input => {
			input.value = 'x';
			input.dispatchEvent(bubbling('focusout'));
		});

		expect(requests).toHaveLength(0);
	});

	it('blocks on a refusal: customValidity, the contributor HTML under the field, the pending state gone', async () => {
		const {form} = formWith(textField('firstName'));
		const input = form.querySelector('input')!;
		input.value = 'spam';
		input.dispatchEvent(bubbling('focusout'));

		requests[0].answer(200, reject('firstName', 'The word <b>spam</b> is refused'));
		await settled();

		expect(input.validity.customError).toBe(true);
		expect(input.validationMessage).toBe('The word spam is refused');
		expect(form.querySelector('.fmdb-form-group > .fmdb-validation-error')!.innerHTML).toBe('The word <b>spam</b> is refused');
		expect(wrapperOf(form, 'firstName').hasAttribute('aria-busy')).toBe(false);
		expect(form.querySelector('.fmdb-field-action-pending')).toBeNull();
	});

	it('clears a refusal on the next accept, and shows a warning without blocking', async () => {
		const {form} = formWith(textField('firstName'));
		const input = form.querySelector('input')!;
		input.value = 'spam';
		input.dispatchEvent(bubbling('focusout'));
		requests[0].answer(200, reject('firstName', 'no'));
		await settled();

		input.value = 'Ada';
		input.dispatchEvent(bubbling('focusout'));
		requests[1].answer(200, advice('firstName', 'Unusual name'));
		await settled();

		expect(input.validity.valid).toBe(true);
		expect(form.querySelector('.fmdb-validation-error')).toBeNull();
		expect(form.querySelector('.fmdb-validation-warning')!.textContent).toBe('Unusual name');
	});

	it('drops the answer to a superseded check: the verdict shown is the latest value\'s', async () => {
		const {form} = formWith(textField('firstName'));
		const input = form.querySelector('input')!;
		input.value = 'spam';
		input.dispatchEvent(bubbling('focusout'));
		input.value = 'Ada';
		input.dispatchEvent(bubbling('focusout'));

		requests[1].answer(200, accept);
		await settled();
		requests[0].answer(200, reject('firstName', 'stale'));
		await settled();

		expect(input.validity.valid).toBe(true);
		expect(form.querySelector('.fmdb-validation-error')).toBeNull();
		expect(wrapperOf(form, 'firstName').hasAttribute('aria-busy')).toBe(false);
	});

	it('asks nothing about a blank value, clears what the field showed, and ends a pending check in flight', async () => {
		const {form} = formWith(textField('firstName'));
		const input = form.querySelector('input')!;
		input.value = 'spam';
		input.dispatchEvent(bubbling('focusout'));
		requests[0].answer(200, reject('firstName', 'no'));
		await settled();

		input.value = '   ';
		input.dispatchEvent(bubbling('focusout'));
		await settled();

		expect(requests).toHaveLength(1);
		expect(input.validity.valid).toBe(true);
		expect(form.querySelector('.fmdb-validation-error')).toBeNull();

		// a check still in flight when the field is emptied: its answer is dropped and it leaves no pending state behind
		input.value = 'spam';
		input.dispatchEvent(bubbling('focusout'));
		input.value = '';
		input.dispatchEvent(bubbling('focusout'));
		requests[1].answer(200, reject('firstName', 'no'));
		await settled();

		expect(wrapperOf(form, 'firstName').getAttribute('aria-busy')).toBeNull();
		expect(input.validity.valid).toBe(true);
	});

	it('asks once per checked value of a group, on change, and refuses the field if any is refused', async () => {
		const {form} = formWith(`
			<div data-fmdb-node-name="topics" data-fmdb-field-action="blur">
				<div class="fmdb-form-group">
					<input type="checkbox" name="topics" value="sports" checked/>
					<input type="checkbox" name="topics" value="spam" checked/>
					<input type="checkbox" name="topics" value="music"/>
				</div>
			</div>`);
		const boxes = Array.from(form.querySelectorAll('input'));

		boxes[1].dispatchEvent(bubbling('change'));

		expect(requests.map(request => (request.sent as {value: string}).value)).toEqual(['sports', 'spam']);
		requests[0].answer(200, accept);
		requests[1].answer(200, reject('topics', 'not spam'));
		await settled();

		expect(boxes.every(box => box.validity.customError)).toBe(true);
		expect(form.querySelectorAll('.fmdb-validation-error')).toHaveLength(1);
	});

	it('leaves a required group its own "select at least one": the field actions lift their validity only', async () => {
		// Checkbox.client sets the group's message on every change and at mount; here by hand.
		const {form} = formWith(`
			<div data-fmdb-node-name="topics" data-fmdb-field-action="blur">
				<div class="fmdb-form-group">
					<input type="checkbox" name="topics" value="sports"/>
					<input type="checkbox" name="topics" value="music"/>
				</div>
			</div>`);
		const boxes = Array.from(form.querySelectorAll('input'));
		boxes.forEach(box => box.setCustomValidity('Select at least one'));

		// checked then unchecked: a check runs on the first change, none on the second (no value)
		boxes[0].checked = true;
		boxes[0].dispatchEvent(bubbling('change'));
		requests[0].answer(200, accept);
		await settled();
		boxes[0].checked = false;
		boxes.forEach(box => box.setCustomValidity('Select at least one'));
		boxes[0].dispatchEvent(bubbling('change'));
		await settled();

		expect(boxes.every(box => box.validationMessage === 'Select at least one')).toBe(true);

		form.dispatchEvent(new Event('reset'));
		expect(boxes.every(box => box.validationMessage === 'Select at least one')).toBe(true);
	});

	it('settles before the submission: every field asked with the submit trigger, false and the focus on a refusal', async () => {
		const {form, settleFieldActions} = formWith(`${textField('firstName')}${textField('email', 'submit')}`);
		const [firstName, email] = Array.from(form.querySelectorAll('input'));
		firstName.value = 'Ada';
		email.value = 'ada@nowhere.test';

		const outcome = settleFieldActions(form);
		expect(requests.map(request => request.sent)).toEqual([
			{field: 'firstName', value: 'Ada', trigger: 'submit'},
			{field: 'email', value: 'ada@nowhere.test', trigger: 'submit'},
		]);
		requests[0].answer(200, accept);
		requests[1].answer(200, reject('email', 'Unknown domain'));

		expect(await outcome).toBe(email);
		expect(email.validity.customError).toBe(true);
	});

	it('refuses a multi-valued field at the settle when one of its values is refused, the others accepted', async () => {
		const {form, settleFieldActions} = formWith(`
			<div data-fmdb-node-name="topics" data-fmdb-field-action="submit">
				<div class="fmdb-form-group">
					<input type="checkbox" name="topics" value="sports" checked/>
					<input type="checkbox" name="topics" value="spam" checked/>
				</div>
			</div>`);

		const outcome = settleFieldActions(form);
		requests[0].answer(200, accept);
		requests[1].answer(200, reject('topics', 'not spam'));

		expect(await outcome).toBe(form.querySelector('input'));
	});

	it('settles true with a warning shown, and true when a check could not be asked: the pipeline judges', async () => {
		const {form, settleFieldActions} = formWith(`${textField('firstName')}${textField('email')}`);
		const [firstName, email] = Array.from(form.querySelectorAll('input'));
		firstName.value = 'Ada';
		email.value = 'ada@example.com';

		const outcome = settleFieldActions(form);
		requests[0].answer(200, advice('firstName', 'Unusual'));
		requests[1].answer(429, {errorCode: 'FMDB-016'});

		expect(await outcome).toBeNull();
		expect(form.querySelector('.fmdb-validation-warning')!.textContent).toBe('Unusual');
		expect(email.validity.valid).toBe(true);
		expect(form.querySelector('.fmdb-validation-error')).toBeNull();
		expect(console.warn).toHaveBeenCalledTimes(1);
	});

	it('reads a timeout, a body that is not an answer and an unknown verdict as unanswered: nothing blocked', async () => {
		const {form, settleFieldActions} = formWith(`${textField('a')}${textField('b')}${textField('c')}`);
		form.querySelectorAll('input').forEach(input => {
			input.value = 'x';
		});

		const outcome = settleFieldActions(form);
		requests[0].expire();
		requests[1].answer(200, '<html>proxy error</html>');
		requests[2].answer(200, {verdict: 'maybe', messages: [{level: 'error', html: 'no', field: 'c'}]});

		expect(await outcome).toBeNull();
		expect(form.querySelector('.fmdb-validation-error')).toBeNull();
		expect(Array.from(form.querySelectorAll('input')).every(input => input.validity.valid)).toBe(true);
		expect(form.querySelector('[aria-busy]')).toBeNull();
	});

	it('shows nothing and blocks nothing on an error status or a network failure at blur', async () => {
		const {form} = formWith(textField('firstName'));
		const input = form.querySelector('input')!;
		input.value = 'spam';
		input.dispatchEvent(bubbling('focusout'));
		requests[0].answer(404, {errorCode: 'FMDB-004'});
		await settled();
		input.dispatchEvent(bubbling('focusout'));
		requests[1].fail();
		await settled();

		expect(input.validity.valid).toBe(true);
		expect(form.querySelector('.fmdb-validation-error')).toBeNull();
		expect(wrapperOf(form, 'firstName').hasAttribute('aria-busy')).toBe(false);
	});

	it('does nothing while disabled — edit mode — and settles true without a request', async () => {
		const {form, settleFieldActions} = formWith(textField('firstName'), {enabled: false});
		const input = form.querySelector('input')!;
		input.value = 'spam';
		input.dispatchEvent(bubbling('focusout'));

		expect(await settleFieldActions(form)).toBeNull();
		expect(requests).toHaveLength(0);
	});

	it('skips a field conditional logic holds hidden, at blur and at the settle', async () => {
		const {form, settleFieldActions} = formWith(textField('firstName', 'blur', 'data-fmdb-logic-hidden="true"'));
		const input = form.querySelector('input')!;
		input.value = 'spam';
		input.dispatchEvent(bubbling('focusout'));

		expect(await settleFieldActions(form)).toBeNull();
		expect(requests).toHaveLength(0);
	});

	it('lifts a refusal as the visitor types, so the browser lets the corrected value through; a warning stays', async () => {
		const {form} = formWith(textField('firstName'), {withConstraintClient: true});
		const input = form.querySelector('input')!;
		input.value = 'spam';
		input.dispatchEvent(bubbling('focusout'));
		requests[0].answer(200, {verdict: 'reject', messages: [
			{level: 'error', html: 'no', field: 'firstName'},
			{level: 'warning', html: 'careful', field: 'firstName'},
		]});
		await settled();

		input.value = 'spa';
		input.dispatchEvent(bubbling('input'));

		expect(input.validity.valid).toBe(true);
		expect(form.querySelector('.fmdb-validation-error')).toBeNull();
		expect(form.querySelector('.fmdb-validation-warning')!.textContent).toBe('careful');
	});

	it('keeps a constraint error while typing: only its own validity is lifted', () => {
		const {form} = formWith(`
			<div data-fmdb-node-name="email" data-fmdb-field-action="blur">
				<div class="fmdb-form-group"><input type="email" name="email" value="not-an-email"/></div>
			</div>`);
		const input = form.querySelector('input')!;
		showFieldError(input, 'Not an email');

		input.dispatchEvent(bubbling('input'));

		expect(input.validity.typeMismatch).toBe(true);
		expect(form.querySelector('.fmdb-validation-error')!.textContent).toBe('Not an email');
	});

	it('marks and focuses the slider of a range field, whose named control is a hidden mirror', async () => {
		const {form, settleFieldActions} = formWith(`
			<div data-fmdb-node-name="budget" data-fmdb-field-action="blur">
				<div class="fmdb-form-group">
					<input type="range" id="budget-slider" min="0" max="100" value="90"/>
					<input type="hidden" name="budget" value="90"/>
				</div>
			</div>`);
		const slider = form.querySelector<HTMLInputElement>('#budget-slider')!;

		const outcome = settleFieldActions(form);
		expect((requests[0].sent as {value: string}).value).toBe('90');
		requests[0].answer(200, reject('budget', 'Too much'));

		expect(await outcome).toBe(slider);
		expect(slider.getAttribute('aria-invalid')).toBe('true');
		expect(slider.validity.customError).toBe(true);
		expect(form.querySelector('.fmdb-validation-error')!.textContent).toBe('Too much');
	});

	it('clears the verdicts on reset: the browser restores the values but not a customValidity', async () => {
		const {form} = formWith(textField('firstName'));
		const input = form.querySelector('input')!;
		input.value = 'spam';
		input.dispatchEvent(bubbling('focusout'));
		requests[0].answer(200, {verdict: 'reject', messages: [
			{level: 'error', html: 'no', field: 'firstName'},
			{level: 'warning', html: 'careful', field: 'firstName'},
		]});
		await settled();

		form.dispatchEvent(new Event('reset'));

		expect(input.validity.valid).toBe(true);
		expect(form.querySelector('.fmdb-validation-warning')).toBeNull();
		expect(input.hasAttribute('aria-describedby')).toBe(false);
	});

	it('drops the answer of a check started before a reset: a slow refusal never lands on the value typed after it', async () => {
		const {form} = formWith(textField('firstName'));
		const input = form.querySelector('input')!;
		input.value = 'spam';
		input.dispatchEvent(bubbling('focusout'));

		form.dispatchEvent(new Event('reset'));
		input.value = 'Ada';
		input.dispatchEvent(bubbling('focusout'));
		requests[1].answer(200, accept);
		await settled();
		requests[0].answer(200, reject('firstName', 'no'));
		await settled();

		expect(input.value).toBe('Ada');
		expect(input.validity.valid).toBe(true);
		expect(form.querySelector('.fmdb-validation-error')).toBeNull();
	});
});
