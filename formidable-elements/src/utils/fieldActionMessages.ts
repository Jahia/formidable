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

/**
 * The controls of one field. The named ones carry the value the field submits; the anchor is the one
 * the visitor sees — the message is drawn and described under it, the focus goes to it. They differ
 * for a range field, whose named control is a hidden mirror of the slider, and coincide everywhere else.
 */
export interface FieldControls {
	named: FormControl[];
	anchor: FormControl | null;
}

const isHiddenInput = (control: FormControl): boolean => control instanceof HTMLInputElement && control.type === 'hidden';

const controlsIn = (root: ParentNode): FormControl[] => Array.from(root.querySelectorAll<FormControl>('input, select, textarea'));

const anchorAmong = (named: FormControl[], all: FormControl[]): FormControl | null =>
	named.find(control => !isHiddenInput(control)) ?? all.find(control => !isHiddenInput(control)) ?? named[0] ?? null;

/** The controls of the field an element wrapper renders: the named ones, and the first visible one as the anchor. */
export const fieldControlsIn = (wrapper: HTMLElement, field: string): FieldControls => {
	const all = controlsIn(wrapper);
	const named = all.filter(control => control.name === field);
	return {named, anchor: anchorAmong(named, all)};
};

/** The controls of a field named by the engine: through its wrapper when the form has one, by name otherwise. */
export const fieldControlsOf = (form: HTMLFormElement, field: string): FieldControls => {
	const wrapper = form.querySelector<HTMLElement>(`[data-fmdb-node-name="${CSS.escape(field)}"]`);
	if (wrapper) {
		return fieldControlsIn(wrapper, field);
	}
	const named = controlsIn(form).filter(control => control.name === field);
	return {named, anchor: anchorAmong(named, named)};
};

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

/**
 * What the field actions wrote into a control's `customValidity`, per control — theirs to clear and
 * no one else's: a required checkbox group keeps its own "select at least one" there, and clearing it
 * would let an empty group through.
 */
const written = new WeakMap<FormControl, string>();

/** The named controls and the anchor, once each: what a refusal marks invalid. */
const markedControls = ({named, anchor}: FieldControls): FormControl[] =>
	anchor && !named.includes(anchor) ? [...named, anchor] : named;

/**
 * Lifts the validity the field actions set on the field's controls, and nothing another client set over
 * it. A control barred from constraint validation — disabled, which is how logic hides a field —
 * reports no validation message at all, so what it holds cannot be compared: it is lifted, since the
 * refusal would otherwise outlive the field's return and block every submission.
 */
export const clearFieldActionValidity = (controls: FieldControls): void => {
	for (const control of markedControls(controls)) {
		const ours = written.get(control);
		if (ours !== undefined && (!control.willValidate || control.validationMessage === ours)) {
			control.setCustomValidity('');
		}
		written.delete(control);
	}
};

/**
 * Shows a field's messages under it and nothing of its earlier ones: the first error blocks — the
 * controls' customValidity carries its text, so the browser's constraint validation refuses the
 * submission until the value changes, and the error element shows the contributor's HTML — the
 * first warning advises. No message: the field's own messages gone, its validity as the field
 * actions found it.
 */
export const showFieldMessages = (controls: FieldControls, messages: FieldMessage[]): void => {
	const anchor = controls.anchor ?? controls.named[0];
	if (!anchor) return;
	clearFieldError(anchor);
	clearFieldWarning(anchor);
	clearFieldActionValidity(controls);
	const error = messages.find(message => message.level === 'error');
	const warning = messages.find(message => message.level === 'warning');
	if (error) {
		// a refusal whose message has no text still has to block: a single space is a non-empty validity message
		const validity = plainText(error.html) || ' ';
		for (const control of markedControls(controls)) {
			control.setCustomValidity(validity);
			written.set(control, validity);
		}
		showFieldError(anchor, error.html, {html: true});
	}
	if (warning) showFieldWarning(anchor, warning.html);
};

/**
 * Anchors the messages of a refused submission on their fields, as the pre-check anchors its own.
 * Returns the anchor of the first field that got an error — for the focus — or null when no message
 * found its field in the form, in which case the caller falls back on the form's own error message: a
 * refusal the visitor cannot see is worse than a generic one.
 */
export const anchorFieldMessages = (form: HTMLFormElement, messages: FieldMessage[]): FormControl | null => {
	const byField = new Map<string, FieldMessage[]>();
	messages.forEach(message => byField.set(message.field, [...(byField.get(message.field) ?? []), message]));
	let firstRefused: FormControl | null = null;
	byField.forEach((fieldMessages, field) => {
		const controls = fieldControlsOf(form, field);
		const anchor = controls.anchor ?? controls.named[0];
		if (!anchor) return;
		showFieldMessages(controls, fieldMessages);
		if (!firstRefused && fieldMessages.some(message => message.level === 'error')) {
			firstRefused = anchor;
		}
	});
	return firstRefused;
};
