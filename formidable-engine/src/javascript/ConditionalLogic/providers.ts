import type {LogicOperator, ProviderConfigKey, RuleSourceType} from './ConditionalLogic.types';

/**
 * Rule sources that are not another form field: state outside the form, read in the
 * browser only. Each designates one thing whose value is a single optional string, so they
 * all share the same operator set and the same editing shape — a name to type in.
 *
 * A provider is therefore fully declarative here: no per-provider React, and no
 * per-provider code in the runtime evaluator either (see
 * formidable-elements/src/utils/logicProviders.ts, which must declare the same ids and
 * config keys; providers.test.ts checks it). This list is internal to the engine; it is
 * deliberately not a public extension point yet, but each entry is the whole description a
 * registered provider would give — its operators and the modules it needs included — so that
 * opening it later moves entries, without changing their shape.
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
    /** What a rule on this provider may test, the first being the default of a new rule. */
    operators: LogicOperator[];
    /**
     * Modules the site must have for the provider to be offered: those that put its state on the
     * page. A rule already stored on it stays editable where they are missing; it fails closed there.
     */
    requiredModules?: string[];
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

// Mirrors the runtime's own guard: a dotted chain of identifiers or numeric array
// indexes (e.g. "dataLayer.0.event"), no bracket indexing, no calls.
const JS_VARIABLE_PATH_PATTERN = /^(window\.)?[A-Za-z_$][\w$]*(\.([A-Za-z_$][\w$]*|\d+))*$/;

// Cookie names are tokens: no separators, no whitespace, no control characters.
const COOKIE_NAME_PATTERN = /^[\w!#$%&'*+\-.^`|~]+$/;

// jCustomer profile property ids: one top-level name, read as a flat key, so no path syntax.
const PROFILE_PROPERTY_PATTERN = /^[\w-]+$/;

const PROVIDERS: LogicProviderDescriptor[] = [
    {
        id: 'jsVariable',
        configKey: 'variable',
        labelKey: 'conditionalLogic.providers.jsVariable.label',
        configLabelKey: 'conditionalLogic.providers.jsVariable.configLabel',
        configPlaceholderKey: 'conditionalLogic.providers.jsVariable.configPlaceholder',
        isValidRef: ref => JS_VARIABLE_PATH_PATTERN.test(ref.trim()),
        operators: PROVIDER_OPERATORS
    },
    {
        id: 'urlParam',
        configKey: 'param',
        labelKey: 'conditionalLogic.providers.urlParam.label',
        configLabelKey: 'conditionalLogic.providers.urlParam.configLabel',
        configPlaceholderKey: 'conditionalLogic.providers.urlParam.configPlaceholder',
        operators: PROVIDER_OPERATORS
    },
    {
        id: 'cookie',
        configKey: 'cookie',
        labelKey: 'conditionalLogic.providers.cookie.label',
        configLabelKey: 'conditionalLogic.providers.cookie.configLabel',
        configPlaceholderKey: 'conditionalLogic.providers.cookie.configPlaceholder',
        isValidRef: ref => COOKIE_NAME_PATTERN.test(ref.trim()),
        operators: PROVIDER_OPERATORS
    },
    {
        // Namespaced: the id is stored in every rule, and a provider of another module must never
        // collide with one of Formidable's. The i18n keys are spelt out, as i18next reads ':' as a
        // namespace separator.
        id: 'jexperience:profile',
        configKey: 'property',
        labelKey: 'conditionalLogic.providers.jexperienceProfile.label',
        configLabelKey: 'conditionalLogic.providers.jexperienceProfile.configLabel',
        configPlaceholderKey: 'conditionalLogic.providers.jexperienceProfile.configPlaceholder',
        isValidRef: ref => PROFILE_PROPERTY_PATTERN.test(ref.trim()),
        operators: PROVIDER_OPERATORS,
        // the page's own gate (JExperienceSite.tracked): jExperience enabled on the site itself. The
        // script that reads the tracker comes with a global render filter, enabled on no site.
        requiredModules: ['jexperience']
    }
];

export const listLogicProviders = (): LogicProviderDescriptor[] => PROVIDERS;

/** The providers a new rule may pick on a site with these modules. */
export const offeredLogicProviders = (installedModules: string[]): LogicProviderDescriptor[] =>
    PROVIDERS.filter(provider => (provider.requiredModules ?? []).every(module => installedModules.includes(module)));

export const getLogicProvider = (sourceType?: string): LogicProviderDescriptor | undefined =>
    sourceType === undefined || sourceType === '' || sourceType === 'field'
        ? undefined
        : PROVIDERS.find(provider => provider.id === sourceType);

/** Every key a provider rule may store, used to strip stale config when the type changes. */
export const providerConfigKeys = (): ProviderConfigKey[] => PROVIDERS.map(provider => provider.configKey);
