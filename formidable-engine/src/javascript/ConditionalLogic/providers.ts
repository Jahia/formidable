import type {LogicOperator, ProviderConfigKey, RuleSourceType} from './ConditionalLogic.types';

/**
 * Rule sources that are not another form field: state outside the form, read in the
 * browser only. Each designates one thing whose value is a single optional string, so they
 * all share the same operator set and the same editing shape — a name to type in.
 *
 * A provider is therefore fully declarative here: no per-provider React, and no
 * per-provider code in the runtime evaluator either (see
 * formidable-elements/src/utils/logicProviders.ts, which must declare the same ids and
 * config keys — nothing checks that today). This list is internal to the engine; it is
 * deliberately not a public extension point yet.
 */
export interface LogicProviderDescriptor {
    id: Exclude<RuleSourceType, 'field'>;
    /** The rule key holding what this provider designates. */
    configKey: ProviderConfigKey;
    /** i18n keys, under conditionalLogic.providers.<id>. */
    labelKey: string;
    configLabelKey: string;
    configPlaceholderKey: string;
    /** Rejects a reference the runtime could never resolve. */
    isValidRef?: (ref: string) => boolean;
}

/**
 * Operators every provider offers: its state is one optional string, so presence and
 * string comparison is all that is meaningful. Kept name-compatible with the `text` value
 * kind so a shared operator table can absorb both later.
 */
export const PROVIDER_OPERATORS: LogicOperator[] = [
    'equals',
    'notEquals',
    'contains',
    'exists',
    'notExists'
];

// Mirrors the runtime's own guard: a plain dotted identifier chain, no indexing, no calls.
const JS_VARIABLE_PATH_PATTERN = /^(window\.)?[A-Za-z_$][\w$]*(\.[A-Za-z_$][\w$]*)*$/;

// Cookie names are tokens: no separators, no whitespace, no control characters.
const COOKIE_NAME_PATTERN = /^[\w!#$%&'*+\-.^`|~]+$/;

const PROVIDERS: LogicProviderDescriptor[] = [
    {
        id: 'jsVariable',
        configKey: 'variable',
        labelKey: 'conditionalLogic.providers.jsVariable.label',
        configLabelKey: 'conditionalLogic.providers.jsVariable.configLabel',
        configPlaceholderKey: 'conditionalLogic.providers.jsVariable.configPlaceholder',
        isValidRef: ref => JS_VARIABLE_PATH_PATTERN.test(ref.trim())
    },
    {
        id: 'urlParam',
        configKey: 'param',
        labelKey: 'conditionalLogic.providers.urlParam.label',
        configLabelKey: 'conditionalLogic.providers.urlParam.configLabel',
        configPlaceholderKey: 'conditionalLogic.providers.urlParam.configPlaceholder'
    },
    {
        id: 'cookie',
        configKey: 'cookie',
        labelKey: 'conditionalLogic.providers.cookie.label',
        configLabelKey: 'conditionalLogic.providers.cookie.configLabel',
        configPlaceholderKey: 'conditionalLogic.providers.cookie.configPlaceholder',
        isValidRef: ref => COOKIE_NAME_PATTERN.test(ref.trim())
    }
];

export const listLogicProviders = (): LogicProviderDescriptor[] => PROVIDERS;

export const getLogicProvider = (sourceType?: string): LogicProviderDescriptor | undefined =>
    sourceType === undefined || sourceType === '' || sourceType === 'field'
        ? undefined
        : PROVIDERS.find(provider => provider.id === sourceType);

/** Every key a provider rule may store, used to strip stale config when the type changes. */
export const providerConfigKeys = (): ProviderConfigKey[] => PROVIDERS.map(provider => provider.configKey);
