import {type RefObject, useEffect, useRef} from 'react';
import {clearAllFieldWarnings, clearFieldError} from '~/utils/validationUtils';
import {type FieldMessage, type FormControl, parseFieldMessages, showFieldMessages} from '~/utils/fieldActionMessages';

/**
 * The marker the element wrapper carries when the field has actions: `blur` when the engine is to be
 * asked as the visitor leaves the field, `submit` when every check waits for the submission. Absent,
 * the field has no action and is never asked about (docs/architecture/field-actions.md, "The browser").
 */
export const FIELD_ACTION_MARKER = 'data-fmdb-field-action';
/** On the wrapper while a check is in flight; aria-busy says the same to assistive technology. */
export const PENDING_CLASS = 'fmdb-field-action-pending';
/** A check that takes longer is treated as unanswered: the visitor is never held, the pipeline judges anyway. */
const REQUEST_TIMEOUT_MS = 10_000;

/** What the engine's pre-check endpoint answers a call about one value. */
interface Answer {
	verdict: 'accept' | 'advice' | 'reject';
	messages: FieldMessage[];
}

/** The outcome of one check of a field: every value asked about, the answers merged. */
export interface FieldVerdict {
	rejected: boolean;
	messages: FieldMessage[];
}

interface UseFieldActionsOptions {
	formRef: RefObject<HTMLFormElement | null>;
	/** The engine's endpoint for this form and language, from the server; undefined = never ask. */
	fieldActionUrl?: string;
	/** Off in edit mode: the rules describe the visitor experience, so they never run while the form is authored. */
	enabled: boolean;
}

interface UseFieldActionsReturn {
	/**
	 * Asks the engine about every field with actions before the submission is sent — the blur-checked
	 * ones again (the engine's verdict cache makes that free) and the submit-only ones for the first time.
	 * Resolves false with the messages shown and the first refused control focused, true otherwise:
	 * warnings are shown and the submission proceeds, and a check that could not be asked blocks nothing,
	 * the pipeline being the authority.
	 */
	settleFieldActions: (form: HTMLFormElement) => Promise<boolean>;
}

const isFormControl = (target: EventTarget | null): target is FormControl =>
	target instanceof HTMLInputElement || target instanceof HTMLSelectElement || target instanceof HTMLTextAreaElement;

const NEVER_ASKED_TYPES = new Set(['file', 'button', 'submit', 'reset', 'image']);

const wrapperOf = (control: Element): HTMLElement | null => control.closest<HTMLElement>(`[${FIELD_ACTION_MARKER}]`);

/** A field conditional logic holds hidden is not asked about: the pipeline skips it too. */
const isAskable = (wrapper: HTMLElement): boolean => wrapper.dataset.fmdbLogicHidden !== 'true';

/** The controls that carry the field's value — the named ones of the wrapper, whatever their type. */
const namedControlsIn = (wrapper: HTMLElement, field: string): FormControl[] =>
	Array.from(wrapper.querySelectorAll<FormControl>('input, select, textarea')).filter(control => control.name === field);

/**
 * The values the field would submit right now, blank ones dropped: one per selected option or checked
 * box, the checked radio's, the control's otherwise. Each is asked about on its own — the engine judges
 * one value per call — and a value repeated is asked once.
 */
export const valuesOf = (controls: FormControl[]): string[] => {
	const values = new Set<string>();
	for (const control of controls) {
		if (control.disabled) continue;
		if (control instanceof HTMLSelectElement) {
			Array.from(control.selectedOptions).forEach(option => values.add(option.value.trim()));
			continue;
		}
		if (control instanceof HTMLInputElement) {
			if (NEVER_ASKED_TYPES.has(control.type)) continue;
			if ((control.type === 'radio' || control.type === 'checkbox') && !control.checked) continue;
		}
		values.add(control.value.trim());
	}
	values.delete('');
	return Array.from(values);
};

/**
 * The moment a blur check runs for a control: `change` for the choice controls (a select, a radio, a
 * box — leaving them says nothing), `focusout` for the others (`blur` does not bubble). One of the two,
 * never both, so a text field is asked once when the visitor leaves it.
 */
const asksOn = (control: FormControl, eventType: string): boolean => {
	const choice = control instanceof HTMLSelectElement
		|| (control instanceof HTMLInputElement && (control.type === 'radio' || control.type === 'checkbox'));
	return choice ? eventType === 'change' : eventType === 'focusout';
};

const setPending = (wrapper: HTMLElement, pending: boolean): void => {
	wrapper.classList.toggle(PENDING_CLASS, pending);
	if (pending) {
		wrapper.setAttribute('aria-busy', 'true');
	} else {
		wrapper.removeAttribute('aria-busy');
	}
};

