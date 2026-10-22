import {describe, expect, it} from 'vitest';
import {followsAnotherRedirect, type RedirectActionNodeLike, redirectTargetDetail} from './redirectAction.server';

interface Options {
	id: string;
	type?: string;
	properties?: Record<string, string>;
	page?: string | Error;
}

/** An action node of a list: its type, its properties, and for a page target the picked page's title. */
const listOf = (...options: Options[]): RedirectActionNodeLike[] => {
	const nodes: RedirectActionNodeLike[] = options.map(option => ({
		getIdentifier: () => option.id,
		isNodeType: name => name === (option.type ?? 'fmdb:redirectAction'),
		getParent: () => ({getNodes: () => nodes}),
		hasProperty: name => name in (option.properties ?? {}),
		getProperty: name => ({
			getString: () => (option.properties ?? {})[name],
			getNode: () => {
				if (option.page instanceof Error) throw option.page;
				return {getDisplayableName: () => option.page as string};
			}
		})
	}));
	return nodes;
};

describe('followsAnotherRedirect', () => {
	it('is false for the first redirect of the list, whatever comes before it', () => {
		const [save, redirect] = listOf({id: 'save', type: 'fmdb:save2jcrAction'}, {id: 'r1'});
		expect(followsAnotherRedirect(save)).toBe(false);
		expect(followsAnotherRedirect(redirect)).toBe(false);
	});

	it('is true for every redirect after the first one', () => {
		const [, , second, , third] = listOf(
			{id: 'r1'}, {id: 'mail', type: 'fmdb:emailNotificationAction'}, {id: 'r2'},
			{id: 'save', type: 'fmdb:save2jcrAction'}, {id: 'r3'});
		expect(followsAnotherRedirect(second)).toBe(true);
		expect(followsAnotherRedirect(third)).toBe(true);
	});
});

describe('redirectTargetDetail', () => {
	it('shows the URL of a URL target, trimmed', () => {
		const [action] = listOf({id: 'r', properties: {targetType: 'url', url: ' https://www.example.com/thanks '}});
		expect(redirectTargetDetail(action)).toBe('https://www.example.com/thanks');
	});

	it('shows the title of the picked page, the default target type', () => {
		const [explicit] = listOf({id: 'r', properties: {targetType: 'page', page: 'uuid'}, page: 'Thank you'});
		const [byDefault] = listOf({id: 'r', properties: {page: 'uuid'}, page: 'Thank you'});
		expect(redirectTargetDetail(explicit)).toBe('Thank you');
		expect(redirectTargetDetail(byDefault)).toBe('Thank you');
	});

	it('shows nothing for a target not set, or a page that no longer resolves', () => {
		expect(redirectTargetDetail(listOf({id: 'r', properties: {targetType: 'url'}})[0])).toBeUndefined();
		expect(redirectTargetDetail(listOf({id: 'r', properties: {targetType: 'url', url: '  '}})[0])).toBeUndefined();
		expect(redirectTargetDetail(listOf({id: 'r', properties: {targetType: 'page'}})[0])).toBeUndefined();
		expect(redirectTargetDetail(listOf({id: 'r', properties: {page: 'gone'}, page: new Error('ItemNotFound')})[0])).toBeUndefined();
	});
});
