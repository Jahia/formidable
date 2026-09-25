type ValidityFlag =
	| 'valueMissing'
	| 'typeMismatch'
	| 'patternMismatch'
	| 'tooShort'
	| 'tooLong'
	| 'rangeUnderflow'
	| 'rangeOverflow'
	| 'stepMismatch'
	| 'badInput';

const VALIDITY_FLAGS: ValidityFlag[] = [
	'valueMissing',
	'typeMismatch',
	'patternMismatch',
	'tooShort',
	'tooLong',
	'rangeUnderflow',
	'rangeOverflow',
	'stepMismatch',
	'badInput',
];

const FLAG_TO_DATA_ATTR: Record<ValidityFlag, string> = {
	valueMissing: 'data-fmdb-msg-value-missing',
	typeMismatch: 'data-fmdb-msg-type-mismatch',
	patternMismatch: 'data-fmdb-msg-pattern-mismatch',
	tooShort: 'data-fmdb-msg-too-short',
	tooLong: 'data-fmdb-msg-too-long',
	rangeUnderflow: 'data-fmdb-msg-range-underflow',
	rangeOverflow: 'data-fmdb-msg-range-overflow',
	stepMismatch: 'data-fmdb-msg-step-mismatch',
	badInput: 'data-fmdb-msg-bad-input',
};

const ERROR_CLASS = 'fmdb-validation-error';
// The twin of the error: a message that advises without blocking (a field action set to warn only).
const WARNING_CLASS = 'fmdb-validation-warning';
const INVALID_CLASS = 'fmdb-invalid';

type FormInputElement = HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement;

const getGroupedInputs = (input: FormInputElement): FormInputElement[] => {
	if (!(input instanceof HTMLInputElement)) return [input];
	if ((input.type !== 'radio' && input.type !== 'checkbox') || !input.name || !input.form) return [input];

	return Array.from(
		input.form.querySelectorAll<HTMLInputElement>(`input[type="${input.type}"][name="${CSS.escape(input.name)}"]`)
	);
};

/**
 * A form's own id, never the control that shadows it. `HTMLFormElement` is `[LegacyOverrideBuiltIns]`:
 * a control whose name matches an IDL attribute takes that property over, and a field's name is the
 * contributor's system name — so a field named `id` turns `form.id` into an `HTMLInputElement`, which
 * is truthy and has no `replace`. A control named `id` cannot shadow the attribute read;
 * `getAttribute` itself could, which is accepted — no label leads a contributor to that name.
 */
export const formIdOf = (form: HTMLFormElement | null | undefined): string =>
	form?.getAttribute('id') ?? '';

const sanitizeIdPart = (value: string): string => {
	const sanitized = value.replace(/[^a-zA-Z0-9_-]+/g, '-').replace(/^-+|-+$/g, '');
	return sanitized || 'field';
};

/** The id of the message element of one kind under one field: stable, so it can be found again and referenced by aria-describedby. */
const buildMessageId = (input: FormInputElement, prefix: string): string => {
	if (input instanceof HTMLInputElement && (input.type === 'radio' || input.type === 'checkbox') && input.name) {
		const formId = formIdOf(input.form);
		const formPrefix = formId ? `${sanitizeIdPart(formId)}-` : '';
		return `${prefix}-${formPrefix}${sanitizeIdPart(input.type)}-${sanitizeIdPart(input.name)}`;
	}

	const base = input.id || input.name || 'field';
	return `${prefix}-${sanitizeIdPart(base)}`;
};

const buildErrorId = (input: FormInputElement): string => buildMessageId(input, ERROR_CLASS);
const buildWarningId = (input: FormInputElement): string => buildMessageId(input, WARNING_CLASS);

const updateDescribedBy = (
	input: FormInputElement,
	errorId: string,
	add: boolean
): void => {
	const current = (input.getAttribute('aria-describedby') ?? '')
		.split(/\s+/)
		.filter(Boolean);
	const next = add
		? Array.from(new Set([...current, errorId]))
		: current.filter(token => token !== errorId);

	if (next.length > 0) {
		input.setAttribute('aria-describedby', next.join(' '));
	} else {
		input.removeAttribute('aria-describedby');
	}
};

const clearFieldAria = (input: FormInputElement): void => {
	input.removeAttribute('aria-invalid');
	updateDescribedBy(input, buildErrorId(input), false);
};

