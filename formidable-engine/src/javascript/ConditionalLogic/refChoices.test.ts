import {afterEach, describe, expect, it, vi} from 'vitest';
import type {LogicSourceRefChoicesSource} from '@jahia/formidable-library/logic-sources';
import {acquireRefChoices, fetchRefChoices, forgetRefChoices, readyView, refChoiceOptions, UNAVAILABLE} from './refChoices';
import {getLogicProvider, type LogicProviderDescriptor} from './providers';

/** Where the provider below lists its references. */
const segmentChoices: LogicSourceRefChoicesSource = {
	query: 'query AcmeSegments($path: String!) { acmeSegments(path: $path) { name } }',
	read: data => {
		const names = (data as {acmeSegments?: {name: string}[] | null} | undefined)?.acmeSegments;
		return names ? {available: true, choices: names.map(({name}) => ({value: name, label: name.toUpperCase()}))} : UNAVAILABLE;
	}
};

/** A provider that lists its references, as an extension module registers one. */
const segment: LogicProviderDescriptor = {
	id: 'acme:segment',
	configKey: 'segment',
	labelKey: 'acme:segment.label',
	configLabelKey: 'acme:segment.configLabel',
	configPlaceholderKey: 'acme:segment.configPlaceholder',
	operators: ['equals'],
	isValidRef: ref => /^\w+$/.test(ref.trim()),
	refChoices: segmentChoices
};
const path = '/sites/s/contents/forms/f/fields/size';

describe('the reference list of a provider', () => {
	afterEach(() => {
		forgetRefChoices();
		vi.restoreAllMocks();
	});

	it('is asked for the edited field and read as dropdown entries', async () => {
		const post = vi.fn().mockResolvedValue({data: {acmeSegments: [{name: 'gold'}, {name: 'silver'}]}});

		const load = await fetchRefChoices(segmentChoices, path, post);

		expect(post).toHaveBeenCalledWith(segmentChoices.query, {path});
		expect(load).toEqual({available: true, choices: [{value: 'gold', label: 'GOLD'}, {value: 'silver', label: 'SILVER'}]});
	});

	it('is unavailable when the query fails, as when the module that serves it is missing', async () => {
		vi.spyOn(console, 'warn').mockImplementation(() => undefined);
		const missing = vi.fn().mockResolvedValue({errors: [{message: "Validation error: Field 'acmeSegments' is undefined"}]});

		expect(await fetchRefChoices(segmentChoices, path, missing)).toEqual(UNAVAILABLE);
	});

	it.each([
		['no choices', {available: true}, []],
		['choices that are no list', {available: true, choices: 'gold'}, []],
		['entries that are no pair of strings', {available: true, choices: [{value: 'gold', label: 'Gold'}, {value: 3, label: 'x'}, null]}, [{value: 'gold', label: 'Gold'}]]
	])('keeps only well-formed entries when the provider answers %s', async (_case, answer, choices) => {
		const source: LogicSourceRefChoicesSource = {query: 'q', read: () => answer as never};

		expect(await fetchRefChoices(source, path, vi.fn().mockResolvedValue({data: {}}))).toEqual({available: true, choices});
	});

	it('is loaded once for every rule row of the field, and asked again after a failure', async () => {
		vi.spyOn(console, 'warn').mockImplementation(() => undefined);
		const load = vi.fn()
			.mockRejectedValueOnce(new Error('network'))
			.mockResolvedValue({available: true, choices: [{value: 'gold', label: 'Gold'}]});

		expect(await acquireRefChoices(segment, path, load)).toEqual(UNAVAILABLE);
		const first = acquireRefChoices(segment, path, load);
		const second = acquireRefChoices(segment, path, load);

		expect(await first).toEqual(await second);
		expect(load).toHaveBeenCalledTimes(2);
	});

	it('is never asked for a provider that lists nothing', async () => {
		const load = vi.fn();

		expect(await acquireRefChoices(getLogicProvider('cookie')!, path, load)).toEqual(UNAVAILABLE);
		expect(load).not.toHaveBeenCalled();
	});
});

describe('refChoiceOptions', () => {
	const choices = [{value: 'gold', label: 'Gold'}, {value: 'a.b', label: 'Nested'}];

	it('offers only what the provider can resolve', () => {
		expect(refChoiceOptions(choices, '', segment.isValidRef)).toEqual([{value: 'gold', label: 'Gold'}]);
	});

	it('keeps a stored reference the list lacks, so opening the rule never rewrites it', () => {
		expect(refChoiceOptions(choices, 'legacy', segment.isValidRef).map(choice => choice.value)).toEqual(['legacy', 'gold']);
		expect(refChoiceOptions(choices, 'gold', segment.isValidRef)).toHaveLength(1);
	});
});

describe('readyView', () => {
	it('offers a dropdown of what the provider can resolve', () => {
		expect(readyView({available: true, choices: [{value: 'gold', label: 'Gold'}, {value: 'a.b', label: 'Nested'}]}, segment.isValidRef))
			.toEqual({status: 'ready', choices: [{value: 'gold', label: 'Gold'}]});
	});

	it('keeps the text input when the list offers nothing the provider can resolve, so the rule can still be written', () => {
		expect(readyView({available: true, choices: []}, segment.isValidRef)).toEqual({status: 'none'});
		expect(readyView({available: true, choices: [{value: 'a.b', label: 'Nested'}]}, segment.isValidRef)).toEqual({status: 'none'});
		expect(readyView(UNAVAILABLE, segment.isValidRef)).toEqual({status: 'none'});
	});
});
