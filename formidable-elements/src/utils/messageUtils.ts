/** Resolves a field name to its control type ('date', 'number', …), or null when unknown. */
export type FieldKindResolver = (fieldName: string) => string | null;

/** The stored node types whose submitted value warrants a localized rendering. */
const NODE_TYPE_KINDS: Record<string, string> = {
	'fmdb:inputNumber': 'number',
	'fmdb:inputRange': 'range',
	'fmdb:inputDate': 'date',
	'fmdb:inputDatetimeLocal': 'datetime-local'
};

/**
 * The field kinds of a live form. The wrapper's data-fmdb-node-type is authoritative:
 * some fields submit through a mirrored control whose own type lies about the kind —
 * the slider's named control is a hidden input, so reading the control's type made the
 * range branch unreachable. The control's type stays as a fallback for markup without
 * a wrapper (a radio group resolves through its first control; RadioNodeList itself
 * carries no type).
 */
export const fieldKindFromForm = (form: HTMLFormElement): FieldKindResolver => fieldName => {
	const wrapper = form.querySelector<HTMLElement>(
		`[data-fmdb-node-name="${CSS.escape(fieldName)}"]`);
	const nodeType = wrapper?.dataset.fmdbNodeType;
	if (nodeType) {
		return NODE_TYPE_KINDS[nodeType] ?? null;
	}

	const control = form.elements.namedItem(fieldName);
	const element = control instanceof RadioNodeList ? control[0] : control;
	return element instanceof HTMLInputElement ? element.type : null;
};

export const interpolateMessage = (
	message: string | undefined,
	formData: FormData,
	locale: string = 'fr-FR',
	fieldKind: FieldKindResolver = () => null
): string | undefined => {
	if (!message) return message;

	let interpolatedMessage = message;

	// Replace ${fieldName} with form field values
	const variableRegex = /\$\{([^}]+)\}/g;
	interpolatedMessage = interpolatedMessage.replace(variableRegex, (match, fieldName) => {
		// Get all values for this field name (handles multiple values)
		const values = formData.getAll(fieldName);
		const kind = fieldKind(fieldName);

		if (values.length === 0) {
			return '';
		} else if (values.length === 1) {
			return formatValue(String(values[0]), locale, kind);
		} else {
			// Join multiple values with commas for readability
			return values.map(value => formatValue(String(value), locale, kind)).join(', ');
		}
	});

	return interpolatedMessage;
};

/**
 * A picker value rendered as a localized date. Built from the YYYY-MM-DD parts, never
 * new Date(value): a date-only ISO string parses as UTC midnight while toLocaleString
 * formats in the visitor's zone, so every zone west of UTC displayed the previous day
 * (the same trap Input/Date/bounds.ts documents).
 */
const formatDateValue = (value: string, locale: string): string => {
	const parts = /^(\d{4})-(\d{2})-(\d{2})(?:T(\d{2}):(\d{2})(?::\d{2})?)?$/.exec(value);
	if (!parts) return value;

	const [, year, month, day, hour, minute] = parts;
	const hasTime = hour !== undefined;
	const date = new Date(
		Number(year), Number(month) - 1, Number(day),
		hasTime ? Number(hour) : 0, hasTime ? Number(minute) : 0
	);
	if (isNaN(date.getTime())) return value;

	return date.toLocaleString(locale, {
		year: 'numeric',
		month: 'long',
		day: 'numeric',
		...(hasTime && {
			hour: '2-digit',
			minute: '2-digit'
		})
	});
};

/**
 * Localized rendering gated on the FIELD's kind, never on the shape of the value: a
 * postal code, an order reference or a phone number typed in a text field round-trips
 * through parseFloat, and "75001" must not come back as "75 001".
 */
const formatValue = (value: string, locale: string, kind: string | null): string => {
	if (!value) return '';

	if (kind === 'date' || kind === 'datetime-local') {
		return formatDateValue(value, locale);
	}

	if (kind === 'number' || kind === 'range') {
		const numberValue = Number(value);
		if (!isNaN(numberValue) && isFinite(numberValue)) {
			return numberValue.toLocaleString(locale);
		}
	}

	return value;
};