/** Under the field's group when it has one (label + control + help), right after the control otherwise. */
const anchorMessage = (input: FormInputElement, element: HTMLElement): void => {
	const formGroup = input.closest('.fmdb-form-group');
	if (formGroup) {
		formGroup.appendChild(element);
	} else {
		input.after(element);
	}
};

export const resolveValidationMessage = (
	input: FormInputElement,
): string => {
	const v = input.validity;

	for (const flag of VALIDITY_FLAGS) {
		if (!v[flag]) continue;

		const customMsg = input.getAttribute(FLAG_TO_DATA_ATTR[flag]);
		if (customMsg) return customMsg;

		return input.validationMessage;
	}

	return input.validationMessage;
};

export interface ShowFieldErrorOptions {
	/**
	 * The message is HTML to render, not text: the contributor's rejection message of a field action,
	 * interpolated and escaped server-side — trusted as every contributor rich text of the module is.
	 * A constraint message stays text.
	 */
	html?: boolean;
}

export const showFieldError = (
	input: FormInputElement,
	message: string,
	options: ShowFieldErrorOptions = {},
): void => {
	clearFieldError(input);
	const groupedInputs = getGroupedInputs(input);
	groupedInputs.forEach(groupedInput => {
		groupedInput.classList.add(INVALID_CLASS);
		groupedInput.setAttribute('aria-invalid', 'true');
	});

	const errorEl = document.createElement('div');
	errorEl.id = buildErrorId(input);
	errorEl.className = ERROR_CLASS;
	errorEl.setAttribute('role', 'status');
	if (options.html) {
		errorEl.innerHTML = message;
	} else {
		errorEl.textContent = message;
	}
	groupedInputs.forEach(groupedInput => updateDescribedBy(groupedInput, errorEl.id, true));

	anchorMessage(input, errorEl);
};

/** Whether an error element is drawn under the control — by this client or by the field actions, which use the same id. */
export const hasFieldError = (input: FormInputElement): boolean =>
	input.ownerDocument.getElementById(buildErrorId(input)) !== null;

/** Invalid through `setCustomValidity` alone: no constraint of the control's own fails. */
export const isCustomErrorOnly = (input: FormInputElement): boolean =>
	input.validity.customError && VALIDITY_FLAGS.every(flag => !input.validity[flag]);

export const clearFieldError = (
	input: FormInputElement,
): void => {
	const groupedInputs = getGroupedInputs(input);
	groupedInputs.forEach(groupedInput => {
		groupedInput.classList.remove(INVALID_CLASS);
		clearFieldAria(groupedInput);
	});

	const errorId = buildErrorId(input);
	input.ownerDocument.getElementById(errorId)?.remove();
};

/**
 * A warning under the field: the message of a field action set to warn only, HTML rendered as the
 * error's is, anchored where the error would be and read by assistive technology through the
 * control's aria-describedby — but the control stays valid: no fmdb-invalid, no aria-invalid,
 * nothing blocks the submission.
 */
export const showFieldWarning = (
	input: FormInputElement,
	html: string,
): void => {
	clearFieldWarning(input);
	const warningEl = document.createElement('div');
	warningEl.id = buildWarningId(input);
	warningEl.className = WARNING_CLASS;
	warningEl.setAttribute('role', 'status');
	warningEl.innerHTML = html;
	getGroupedInputs(input).forEach(groupedInput => updateDescribedBy(groupedInput, warningEl.id, true));

	anchorMessage(input, warningEl);
};

export const clearFieldWarning = (
	input: FormInputElement,
): void => {
	const warningId = buildWarningId(input);
	getGroupedInputs(input).forEach(groupedInput => updateDescribedBy(groupedInput, warningId, false));
	input.ownerDocument.getElementById(warningId)?.remove();
};

export const clearAllFieldErrors = (form: HTMLFormElement): void => {
	form.querySelectorAll(`.${ERROR_CLASS}`).forEach(el => el.remove());
	form.querySelectorAll(`.${INVALID_CLASS}`).forEach(el => {
		el.classList.remove(INVALID_CLASS);
	});
	form.querySelectorAll<FormInputElement>('input, select, textarea')
		.forEach(input => clearFieldAria(input));
};

export const clearAllFieldWarnings = (form: HTMLFormElement): void => {
	form.querySelectorAll<FormInputElement>('input, select, textarea')
		.forEach(input => updateDescribedBy(input, buildWarningId(input), false));
	form.querySelectorAll(`.${WARNING_CLASS}`).forEach(el => el.remove());
};
