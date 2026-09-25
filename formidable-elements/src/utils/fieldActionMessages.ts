import {clearFieldError, clearFieldWarning, showFieldError, showFieldWarning} from './validationUtils';

export type FormControl = HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement;

/**
 * One entry of the engine's `messages` array — the pre-check's answer and a refused submission carry
 * the same shape (docs/administration/error-codes.md): the level, the contributor's message rendered
 * and escaped server-side, the node name of the field it is about. Nothing else: the action behind it
 * stays in the server logs.
 */
export interface FieldMessage {
	level: 'error' | 'warning';
	html: string;
	field: string;
}

/** The `messages` of a parsed engine answer, or nothing for any other shape — a body is never trusted for its form. */
export const parseFieldMessages = (body: unknown): FieldMessage[] => {
	const messages = (body as {messages?: unknown} | null)?.messages;
	if (!Array.isArray(messages)) return [];
	return messages.flatMap((entry): FieldMessage[] => {
		if (typeof entry !== 'object' || entry === null) return [];
		const {level, html, field} = entry as Record<string, unknown>;
		if (typeof field !== 'string' || typeof html !== 'string') return [];
		return [{level: level === 'warning' ? 'warning' : 'error', html, field}];
	});
};

/** The message as `setCustomValidity` wants it: the contributor's rich text without its markup. */
export const plainText = (html: string): string =>
	new DOMParser().parseFromString(html, 'text/html').body.textContent?.replaceAll(/\s+/g, ' ').trim() ?? '';

/** The controls a message about `field` anchors on: the form's controls of that name, whatever their type. */
export const controlsOf = (form: HTMLFormElement, field: string): FormControl[] => {
	const name = CSS.escape(field);
	return Array.from(form.querySelectorAll<FormControl>(`input[name="${name}"], select[name="${name}"], textarea[name="${name}"]`));
};

/**
 * Shows a field's messages under it and nothing of its earlier ones: the first error blocks — the
 * control's customValidity carries its text, so the browser's constraint validation refuses the
 * submission until the value changes, and the error element shows the contributor's HTML — the
 * first warning advises. No message: the field is valid again, its messages gone.
 */
export const showFieldMessages = (controls: FormControl[], messages: FieldMessage[]): void => {
	const [first] = controls;
	if (!first) return;
	clearFieldError(first);
	clearFieldWarning(first);
	const error = messages.find(message => message.level === 'error');
	const warning = messages.find(message => message.level === 'warning');
	// a refusal whose message has no text still has to block: a single space is a non-empty validity message
	const validity = error ? plainText(error.html) || ' ' : '';
	controls.forEach(control => control.setCustomValidity(validity));
	if (error) showFieldError(first, error.html, {html: true});
	if (warning) showFieldWarning(first, warning.html);
};

/**
 * Anchors the messages of a refused submission on their fields, as the pre-check anchors its own.
 * Returns the first control that got an error — for the focus — or null when no message found its
 * field in the form, in which case the caller falls back on the form's own error message: a refusal
 * the visitor cannot see is worse than a generic one.
 */
export const anchorFieldMessages = (form: HTMLFormElement, messages: FieldMessage[]): FormControl | null => {
	const byField = new Map<string, FieldMessage[]>();
	messages.forEach(message => byField.set(message.field, [...(byField.get(message.field) ?? []), message]));
	let firstRefused: FormControl | null = null;
	byField.forEach((fieldMessages, field) => {
		const controls = controlsOf(form, field);
		if (controls.length === 0) return;
		showFieldMessages(controls, fieldMessages);
		if (!firstRefused && fieldMessages.some(message => message.level === 'error')) {
			firstRefused = controls[0];
		}
	});
	return firstRefused;
};
