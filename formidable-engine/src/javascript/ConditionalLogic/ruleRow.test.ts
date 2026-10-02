import {describe, expect, it} from 'vitest';
import {
	describeRuleView,
	isLastRuleOfList,
	providerReferenceIssue,
	providerRefErrorShown,
	rowMessageOf,
	ruleSourceTypeOf,
	translated
} from './ruleRow';
import {getSourceDescriptor} from './sourceDescriptors';
import {getLogicProvider} from './providers';
import type {ConditionalLogicRule, SourceFieldOption} from './ConditionalLogic.types';

const source = (type: string, valueKind: SourceFieldOption['valueKind']): SourceFieldOption => ({
	id: 'u-1', name: 'country', path: '/f/fields/country', label: 'Country', type, valueKind, choiceValues: []
});
const rule = (patch: Partial<ConditionalLogicRule> = {}): ConditionalLogicRule => ({operator: 'equals', ...patch} as ConditionalLogicRule);

describe('describeRuleView', () => {
	it('shows the choices of a choice source when the operator compares against a value', () => {
		const choice = source('fmdb:select', 'choice');
		expect(describeRuleView(choice, getSourceDescriptor('fmdb:select', 'choice'), 'in'))
			.toEqual({showValueDropdown: true, showScalarInput: false, scalarInputType: 'date'});
	});

	it('shows a typed input for a scalar source, and nothing for an operator that needs no value', () => {
		const number = source('fmdb:inputNumber', 'number');
		const descriptor = getSourceDescriptor('fmdb:inputNumber', 'number');
		expect(describeRuleView(number, descriptor, 'gt').showScalarInput).toBe(true);
		expect(describeRuleView(number, descriptor, 'gt').scalarInputType).toBe('number');
		expect(describeRuleView(number, descriptor, 'isEmpty').showScalarInput).toBe(false);
		expect(describeRuleView(undefined, undefined, 'equals')).toEqual({showValueDropdown: false, showScalarInput: false, scalarInputType: 'date'});
	});
});

describe('ruleSourceTypeOf', () => {
	it('names the provider, keeps an unknown type as stored, and defaults to field', () => {
		const urlParam = getLogicProvider('urlParam');
		expect(ruleSourceTypeOf(rule({sourceType: 'urlParam'}), urlParam)).toEqual({isUnknownSourceType: false, ruleSourceType: 'urlParam'});
		expect(ruleSourceTypeOf(rule({sourceType: 'fromTheFuture' as never}), undefined)).toEqual({isUnknownSourceType: true, ruleSourceType: 'fromTheFuture'});
		expect(ruleSourceTypeOf(rule(), undefined)).toEqual({isUnknownSourceType: false, ruleSourceType: 'field'});
	});
});

describe('providerReferenceIssue', () => {
	const urlParam = getLogicProvider('urlParam')!;

	it('is missing without a reference, fine with one, and null without a provider', () => {
		expect(providerReferenceIssue(urlParam, rule({[urlParam.configKey]: '  '} as never))).toBe('conditionalLogic.providerRefMissing');
		expect(providerReferenceIssue(urlParam, rule({[urlParam.configKey]: 'campaign'} as never))).toBeNull();
		expect(providerReferenceIssue(undefined, rule())).toBeNull();
	});

	it('refuses what the provider refuses', () => {
		const strict = {...urlParam, isValidRef: (ref: string) => ref === 'ok'};
		expect(providerReferenceIssue(strict, rule({[urlParam.configKey]: 'ok'} as never))).toBeNull();
		expect(providerReferenceIssue(strict, rule({[urlParam.configKey]: 'nope'} as never))).toBe('conditionalLogic.providerRefInvalid');
	});
});

describe('isLastRuleOfList / providerRefErrorShown', () => {
	it('reads the row index off the field id and compares it with the list', () => {
		expect(isLastRuleOfList('logics[1]', ['a', 'b'])).toBe(true);
		expect(isLastRuleOfList('logics[0]', ['a', 'b'])).toBe(false);
		// No index, or no list: nothing says the row was left behind
		expect(isLastRuleOfList('logics', ['a', 'b'])).toBe(true);
		expect(isLastRuleOfList('logics[0]', undefined)).toBe(true);
	});

	it('shows the error once visited or left behind, never without an issue', () => {
		expect(providerRefErrorShown('conditionalLogic.providerRefMissing', true, true)).toBe(true);
		expect(providerRefErrorShown('conditionalLogic.providerRefMissing', false, false)).toBe(true);
		expect(providerRefErrorShown('conditionalLogic.providerRefMissing', false, true)).toBe(false);
		expect(providerRefErrorShown(null, true, false)).toBe(false);
	});
});

describe('rowMessageOf', () => {
	const dateView = {showValueDropdown: false, showScalarInput: true, scalarInputType: 'date' as const};

	it('puts the provider reference error first, in the error colour', () => {
		const row = rowMessageOf({providerIssueShown: true, providerIssue: 'conditionalLogic.providerRefMissing', isFieldRule: false, view: dateView, operator: 'between', values: ['2026-02-01', '2026-01-01']});
		expect(row).toEqual({messageKey: 'conditionalLogic.providerRefMissing', color: 'var(--color-danger)', visibility: 'visible'});
	});

	it('flags an inverted date interval as an error and an empty one as a warning', () => {
		const inverted = rowMessageOf({providerIssueShown: false, providerIssue: null, isFieldRule: true, view: dateView, operator: 'between', values: ['2026-02-01', '2026-01-01']});
		expect(inverted.messageKey).toBe('conditionalLogic.betweenInverted');
		expect(inverted.color).toBe('var(--color-danger)');
		// A fine interval, or an operator that is not "between": nothing on the line
		expect(rowMessageOf({providerIssueShown: false, providerIssue: null, isFieldRule: true, view: dateView, operator: 'between', values: ['2026-01-01', '2026-02-01']}).visibility).toBe('hidden');
		expect(rowMessageOf({providerIssueShown: false, providerIssue: null, isFieldRule: true, view: dateView, operator: 'equals', values: ['2026-02-01', '2026-01-01']}).messageKey).toBeNull();
	});

	it('translates a key and leaves null alone', () => {
		expect(translated(key => `[${key}]`, 'conditionalLogic.loadError')).toBe('[conditionalLogic.loadError]');
		expect(translated(key => `[${key}]`, null)).toBeNull();
	});
});