export function useFieldActions({formRef, fieldActionUrl, enabled}: UseFieldActionsOptions): UseFieldActionsReturn {
	// Per field, the number of the latest check: an answer to an older one is dropped, so a visitor
	// who leaves a field twice quickly never sees the first value's verdict shown for the second.
	const sequencesRef = useRef(new Map<string, number>());
	// One console line per status: the pre-check is a courtesy, its failure is not the visitor's business.
	const warnedRef = useRef(new Set<number>());

	/**
	 * One value to the engine, null when it could not answer — a network error, a timeout, any status
	 * but a 2xx (401 members only, 404 disabled or unknown, 429 rate limit, 5xx) or a body that is not
	 * an answer. XHR rather than fetch: Jahia's CSRFGuard integrates with XMLHttpRequest, as the
	 * submission's own request notes.
	 */
	const ask = (body: {field: string; value: string; trigger: 'blur' | 'submit'}): Promise<Answer | null> =>
		new Promise(resolve => {
			const xhr = new XMLHttpRequest();
			xhr.open('POST', fieldActionUrl!, true);
			xhr.setRequestHeader('Content-Type', 'application/json');
			xhr.withCredentials = true;
			xhr.timeout = REQUEST_TIMEOUT_MS;
			xhr.onload = () => {
				if (xhr.status < 200 || xhr.status >= 300) {
					if (!warnedRef.current.has(xhr.status)) {
						warnedRef.current.add(xhr.status);
						console.warn(`[Formidable] The field check answered ${xhr.status}; the value will be checked at submission.`);
					}
					resolve(null);
					return;
				}
				try {
					const parsed = JSON.parse(xhr.responseText) as {verdict?: unknown};
					const verdict = parsed.verdict;
					resolve(verdict === 'accept' || verdict === 'advice' || verdict === 'reject'
						? {verdict, messages: parseFieldMessages(parsed)}
						: null);
				} catch {
					resolve(null);
				}
			};
			xhr.onerror = () => resolve(null);
			xhr.ontimeout = () => resolve(null);
			xhr.send(JSON.stringify(body));
		});

	/**
	 * Checks one field: every value it holds, in parallel; the answers merged — any refusal refuses,
	 * else any message advises, else the field is accepted — and shown under the field. Null when the
	 * check was superseded by a later one for the same field, or when no value could be asked about.
	 */
	const check = async (wrapper: HTMLElement, trigger: 'blur' | 'submit'): Promise<FieldVerdict | null> => {
		const field = wrapper.dataset.fmdbNodeName;
		if (!field) return null;
		const controls = namedControlsIn(wrapper, field);
		const sequence = (sequencesRef.current.get(field) ?? 0) + 1;
		sequencesRef.current.set(field, sequence);
		const values = valuesOf(controls);
		if (values.length === 0) {
			// an unanswered field says nothing to check: whatever it showed is gone
			showFieldMessages(controls, []);
			return {rejected: false, messages: []};
		}
		setPending(wrapper, true);
		const answers = await Promise.all(values.map(value => ask({field, value, trigger})));
		if (sequencesRef.current.get(field) !== sequence) {
			// a later check owns the field now, its pending state included
			return null;
		}
		setPending(wrapper, false);
		if (answers.every(answer => answer === null)) {
			// could not ask: nothing shown, nothing blocked
			return null;
		}
		const messages = answers.flatMap(answer => answer?.messages ?? []);
		const rejected = answers.some(answer => answer?.verdict === 'reject');
		showFieldMessages(controls, messages);
		return {rejected, messages};
	};

	useEffect(() => {
		const form = formRef.current;
		if (!form || !enabled || !fieldActionUrl) return;

		const onLeave = (event: Event) => {
			const control = event.target;
			if (!isFormControl(control) || control.disabled || !asksOn(control, event.type)) return;
			const wrapper = wrapperOf(control);
			if (!wrapper || !isAskable(wrapper) || wrapper.dataset.fmdbFieldAction !== 'blur') return;
			void check(wrapper, 'blur');
		};

		// Capture phase, so it runs before useCustomFormValidation's own input handler, which only
		// clears an error once the control is valid again — and a refusal keeps it invalid until here.
		// The next leave re-checks; a warning stays until the next answer.
		const onInput = (event: Event) => {
			const control = event.target;
			if (!isFormControl(control)) return;
			const wrapper = wrapperOf(control);
			const field = wrapper?.dataset.fmdbNodeName;
			if (!wrapper || !field) return;
			const controls = namedControlsIn(wrapper, field);
			controls.forEach(named => named.setCustomValidity(''));
			if (controls[0]) clearFieldError(controls[0]);
		};

		// The browser's reset restores the values but not a customValidity, nor what was drawn: every
		// verdict goes, the fields' own errors included (useCustomFormValidation clears them too, on the
		// same event — this hook does not rely on it).
		const onReset = () => {
			sequencesRef.current.clear();
			form.querySelectorAll<HTMLElement>(`[${FIELD_ACTION_MARKER}]`).forEach(wrapper => {
				setPending(wrapper, false);
				const field = wrapper.dataset.fmdbNodeName;
				const controls = field ? namedControlsIn(wrapper, field) : [];
				controls.forEach(control => control.setCustomValidity(''));
				if (controls[0]) clearFieldError(controls[0]);
			});
			clearAllFieldWarnings(form);
		};

		form.addEventListener('focusout', onLeave);
		form.addEventListener('change', onLeave);
		form.addEventListener('input', onInput, true);
		form.addEventListener('reset', onReset);

		return () => {
			form.removeEventListener('focusout', onLeave);
			form.removeEventListener('change', onLeave);
			form.removeEventListener('input', onInput, true);
			form.removeEventListener('reset', onReset);
		};
	}, [formRef, enabled, fieldActionUrl]);

	const settleFieldActions = async (form: HTMLFormElement): Promise<boolean> => {
		if (!enabled || !fieldActionUrl) return true;
		const wrappers = Array.from(form.querySelectorAll<HTMLElement>(`[${FIELD_ACTION_MARKER}]`)).filter(isAskable);
		const verdicts = await Promise.all(wrappers.map(wrapper => check(wrapper, 'submit')));
		const refused = wrappers.find((_, index) => verdicts[index]?.rejected);
		if (!refused) return true;
		const field = refused.dataset.fmdbNodeName;
		const [first] = field ? namedControlsIn(refused, field) : [];
		first?.focus();
		return false;
	};

	return {settleFieldActions};
}
