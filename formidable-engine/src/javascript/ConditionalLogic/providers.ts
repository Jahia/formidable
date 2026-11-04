import {registry} from '@jahia/ui-extender';
import {
    type EditorLogicSource,
    LOGIC_SOURCE_ID_PATTERN,
    LOGIC_SOURCE_REGISTRY_TYPE,
    SHARED_RULE_KEYS
} from '@jahia/formidable-library/logic-sources';
import type {LogicOperator} from './ConditionalLogic.types';

/**
 * Rule sources that are not another form field: state outside the form, read in the
 * browser only. Each designates one thing whose value is a single optional string, so they
 * all share the same operator set and the same editing shape.
 *
 * Two kinds, one shape. The built-in sources below ship with the engine. A module adds a source
 * of its own by registering its editor half under LOGIC_SOURCE_REGISTRY_TYPE, and its page half
 * on the page (docs/adr/0001-extension-modules-register-logic-sources.md); the engine never names
 * such a module. A rule stored on a source no module registered shows as an unknown source.
 *
 * The page half of the built-ins is formidable-elements/src/utils/logicProviders.ts, which must
 * declare the same ids and config keys; providers.test.ts checks it.
 */
export interface LogicProviderDescriptor extends Omit<EditorLogicSource, 'operators'> {
    id: string;
    operators: LogicOperator[];
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

const BUILT_IN_PROVIDERS: LogicProviderDescriptor[] = [
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
    }
];

/** The built-in sources alone, which the page and the server know as well. */
export const builtInLogicProviders = (): LogicProviderDescriptor[] => BUILT_IN_PROVIDERS;

type RegisteredEntry = EditorLogicSource & {key?: unknown};

// One descriptor per registry entry, so that a row re-rendering gets the same object back and its
// reference list is not asked for again.
const descriptors = new WeakMap<object, LogicProviderDescriptor | null>();

const describe = (entry: RegisteredEntry): LogicProviderDescriptor | null => {
    const id = typeof entry.key === 'string' ? entry.key : '';
    const usable = LOGIC_SOURCE_ID_PATTERN.test(id)
        && typeof entry.configKey === 'string'
        && entry.configKey !== ''
        && !SHARED_RULE_KEYS.includes(entry.configKey)
        && !BUILT_IN_PROVIDERS.some(provider => provider.configKey === entry.configKey)
        && typeof entry.labelKey === 'string'
        && Array.isArray(entry.operators)
        && entry.operators.length > 0
        && entry.operators.every(operator => PROVIDER_OPERATORS.includes(operator));
    if (!usable) {
        console.warn(`[ConditionalLogic] ignored the logic source "${id}": its entry does not follow the contract`);
        return null;
    }

    return {
        id,
        configKey: entry.configKey,
        labelKey: entry.labelKey,
        configLabelKey: entry.configLabelKey,
        configPlaceholderKey: entry.configPlaceholderKey,
        isValidRef: entry.isValidRef,
        operators: entry.operators,
        requiredModules: entry.requiredModules,
        refChoices: entry.refChoices
    };
};

const descriptorOf = (entry: RegisteredEntry): LogicProviderDescriptor | null => {
    if (!descriptors.has(entry)) {
        descriptors.set(entry, describe(entry));
    }

    return descriptors.get(entry) ?? null;
};

const registeredProviders = (): LogicProviderDescriptor[] =>
    ((registry.find({type: LOGIC_SOURCE_REGISTRY_TYPE}) ?? []) as unknown as RegisteredEntry[])
        .map(descriptorOf)
        .filter((provider): provider is LogicProviderDescriptor => provider !== null);

/** The built-ins, then the sources registered by the modules loaded in this jContent. */
export const listLogicProviders = (): LogicProviderDescriptor[] => [...BUILT_IN_PROVIDERS, ...registeredProviders()];

/** The providers a new rule may pick on a site with these modules. */
export const offeredLogicProviders = (installedModules: string[]): LogicProviderDescriptor[] =>
    listLogicProviders().filter(provider => (provider.requiredModules ?? []).every(module => installedModules.includes(module)));

/** The provider of a rule: a built-in, or the entry registered under that id, read directly. */
export const getLogicProvider = (sourceType?: string): LogicProviderDescriptor | undefined => {
    if (sourceType === undefined || sourceType === '' || sourceType === 'field') {
        return undefined;
    }

    const builtIn = BUILT_IN_PROVIDERS.find(provider => provider.id === sourceType);
    if (builtIn) {
        return builtIn;
    }

    const entry = registry.get(LOGIC_SOURCE_REGISTRY_TYPE, sourceType) as unknown as RegisteredEntry | undefined;
    return (entry && descriptorOf(entry)) ?? undefined;
};

/** Every key a provider rule may store, used to strip stale config when the type changes. */
export const providerConfigKeys = (): string[] => listLogicProviders().map(provider => provider.configKey);
