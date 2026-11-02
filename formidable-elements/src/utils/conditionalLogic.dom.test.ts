// @vitest-environment jsdom
import {afterEach, describe, expect, it} from 'vitest';
import {applyConditionalLogicVisibility, buildLogicStateHeader} from './conditionalLogic';
import type {HostedLogicProvider} from './logicProviders';

/** A form with one field shown only when the visitor's profile property matches the rule. */
const mount = (operator: string, value?: string) => {
	const rule = JSON.stringify({sourceType: 'jexperience:profile', property: 'country', operator, value});
	document.body.innerHTML = `<form><div data-fmdb-node-name="companySize" data-fmdb-logics='${JSON.stringify([rule])}'></div></form>`;
	const form = document.querySelector('form')!;
	const field = form.querySelector<HTMLElement>('[data-fmdb-node-name="companySize"]')!;
	applyConditionalLogicVisibility(form);
	return {form, hidden: () => field.dataset.fmdbLogicHidden === 'true'};
};

/** The object formidable-jxp.js puts on the page, for a visitor whose profile holds `profile`. */
const tracker = (profile: Record<string, string>, ready = true): HostedLogicProvider => ({
	available: () => ready,
	read: property => profile[property]
});

const declaration = (form: HTMLFormElement) => {
	const header = buildLogicStateHeader(form);
	return header === null ? null : JSON.parse(atob(header));
};

afterEach(() => {
	globalThis.formidableJxp = undefined;
});

describe('a jExperience profile property as a conditional logic source', () => {
	it('shows the field when the property has the expected value', () => {
		globalThis.formidableJxp = {logicProvider: tracker({country: 'FR'})};
		expect(mount('equals', 'FR').hidden()).toBe(false);
	});

	it('hides the field when the property has another value', () => {
		globalThis.formidableJxp = {logicProvider: tracker({country: 'DE'})};
		expect(mount('equals', 'FR').hidden()).toBe(true);
	});

	it('reads a property the profile does not hold as absent', () => {
		globalThis.formidableJxp = {logicProvider: tracker({})};
		expect(mount('notExists').hidden()).toBe(false);
	});

	it.each(['equals', 'notEquals', 'contains', 'exists', 'notExists'])(
		'keeps the field hidden with %s when the tracker is blocked or not loaded',
		operator => {
			globalThis.formidableJxp = {logicProvider: tracker({country: 'FR'}, false)};
			expect(mount(operator, 'XX').hidden()).toBe(true);
		}
	);

	it('keeps the field hidden when the jExperience script is not on the page', () => {
		expect(mount('notExists').hidden()).toBe(true);
	});

	it('keeps the field hidden when the jExperience script throws', () => {
		globalThis.formidableJxp = {logicProvider: {available: () => true, read: () => {
			throw new Error('broken');
		}}};
		expect(mount('exists').hidden()).toBe(true);
	});

	it('declares the value the browser read, and an absent property as null', () => {
		globalThis.formidableJxp = {logicProvider: tracker({country: 'FR'})};
		expect(declaration(mount('equals', 'FR').form)).toEqual({v: 1, providers: {'jexperience:profile': {country: 'FR'}}});

		globalThis.formidableJxp = {logicProvider: tracker({})};
		expect(declaration(mount('equals', 'FR').form)).toEqual({v: 1, providers: {'jexperience:profile': {country: null}}});
	});

	it('declares nothing for an unavailable source, so the server fails its rules closed', () => {
		globalThis.formidableJxp = {logicProvider: tracker({country: 'FR'}, false)};
		expect(declaration(mount('notExists').form)).toBeNull();
	});
});
