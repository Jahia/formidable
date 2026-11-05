import {afterEach, describe, expect, it, vi} from 'vitest';
import {type FieldActionNodeLike, fieldActionsOf, sensitiveValueConsumersOf} from './fieldActions.server';

/** A node as the wrapper reads it: its type and mixins, its children, and for an action its trigger property. */
const nodeOf = (options: {type?: string; mixins?: string[]; children?: Record<string, FieldActionNodeLike>; trigger?: string}): FieldActionNodeLike => {
	const mixins = options.mixins ?? [];
	const children = options.children ?? {};
	return {
		isNodeType: name => mixins.includes(name),
		hasNode: name => name in children,
		getNode: name => children[name],
		getNodes: () => Object.values(children),
		// The Java string, as the host hands it
		getPrimaryNodeTypeName: () => ({toString: () => options.type ?? 'test:fieldAction'}) as unknown as string,
		hasProperty: name => name === 'trigger' && options.trigger !== undefined,
		// The Java string, as the host hands it: an object whose String() is the value
		getProperty: () => ({getString: () => ({toString: () => options.trigger}) as unknown as string})
	};
};

const action = (trigger?: string, type?: string) => nodeOf({type, mixins: ['fmdbmix:fieldAction'], trigger});
const listOf = (actions: FieldActionNodeLike[]) =>
	nodeOf({children: Object.fromEntries(actions.map((node, index) => [`action-${index}`, node]))});
const fieldWith = (...actions: FieldActionNodeLike[]) => nodeOf({
	mixins: ['fmdbmix:fieldActions'],
	children: {actions: listOf(actions)}
});
/** A password: a field carrying fmdbmix:sensitiveField, whatever its editor holds. */
const sensitiveFieldWith = (...actions: FieldActionNodeLike[]) => nodeOf({
	mixins: ['fmdbmix:fieldActions', 'fmdbmix:sensitiveField'],
	children: {actions: listOf(actions)}
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

describe('fieldActionsOf on a sensitive field', () => {
	const policy = 'test:passwordPolicy';
	const emailCheck = 'test:emailDomainCheck';
	const consumers = (...types: string[]) => (type: string) => types.includes(type);

	it('never makes the browser ask when no field action consumes sensitive values, whatever the editor holds', () => {
		// the value of a password goes to the pre-check endpoint only for an action whose Java type opted in
		expect(fieldActionsOf(sensitiveFieldWith(action('blur', emailCheck)), consumers())).toEqual({hasList: true, trigger: undefined});
	});

	it('asks only about the field actions that consume sensitive values', () => {
		const consumes = vi.fn(consumers(policy));

		expect(fieldActionsOf(sensitiveFieldWith(action('blur', emailCheck), action('submit', policy)), consumes))
			.toEqual({hasList: true, trigger: 'submit'});
		expect(fieldActionsOf(sensitiveFieldWith(action('submit', emailCheck), action('blur', policy)), consumes))
			.toEqual({hasList: true, trigger: 'blur'});
		expect(consumes).toHaveBeenCalledWith(emailCheck);
		expect(consumes).toHaveBeenCalledWith(policy);
	});

	it('keeps none when the caller gives no answer: a sensitive field fails closed', () => {
		expect(fieldActionsOf(sensitiveFieldWith(action('blur', policy)))).toEqual({hasList: true, trigger: undefined});
	});

	it('asks nothing for a field that is not sensitive', () => {
		const consumes = vi.fn(consumers());

		expect(fieldActionsOf(fieldWith(action('blur', emailCheck)), consumes)).toEqual({hasList: true, trigger: 'blur'});
		expect(consumes).not.toHaveBeenCalled();
	});
});

describe('sensitiveValueConsumersOf', () => {
	afterEach(() => {
		vi.restoreAllMocks();
	});

	it('answers what the engine\'s FieldActionTypes service says for the node type', () => {
		const consumesSensitiveValues = vi.fn((type: string) => type === 'test:passwordPolicy');
		const getService = vi.fn(() => ({consumesSensitiveValues}));
		const consumes = sensitiveValueConsumersOf(getService);

		expect(consumes('test:passwordPolicy')).toBe(true);
		expect(consumes('test:emailDomainCheck')).toBe(false);
		expect(getService).toHaveBeenCalledWith('org.jahia.modules.formidable.engine.api.FieldActionTypes');
	});

	it('looks the service up once, at the first question, however many field actions are asked about', () => {
		const getService = vi.fn(() => ({consumesSensitiveValues: () => true}));
		const consumes = sensitiveValueConsumersOf(getService);
		expect(getService).not.toHaveBeenCalled();

		expect(fieldActionsOf(sensitiveFieldWith(action('submit', 'test:a'), action('submit', 'test:b'), action('submit', 'test:c')), consumes))
			.toEqual({hasList: true, trigger: 'submit'});
		expect(getService).toHaveBeenCalledOnce();
	});

	it('never looks the service up for a field that is not sensitive', () => {
		const getService = vi.fn(() => ({consumesSensitiveValues: () => true}));

		fieldActionsOf(fieldWith(action('blur')), sensitiveValueConsumersOf(getService));

		expect(getService).not.toHaveBeenCalled();
	});

	it('fails closed without the service, and says so once', () => {
		const warn = vi.spyOn(console, 'warn').mockImplementation(() => undefined);
		const getService = vi.fn(() => null);
		const consumes = sensitiveValueConsumersOf(getService);

		expect(consumes('test:passwordPolicy')).toBe(false);
		expect(consumes('test:otherPolicy')).toBe(false);
		expect(getService).toHaveBeenCalledOnce();
		expect(warn).toHaveBeenCalledOnce();
	});

	it('fails closed when the service throws, or when reaching it does', () => {
		vi.spyOn(console, 'error').mockImplementation(() => undefined);
		const throwing = {consumesSensitiveValues: () => {
			throw new Error('engine stopping');
		}};

		expect(sensitiveValueConsumersOf(() => throwing)('test:passwordPolicy')).toBe(false);
		const unreachable = vi.fn(() => {
			throw new Error('no such service');
		});
		const consumes = sensitiveValueConsumersOf(unreachable);
		expect(consumes('test:passwordPolicy')).toBe(false);
		expect(consumes('test:otherPolicy')).toBe(false);
		// a lookup that threw is not tried again for the same field
		expect(unreachable).toHaveBeenCalledOnce();
	});
});
