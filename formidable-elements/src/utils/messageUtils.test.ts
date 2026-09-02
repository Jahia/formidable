import {describe, expect, it} from 'vitest';
import {interpolateMessage} from './messageUtils';

const formDataOf = (entries: Record<string, string | string[]>): FormData => {
	const formData = new FormData();
	for (const [name, value] of Object.entries(entries)) {
		for (const one of Array.isArray(value) ? value : [value]) {
			formData.append(name, one);
		}
	}

	return formData;
};

describe('interpolateMessage', () => {
	it('formats a date field from its parts, immune to the runner timezone', () => {
		// new Date('2024-01-15') parses as UTC midnight, so formatting in a zone west
		// of UTC displayed January 14. Built from the parts, the day never shifts.
		const message = interpolateMessage('Sent on ${when}', formDataOf({when: '2024-01-15'}),
			'en-US', () => 'date');

		expect(message).toContain('January 15, 2024');
	});

	it('keeps the time of a datetime-local field', () => {
		const message = interpolateMessage('At ${when}', formDataOf({when: '2024-01-15T09:30'}),
			'en-US', () => 'datetime-local');

		expect(message).toContain('January 15, 2024');
		expect(message).toMatch(/09:30/);
	});

	it('localizes a number field', () => {
		const message = interpolateMessage('Budget: ${budget}', formDataOf({budget: '50000'}),
			'fr-FR', () => 'number');

		// fr grouping separator is a (narrow) no-break space; pin the grouping, not the codepoint.
		expect(message).toMatch(/50[\u202F\u00A0 ]000/);
	});

	it('localizes a range field, whose submitted control is a hidden mirror', () => {
		const message = interpolateMessage('Budget: ${budget}', formDataOf({budget: '50000'}),
			'fr-FR', () => 'range');

		expect(message).toMatch(/50[\u202F\u00A0 ]000/);
	});

	it('renders a numeric-looking text answer verbatim', () => {
		// A postal code round-trips through parseFloat: formatting is gated on the
		// FIELD kind, never on the shape of the value.
		const message = interpolateMessage('Code: ${zip}', formDataOf({zip: '75001'}),
			'fr-FR', () => null);

		expect(message).toBe('Code: 75001');
	});

	it('joins multiple values and blanks a missing field', () => {
		const message = interpolateMessage('Picked ${tags} by ${nobody}',
			formDataOf({tags: ['music', 'sport']}), 'en-US', () => null);

		expect(message).toBe('Picked music, sport by ');
	});
});
