import {describe, expect, it} from 'vitest';
import {formatFieldValue, parseFormFields} from './FormResults.utils';

describe('formatFieldValue', () => {
	it('shows a date field value as the reader\'s local date, not shifted by the zone', () => {
		// "2026-09-09" parsed as UTC midnight reads as the 8th west of Greenwich.
		expect(formatFieldValue('2026-09-09', 'date')).toEqual(new Date(2026, 8, 9).toLocaleDateString());
	});

	it('shows a datetime field value as the reader\'s local date and time, without the ISO separator', () => {
		const expected = new Date(2026, 8, 5, 12, 53).toLocaleString(undefined, {dateStyle: 'short', timeStyle: 'short'});

		expect(formatFieldValue('2026-09-05T12:53', 'datetime')).toEqual(expected);
		expect(formatFieldValue('2026-09-05T12:53:00', 'datetime')).toEqual(expected);
		expect(formatFieldValue('2026-09-05T12:53', 'datetime')).not.toContain('T');
	});

	it('leaves a value that does not parse as stored', () => {
		expect(formatFieldValue('yesterday', 'date')).toEqual('yesterday');
		expect(formatFieldValue('2026-09-05', 'datetime')).toEqual('2026-09-05');
		expect(formatFieldValue('2026-13-45', 'date')).toEqual('2026-13-45');
		expect(formatFieldValue('2026-09-05T25:61', 'datetime')).toEqual('2026-09-05T25:61');
	});

	it('leaves the value of any other field kind as stored', () => {
		expect(formatFieldValue('2026-09-09', undefined)).toEqual('2026-09-09');
	});
});

describe('parseFormFields', () => {
	it('records which fields carry a date or a datetime', () => {
		const fields = parseFormFields({
			jcr: {nodeById: {fields: {nodes: [{descendants: {nodes: [
				{name: 'birthday', displayName: 'Birthday', isDate: true, isDatetime: false},
				{name: 'appointment', displayName: 'Appointment', isDate: false, isDatetime: true},
				{name: 'comment', displayName: 'Comment', isDate: false, isDatetime: false}
			]}}]}}}
		});

		expect(fields.order).toEqual(['birthday', 'appointment', 'comment']);
		expect(fields.kinds.get('birthday')).toEqual('date');
		expect(fields.kinds.get('appointment')).toEqual('datetime');
		expect(fields.kinds.has('comment')).toEqual(false);
	});
});
