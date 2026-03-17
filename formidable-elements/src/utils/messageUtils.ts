export const interpolateMessage = (
	message: string | undefined,
	formData: FormData,
	locale: string = 'fr-FR'
): string | undefined => {
	if (!message) return message;

	let interpolatedMessage = message;

	// Replace ${fieldName} with form field values
	const variableRegex = /\$\{([^}]+)\}/g;
	interpolatedMessage = interpolatedMessage.replace(variableRegex, (match, fieldName) => {
		// Get all values for this field name (handles multiple values)
		const values = formData.getAll(fieldName);

		if (values.length === 0) {
			return '';
		} else if (values.length === 1) {
			return formatValue(String(values[0]), locale);
		} else {
			// Join multiple values with commas for readability
			return values.map(value => formatValue(String(value), locale)).join(', ');
		}
	});

	return interpolatedMessage;
};

const formatValue = (value: string, locale: string): string => {
	if (!value) return '';

	// Check if value is a date (YYYY-MM-DD or YYYY-MM-DDTHH:MM)
	const dateRegex = /^\d{4}-\d{2}-\d{2}(T\d{2}:\d{2}(:\d{2})?)?$/;
	if (dateRegex.test(value)) {
		const date = new Date(value);
		if (!isNaN(date.getTime())) {
			// Format as date with time if time is included
			const hasTime = value.includes('T');
			return date.toLocaleString(locale, {
				year: 'numeric',
				month: 'long',
				day: 'numeric',
				...(hasTime && {
					hour: '2-digit',
					minute: '2-digit'
				})
			});
		}
	}

	// Check if value is a number
	const numberValue = parseFloat(value);
	if (!isNaN(numberValue) && isFinite(numberValue) && String(numberValue) === value) {
		return numberValue.toLocaleString(locale);
	}

	// Return original value if not a date or number
	return value;
};
