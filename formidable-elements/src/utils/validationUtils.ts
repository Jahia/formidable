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
const INVALID_CLASS = 'fmdb-invalid';

export const resolveValidationMessage = (
	input: HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement,
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

export const showFieldError = (
	input: HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement,
	message: string,
): void => {
	clearFieldError(input);
	input.classList.add(INVALID_CLASS);

	const errorEl = document.createElement('div');
	errorEl.className = ERROR_CLASS;
	errorEl.setAttribute('role', 'alert');
	errorEl.setAttribute('aria-live', 'polite');
	errorEl.textContent = message;

	const formGroup = input.closest('.fmdb-form-group');
	if (formGroup) {
		formGroup.appendChild(errorEl);
	} else {
		input.insertAdjacentElement('afterend', errorEl);
	}
};

export const clearFieldError = (
	input: HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement,
): void => {
	input.classList.remove(INVALID_CLASS);

	const formGroup = input.closest('.fmdb-form-group');
	const container = formGroup ?? input.parentElement;
	if (container) {
		const existing = container.querySelector(`.${ERROR_CLASS}`);
		existing?.remove();
	}
};

export const clearAllFieldErrors = (form: HTMLFormElement): void => {
	form.querySelectorAll(`.${ERROR_CLASS}`).forEach(el => el.remove());
	form.querySelectorAll(`.${INVALID_CLASS}`).forEach(el => el.classList.remove(INVALID_CLASS));
};

