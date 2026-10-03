import {describe, expect, it} from 'vitest';
import {nextEntryIndex} from './FormResults.utils';

describe('nextEntryIndex', () => {
	it('moves down and up through the list, wrapping around at both ends', () => {
		expect(nextEntryIndex(3, 0, 'ArrowDown')).toBe(1);
		expect(nextEntryIndex(3, 2, 'ArrowDown')).toBe(0);
		expect(nextEntryIndex(3, 1, 'ArrowUp')).toBe(0);
		expect(nextEntryIndex(3, 0, 'ArrowUp')).toBe(2);
	});

	it('starts at the first entry downwards and the last upwards when nothing is selected', () => {
		expect(nextEntryIndex(3, -1, 'ArrowDown')).toBe(0);
		expect(nextEntryIndex(3, -1, 'ArrowUp')).toBe(2);
	});
});
