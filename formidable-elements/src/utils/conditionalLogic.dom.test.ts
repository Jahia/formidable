// @vitest-environment jsdom
import {afterEach, describe, expect, it, vi} from 'vitest';
import type {PageLogicSource} from '@jahia/formidable-library/logic-sources';
import {applyConditionalLogicVisibility, buildLogicStateHeader} from './conditionalLogic';
import {FORM_LOGIC_INVALIDATE_EVENT, forgetRegisteredLogicProviders, getLogicProvider, onLogicProviderRegistered} from './logicProviders';

/** A form with one field shown only when the registered source's `tier` matches the rule. */
const mount = (operator: string, value?: string) => {
	const rule = JSON.stringify({sourceType: 'acme:segment', segment: 'tier', operator, value});
	document.body.innerHTML = `<form><div data-fmdb-node-name="companySize" data-fmdb-logics='${JSON.stringify([rule])}'></div></form>`;
	const form = document.querySelector('form')!;
	const field = form.querySelector<HTMLElement>('[data-fmdb-node-name="companySize"]')!;
	applyConditionalLogicVisibility(form);
	return {form, hidden: () => field.dataset.fmdbLogicHidden === 'true'};
};

/** The page half another module's script pushes, for a visitor whose segments are `state`. */
const segmentSource = (state: Record<string, string>, ready = true): PageLogicSource => ({
	id: 'acme:segment',
	configKey: 'segment',
	available: () => ready,
	read: ref => state[ref]
});

/** What another module's script does, whether Formidable's script ran before it or not. */
const push = (source: PageLogicSource) => {
	globalThis.formidableLogicSources ??= [];
	globalThis.formidableLogicSources.push(source);
};

const declaration = (form: HTMLFormElement) => {
	const header = buildLogicStateHeader(form);
	return header === null ? null : JSON.parse(atob(header));
};

afterEach(() => {
	forgetRegisteredLogicProviders();
	vi.restoreAllMocks();
});

describe('a source another module registers on the page', () => {
	it('shows the field when the source has the expected value', () => {
		push(segmentSource({tier: 'gold'}));
		expect(mount('equals', 'gold').hidden()).toBe(false);
	});

	it('hides the field when the source has another value', () => {
		push(segmentSource({tier: 'silver'}));
		expect(mount('equals', 'gold').hidden()).toBe(true);
	});

	it('reads a reference the source does not hold as absent', () => {
		push(segmentSource({}));
		expect(mount('notExists').hidden()).toBe(false);
	});

	it.each(['equals', 'notEquals', 'contains', 'exists', 'notExists'])(
		'keeps the field hidden with %s while the source is unavailable',
		operator => {
			push(segmentSource({tier: 'gold'}, false));
			expect(mount(operator, 'XX').hidden()).toBe(true);
		}
	);

	it.each(['equals', 'notEquals', 'exists', 'notExists'])(
		'keeps the field hidden with %s when no module registered the source',
		operator => {
			const {form, hidden} = mount(operator, 'gold');
			expect(hidden()).toBe(true);
			expect(form.querySelector<HTMLElement>('[data-fmdb-node-name]')!.dataset.fmdbLogicUnresolved).toBe('source:acme:segment');
			// nothing declared: the server hides the field too
			expect(declaration(form)).toBeNull();
		}
	);

	it('keeps the reference of a rule parsed before the source registered, and evaluates again on registration', async () => {
		const {form, hidden} = mount('equals', 'gold');
		expect(hidden()).toBe(true);
		const invalidated = vi.fn();
		document.addEventListener(FORM_LOGIC_INVALIDATE_EVENT, invalidated);

		push(segmentSource({tier: 'gold'}));
		await Promise.resolve();

		expect(invalidated).toHaveBeenCalledTimes(1);
		applyConditionalLogicVisibility(form);
		expect(hidden()).toBe(false);
		document.removeEventListener(FORM_LOGIC_INVALIDATE_EVENT, invalidated);
	});

	it('keeps the field hidden when the source throws, whatever the operator', () => {
		push({id: 'acme:segment', configKey: 'segment', available: () => true, read: () => {
			throw new Error('broken');
		}});
		expect(mount('exists').hidden()).toBe(true);
		// read as "absent", a broken script would show this one
		expect(mount('notExists').hidden()).toBe(true);
	});

	it('keeps the field hidden when the availability check throws', () => {
		push({id: 'acme:segment', configKey: 'segment', available: () => {
			throw new Error('broken');
		}, read: () => 'gold'});
		expect(mount('notExists').hidden()).toBe(true);
	});

	it('declares nothing for a reference whose read throws, so the server fails its rule closed', () => {
		push({id: 'acme:segment', configKey: 'segment', available: () => true, read: () => {
			throw new Error('broken');
		}});
		expect(declaration(mount('notExists').form)).toEqual({v: 1, providers: {'acme:segment': {}}});
	});

	it('declares the value the browser read, and an absent reference as null', () => {
		const state: Record<string, string> = {tier: 'gold'};
		push(segmentSource(state));
		expect(declaration(mount('equals', 'gold').form)).toEqual({v: 1, providers: {'acme:segment': {tier: 'gold'}}});

		delete state.tier;
		expect(declaration(mount('equals', 'gold').form)).toEqual({v: 1, providers: {'acme:segment': {tier: null}}});
	});

	it('declares nothing for an unavailable source, so the server fails its rules closed', () => {
		push(segmentSource({tier: 'gold'}, false));
		expect(declaration(mount('notExists').form)).toBeNull();
	});
});

