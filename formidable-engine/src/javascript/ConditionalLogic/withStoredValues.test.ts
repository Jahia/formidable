import {describe, expect, it} from 'vitest';
import {withStoredValues} from './ConditionalLogic.utils';

describe('withStoredValues', () => {
	it('keeps the options untouched when every stored value is known', () => {
		const options = [{label: 'Red', value: 'red'}, {label: 'Green', value: 'green'}];

		expect(withStoredValues(options, ['red'])).toBe(options);
	});

	it('appends a raw-value option for a stored value the current language does not know', () => {
		// A 0.3-migrated field can keep divergent per-language values until its first
		// save: the FRENCH list holds rouge/vert while the shared rule stores 'red'.
		// An empty chip reads as data loss — the raw value is what the rule compares.
		const french = [{label: 'Rouge', value: 'rouge'}, {label: 'Vert', value: 'vert'}];

		expect(withStoredValues(french, ['red'])).toEqual([
			{label: 'Rouge', value: 'rouge'},
			{label: 'Vert', value: 'vert'},
			{label: 'red', value: 'red'}
		]);
	});

	it('ignores empty stored values', () => {
		expect(withStoredValues([], [''])).toEqual([]);
	});
});
