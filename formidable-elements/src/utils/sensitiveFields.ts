/**
 * The marks of a sensitive field: a field type whose value is a secret by nature, a password. The server render reads
 * the mixin, the browser reads the wrapper's marker and the controls under it.
 */

/** A secret by nature (a password): never a logic source, never printed, emptied after a refusal. */
export const SENSITIVE_FIELD_MIXIN = 'fmdbmix:sensitiveField';

/**
 * Set by the element wrapper (LogicAwareRender) on a field whose type carries {@link SENSITIVE_FIELD_MIXIN}, whatever
 * its control's type (a PIN in a text input, a show/hide toggle).
 */
const SENSITIVE_ATTRIBUTE = 'data-fmdb-sensitive';
export const SENSITIVE_SELECTOR = `[${SENSITIVE_ATTRIBUTE}="true"]`;

/** The input types whose value is not typed by the visitor: never a secret, and emptying it would change what the control submits. */
const NOT_TYPED_INPUTS = new Set(['checkbox', 'radio', 'button', 'submit', 'reset', 'image']);

/**
 * Whether a control holds a secret: every text control of a sensitive field's wrapper, whatever its type (a PIN in a
 * text input, the mirror of a show/hide toggle), and every password control, wrapped or not.
 */
export const isSecretControl = (control: Element): control is HTMLInputElement | HTMLTextAreaElement => {
	if (control instanceof HTMLInputElement) {
		return control.type === 'password' || (!NOT_TYPED_INPUTS.has(control.type) && control.closest(SENSITIVE_SELECTOR) !== null);
	}
	return control instanceof HTMLTextAreaElement && control.closest(SENSITIVE_SELECTOR) !== null;
};
