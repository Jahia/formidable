import {describe, expect, it} from 'vitest';
import {type FieldActionNodeLike, fieldActionsOf} from './fieldActions.server';

/** A node as the wrapper reads it: its mixins, its children, and for an action its trigger property. */
const nodeOf = (options: {mixins?: string[]; children?: Record<string, FieldActionNodeLike>; trigger?: string}): FieldActionNodeLike => {
	const mixins = options.mixins ?? [];
	const children = options.children ?? {};
	return {
		isNodeType: name => mixins.includes(name),
		hasNode: name => name in children,
		getNode: name => children[name],
		getNodes: () => Object.values(children),
		hasProperty: name => name === 'trigger' && options.trigger !== undefined,
		// The Java string, as the host hands it: an object whose String() is the value
		getProperty: () => ({getString: () => ({toString: () => options.trigger}) as unknown as string})
	};
};

const action = (trigger?: string) => nodeOf({mixins: ['fmdbmix:fieldAction'], trigger});
const fieldWith = (...actions: FieldActionNodeLike[]) => nodeOf({
	mixins: ['fmdbmix:fieldActions'],
	children: {
		actions: nodeOf({children: Object.fromEntries(actions.map((node, index) => [`action-${index}`, node]))})
	}
});

describe('fieldActionsOf', () => {
	it('sees nothing on a field without the switch, or with the switch but no list', () => {
		expect(fieldActionsOf(nodeOf({}))).toEqual({hasList: false});
		expect(fieldActionsOf(nodeOf({mixins: ['fmdbmix:fieldActions']}))).toEqual({hasList: false});
	});

	it('sees the list but no trigger when it is empty: the zone renders, the client is not told', () => {
		expect(fieldActionsOf(fieldWith())).toEqual({hasList: true, trigger: undefined});
	});

	it('asks at blur when any action is checked at blur — the CND default when the property is absent', () => {
		expect(fieldActionsOf(fieldWith(action('blur')))).toEqual({hasList: true, trigger: 'blur'});
		expect(fieldActionsOf(fieldWith(action()))).toEqual({hasList: true, trigger: 'blur'});
		expect(fieldActionsOf(fieldWith(action('submit'), action('blur')))).toEqual({hasList: true, trigger: 'blur'});
	});

	it('asks at submission only when every action waits for it', () => {
		expect(fieldActionsOf(fieldWith(action('submit'), action('submit')))).toEqual({hasList: true, trigger: 'submit'});
	});

	it('ignores a child of the list that is not a field action', () => {
		const stray = nodeOf({mixins: ['jmix:list'], trigger: 'blur'});
		expect(fieldActionsOf(fieldWith(stray))).toEqual({hasList: true, trigger: undefined});
		expect(fieldActionsOf(fieldWith(stray, action('submit')))).toEqual({hasList: true, trigger: 'submit'});
	});
});
