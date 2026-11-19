import {afterEach, describe, expect, it, vi} from 'vitest';
import {type ActionNodeLike, isPresumedToWrite} from './readOnlyActions.server';

/** A node as the check reads it: its mixins and its children. */
const nodeOf = (options: {mixins?: string[]; children?: Record<string, ActionNodeLike>} = {}): ActionNodeLike => {
	const mixins = options.mixins ?? [];
	const children = options.children ?? {};
	return {
		isNodeType: name => mixins.includes(name),
		hasNode: name => name in children,
		getNode: name => children[name],
		getNodes: () => Object.values(children),
		getPath: () => '/sites/mysite/contents/form'
	};
};

const mail = () => nodeOf({mixins: ['fmdbmix:formAction', 'fmdbmix:readOnlyCompatibleAction']});
const store = () => nodeOf({mixins: ['fmdbmix:formAction']});
const listOf = (...actions: ActionNodeLike[]) =>
	nodeOf({children: Object.fromEntries(actions.map((node, index) => [`action-${index}`, node]))});

describe('isPresumedToWrite', () => {
	afterEach(() => {
		vi.restoreAllMocks();
	});

	it('is false for a form whose actions all declare that they never write', () => {
		expect(isPresumedToWrite(nodeOf({children: {actions: listOf(mail())}}))).toBe(false);
	});

	it('is true when an action on submission is presumed to write', () => {
		expect(isPresumedToWrite(nodeOf({children: {actions: listOf(mail(), store())}}))).toBe(true);
	});

	it('ignores a second list that a form with the switch off still carries, as the pipeline does', () => {
		const form = nodeOf({children: {actions: listOf(mail()), deferredActions: listOf(store())}});

		expect(isPresumedToWrite(form)).toBe(false);
	});

	it('is true for a form with the switch on, whatever its actions, as the pipeline decides', () => {
		// The hold stores a submission with an upload, and the view cannot know whether a visitor attaches one.
		const form = nodeOf({mixins: ['fmdbmix:quarantine'], children: {actions: listOf(mail()), deferredActions: listOf(mail())}});

		expect(isPresumedToWrite(form)).toBe(true);
	});

	it('is true for a form with the switch on and no list', () => {
		expect(isPresumedToWrite(nodeOf({mixins: ['fmdbmix:quarantine']}))).toBe(true);
	});

	it('is false for a form with no list', () => {
		expect(isPresumedToWrite(nodeOf())).toBe(false);
	});

	it('presumes a write when a list cannot be read', () => {
		vi.spyOn(console, 'error').mockImplementation(() => undefined);
		const form = nodeOf({children: {actions: listOf(mail())}});
		form.getNode = () => {
			throw new Error('the repository is stopping');
		};

		expect(isPresumedToWrite(form)).toBe(true);
	});
});
