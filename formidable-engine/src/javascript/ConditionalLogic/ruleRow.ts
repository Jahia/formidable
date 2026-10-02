import type {ConditionalLogicRule, LogicOperator, SourceFieldOption} from './ConditionalLogic.types';
import {dateBetweenIssue, isScalarValueKind, localIsoDay} from './ConditionalLogic.utils';
import type {LogicProviderDescriptor} from './providers';
import type {LogicSourceDescriptor} from './sourceDescriptors';
import {operatorNeedsValue} from './sourceDescriptors';

/**
 * What a rule row shows, decided outside the component: each of these is a few branches that
 * read better as a named function with its own test than as a ternary among the hooks, and the
 * component stays a plain assembly of them (SonarQube's cognitive complexity gate, review of #372).
 */

export type ScalarInputType = 'date' | 'number' | 'text';

export interface RuleView {
    /** A dropdown of the source's choices: a choice source, with an operator that compares against a value. */
    showValueDropdown: boolean;
    /** One or two scalar inputs: a date, number or text source, with such an operator; "is filled" needs none. */
    showScalarInput: boolean;
    scalarInputType: ScalarInputType;
}

export const describeRuleView = (
    source: SourceFieldOption | undefined,
    descriptor: LogicSourceDescriptor | undefined,
    operator: LogicOperator
): RuleView => {
    const needsValue = source !== undefined && operatorNeedsValue(operator);
    const kind = descriptor?.valueKind;
    return {
        showValueDropdown: needsValue && kind === 'choice',
        showScalarInput: needsValue && isScalarValueKind(kind),
        scalarInputType: kind === 'number' ? 'number' : (kind === 'text' ? 'text' : 'date')
    };
};

/**
 * The source type the row's first dropdown shows: the provider's id, the stored type when it names no
 * provider this module ships (a rule authored against a newer version — rendered as-is, never rewritten
 * from here), or `field`.
 */
export const ruleSourceTypeOf = (
    rule: ConditionalLogicRule,
    provider: LogicProviderDescriptor | undefined
): {isUnknownSourceType: boolean; ruleSourceType: string} => {
    const isUnknownSourceType = !provider && Boolean(rule.sourceType) && rule.sourceType !== 'field';
    return {isUnknownSourceType, ruleSourceType: provider?.id ?? (isUnknownSourceType ? rule.sourceType! : 'field')};
};

export type ProviderRefIssue = 'conditionalLogic.providerRefMissing' | 'conditionalLogic.providerRefInvalid';

/** What is wrong with the provider reference — missing, or refused by the provider; null without a provider, or when fine. */
export const providerReferenceIssue = (
    provider: LogicProviderDescriptor | undefined,
    rule: ConditionalLogicRule
): ProviderRefIssue | null => {
    if (!provider) {
        return null;
    }

    const ref = (rule[provider.configKey] ?? '').trim();
    if (ref === '') {
        return 'conditionalLogic.providerRefMissing';
    }

    return provider.isValidRef && !provider.isValidRef(ref) ? 'conditionalLogic.providerRefInvalid' : null;
};

/** The stored rules of the property this row belongs to, as the editor holds them. */
export const allRuleValuesOf = (form: {values?: Record<string, unknown>} | undefined, fieldName: string | undefined): unknown =>
    (fieldName ? form?.values?.[fieldName] : undefined);

/**
 * Whether this row is the last of its list. A rule that is no longer the last has been left behind: the
 * contributor added rules after it, so its reference counts as visited even when no blur was ever
 * observed (dropdown menus and the add button can take the focus without it ever sitting inside the row).
 */
export const isLastRuleOfList = (id: string | undefined, allRuleValues: unknown): boolean => {
    const match = /\[(\d+)\]$/.exec(id ?? '');
    return !match || !Array.isArray(allRuleValues) || Number(match[1]) >= allRuleValues.length - 1;
};

/** The reference error shows once the input was visited, or once the row was left behind — and only when there is one. */
export const providerRefErrorShown = (issue: ProviderRefIssue | null, touched: boolean, lastRule: boolean): boolean =>
    (touched || !lastRule) && issue !== null;

export interface RowMessage {
    messageKey: string | null;
    color: string;
    visibility: 'visible' | 'hidden';
}

/**
 * The reserved line under the row: the provider reference error first; else a date interval issue —
 * `inverted` (two fixed dates in the wrong order) is an authoring error, `noMatch` (a submission-day
 * side empties the interval) a warning, since the runtime ignores such a rule instead of hiding its
 * field forever.
 */
export const rowMessageOf = (args: {
    providerIssueShown: boolean;
    providerIssue: ProviderRefIssue | null;
    isFieldRule: boolean;
    view: RuleView;
    operator: LogicOperator;
    values: string[] | undefined;
}): RowMessage => {
    const betweenIssue = args.isFieldRule && args.view.showScalarInput && args.view.scalarInputType === 'date'
        && args.operator === 'between'
        ? dateBetweenIssue(args.values, localIsoDay())
        : null;
    let messageKey: string | null = null;
    if (args.providerIssueShown) {
        messageKey = args.providerIssue;
    } else if (betweenIssue === 'inverted') {
        messageKey = 'conditionalLogic.betweenInverted';
    } else if (betweenIssue === 'noMatch') {
        messageKey = 'conditionalLogic.betweenNoMatch';
    }

    return {
        messageKey,
        color: !args.providerIssueShown && betweenIssue === 'noMatch' ? 'var(--color-warning)' : 'var(--color-danger)',
        visibility: messageKey ? 'visible' : 'hidden'
    };
};

/** A translated message, or null without a key. */
export const translated = (t: (key: string) => string, key: string | null): string | null => (key ? t(key) : null);
