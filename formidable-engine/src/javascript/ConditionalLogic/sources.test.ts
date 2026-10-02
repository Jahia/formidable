import {afterEach, describe, expect, it, vi} from 'vitest';
import {acquireSources, fetchSources, forgetSources, RELEASE_GRACE_MS, releaseSources} from './sources';
import type {SourcesInput, SourcesLoad} from './sources';

const input: SourcesInput = {path: '/sites/s/contents/forms/f/fields/city', workspace: 'EDIT', language: 'en', defaultLanguage: 'en'};

const field = (name: string, type: string, flags: Record<string, boolean> = {}) => ({
	uuid: `u-${name}`,
	workspace: 'EDIT',
	name,
	path: `/sites/s/contents/forms/f/fields/${name}`,
	displayName: name,
	primaryNodeType: {name: type},
	properties: [{name: 'fieldKey', value: `k-${name}`}],
	defaultProperties: [],
	...flags
});

const answer = (nodeByPath: unknown) => ({data: {jcr: {nodeByPath}}});

describe('fetchSources', () => {
	it('reads the field, its rule references and the sources before it in one request', async () => {
		const post = vi.fn().mockResolvedValue(answer({
			...field('city', 'fmdb:inputText', {isTextField: true}),
			descendant: {children: {nodes: [
				{uuid: 'l1', workspace: 'EDIT', name: 'rule-1', path: '/x/logicsSrc/rule-1', property: {refNode: {name: 'country', uuid: 'u-country'}}}
			]}},
			ancestors: [{
				uuid: 'u-form', workspace: 'EDIT', name: 'f', path: '/sites/s/contents/forms/f', primaryNodeType: {name: 'fmdb:form'},
				descendants: {nodes: [
					field('country', 'fmdb:select', {isChoiceField: true}),
					field('city', 'fmdb:inputText', {isTextField: true}),
					// After the field: never a source of it
					field('zip', 'fmdb:inputText', {isTextField: true})
				]}
			}]
		}));

		const load = await fetchSources(input, post);

		expect(post).toHaveBeenCalledTimes(1);
		expect(post.mock.calls[0][1]).toEqual(input);
		expect(load.sources.map(source => source.id)).toEqual(['u-country']);
		expect(load.logicIdToSource.get('rule-1')).toEqual({name: 'country', uuid: 'u-country'});
	});

	it('fails when no form stands above the field', async () => {
		const post = vi.fn().mockResolvedValue(answer({...field('orphan', 'fmdb:inputText'), ancestors: []}));

		await expect(fetchSources(input, post)).rejects.toThrow('no form above');
	});

	it('surfaces a GraphQL error instead of an empty list', async () => {
		const post = vi.fn().mockResolvedValue({errors: [{message: 'boom'}]});

		await expect(fetchSources(input, post)).rejects.toThrow('boom');
	});
});

describe('acquireSources / releaseSources', () => {
	const result: SourcesLoad = {sources: [], logicIdToSource: new Map()};

	afterEach(() => {
		forgetSources();
		vi.useRealTimers();
	});

	it('shares one load between the rule rows of a field', async () => {
		const load = vi.fn().mockResolvedValue(result);

		const first = acquireSources(input, load);
		const second = acquireSources(input, load);
		const third = acquireSources(input, load);

		expect(load).toHaveBeenCalledTimes(1);
		expect(await first).toBe(result);
		expect(await second).toBe(result);
		expect(await third).toBe(result);
	});

	it('keeps the load through the remount storm of an added rule, and forgets it once the editor is closed', () => {
		vi.useFakeTimers();
		const load = vi.fn().mockResolvedValue(result);

		// The editor remounts every row when a rule is added: released, then acquired again at once.
		acquireSources(input, load);
		releaseSources(input);
		acquireSources(input, load);
		vi.advanceTimersByTime(RELEASE_GRACE_MS * 2);
		expect(load).toHaveBeenCalledTimes(1);

		// The editor closes: the last row releases, nobody comes back within the grace.
		releaseSources(input);
		vi.advanceTimersByTime(RELEASE_GRACE_MS + 1);
		acquireSources(input, load);
		expect(load).toHaveBeenCalledTimes(2);
	});

	it('does not keep a failed load', async () => {
		const load = vi.fn().mockRejectedValueOnce(new Error('down')).mockResolvedValue(result);

		await expect(acquireSources(input, load)).rejects.toThrow('down');
		await Promise.resolve();
		await expect(acquireSources(input, load)).resolves.toBe(result);
		expect(load).toHaveBeenCalledTimes(2);
	});
});