describe('the registration of a page source', () => {
	afterEach(() => forgetRegisteredLogicProviders());

	it('asks for one evaluation per batch of registrations, once the registering code has run', async () => {
		push(segmentSource({}));
		push({id: 'acme:tier', configKey: 'tier', read: () => undefined});
		const invalidated = vi.fn();
		const registered = vi.fn();
		document.addEventListener(FORM_LOGIC_INVALIDATE_EVENT, invalidated);
		onLogicProviderRegistered(registered);

		// what was pushed before the form came up registers during its first evaluation
		mount('exists');
		expect(invalidated).not.toHaveBeenCalled();
		await Promise.resolve();

		expect(invalidated).toHaveBeenCalledTimes(1);
		expect(registered).toHaveBeenCalledTimes(1);
		document.removeEventListener(FORM_LOGIC_INVALIDATE_EVENT, invalidated);
	});

	it('logs the first throw of a source, and only the first', () => {
		const warn = vi.spyOn(console, 'warn').mockImplementation(() => undefined);
		push({id: 'acme:segment', configKey: 'segment', available: () => true, read: () => {
			throw new Error('broken');
		}});
		mount('exists');
		mount('exists');

		expect(warn.mock.calls.filter(([message]) => String(message).includes('"acme:segment" threw'))).toHaveLength(1);
	});

	it('keeps a throw of the source\'s unsubscribe function from reaching the form', () => {
		vi.spyOn(console, 'warn').mockImplementation(() => undefined);
		push({id: 'acme:segment', configKey: 'segment', read: () => undefined, subscribe: () => () => {
			throw new Error('broken');
		}});
		mount('exists');

		const unsubscribe = getLogicProvider('acme:segment')!.subscribe!(['tier'], () => undefined);
		expect(() => unsubscribe()).not.toThrow();
	});

	it.each([
		['an id without its module', {id: 'segment', configKey: 'segment', read: () => undefined}],
		['a shared rule key as config key', {id: 'acme:segment', configKey: 'value', read: () => undefined}],
		['the config key of a built-in', {id: 'acme:segment', configKey: 'cookie', read: () => undefined}],
		['no read function', {id: 'acme:segment', configKey: 'segment'} as unknown as PageLogicSource]
	])('ignores %s', (_case, source) => {
		vi.spyOn(console, 'warn').mockImplementation(() => undefined);
		mount('exists');
		push(source);

		expect(getLogicProvider(source.id)).toBeUndefined();
	});

	it('keeps the first registration of an id', () => {
		mount('exists');
		push(segmentSource({tier: 'gold'}));
		push(segmentSource({tier: 'silver'}));

		expect(getLogicProvider('acme:segment')!.read('tier')).toBe('gold');
	});

	it('never lets a source take a built-in id', () => {
		vi.spyOn(console, 'warn').mockImplementation(() => undefined);
		mount('exists');
		push({id: 'cookie', configKey: 'crumb', read: () => 'x'});

		expect(getLogicProvider('cookie')!.configKey).toBe('cookie');
	});
});
