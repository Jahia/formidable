import {afterEach, describe, expect, it, vi} from 'vitest';
import {registry} from '@jahia/ui-extender';
import {LOGIC_SOURCE_REGISTRY_TYPE} from '@jahia/formidable-library/logic-sources';
import {builtInLogicProviders as builtInPageProviders} from '../../../../formidable-elements/src/utils/logicProviders';
import {normalizeStoredProviderRule, parseRule, sanitizeProviderOperator} from './ConditionalLogic.utils';
import {builtInLogicProviders, getLogicProvider, listLogicProviders, offeredLogicProviders} from './providers';

/** A source as an extension module registers it: the engine knows nothing of it beforehand. */
const ACME_SEGMENT = {
    configKey: 'segment',
    labelKey: 'acme:segment.label',
    configLabelKey: 'acme:segment.configLabel',
    configPlaceholderKey: 'acme:segment.configPlaceholder',
    operators: ['equals', 'exists', 'notExists'],
    requiredModules: ['acme'],
    isValidRef: (ref: string) => /^\w+$/.test(ref.trim())
};

const unregister = () => {
    for (const entry of registry.find({type: LOGIC_SOURCE_REGISTRY_TYPE})) {
        registry.remove(LOGIC_SOURCE_REGISTRY_TYPE, entry.key);
    }
};

describe('the built-in providers of the editor and of the browser', () => {
    // The editor, the browser runtime and the server each declare the built-ins; a built-in
    // missing from one of them is a rule that the editor saves and the page or the server drops.
    // The server's list is checked against this one by FormLogicRuleCleanupTest.
    it('declare the same ids and config keys', () => {
        const editor = builtInLogicProviders().map(provider => [provider.id, provider.configKey]);
        const page = builtInPageProviders().map(provider => [provider.id, provider.configKey]);

        expect(page).toEqual(editor);
        expect(editor.map(([id]) => id)).toEqual(['jsVariable', 'urlParam', 'cookie']);
    });

    it.each(builtInLogicProviders().map(provider => [provider.id, provider.configKey] as const))(
        'keep the reference of %s through a parse and a save, as the rule row does at every keystroke',
        (id, configKey) => {
            const stored = JSON.stringify({logicId: 'l1', sourceType: id, [configKey]: 'country', operator: 'exists'});

            const saved = normalizeStoredProviderRule(parseRule(stored));

            expect(saved[configKey]).toBe('country');
            expect(saved.sourceType).toBe(id);
        }
    );
});

describe('a provider an extension module registers', () => {
    afterEach(() => {
        unregister();
        vi.restoreAllMocks();
    });

    it('is unknown until its module registers it, and the stored rule keeps its reference meanwhile', () => {
        const stored = JSON.stringify({logicId: 'l1', sourceType: 'acme:segment', segment: 'gold', operator: 'equals', value: 'x'});

        expect(getLogicProvider('acme:segment')).toBeUndefined();
        expect(parseRule(stored).segment).toBe('gold');

        registry.add(LOGIC_SOURCE_REGISTRY_TYPE, 'acme:segment', ACME_SEGMENT);

        const provider = getLogicProvider('acme:segment')!;
        expect(provider.configKey).toBe('segment');
        expect(normalizeStoredProviderRule(parseRule(stored))).toEqual(
            {logicId: 'l1', sourceType: 'acme:segment', segment: 'gold', operator: 'equals', value: 'x'}
        );
        expect(listLogicProviders().map(known => known.id)).toEqual(['jsVariable', 'urlParam', 'cookie', 'acme:segment']);
    });

    it('is the same object at every lookup, so a rule row does not ask for its list again', () => {
        registry.add(LOGIC_SOURCE_REGISTRY_TYPE, 'acme:segment', ACME_SEGMENT);

        expect(getLogicProvider('acme:segment')).toBe(getLogicProvider('acme:segment'));
    });

    it('is offered only on a site that has the modules it requires', () => {
        registry.add(LOGIC_SOURCE_REGISTRY_TYPE, 'acme:segment', ACME_SEGMENT);
        const ids = (modules: string[]) => offeredLogicProviders(modules).map(provider => provider.id);

        expect(ids(['formidable-elements'])).toEqual(['jsVariable', 'urlParam', 'cookie']);
        expect(ids(['formidable-elements', 'acme'])).toContain('acme:segment');
    });

    it('keeps an operator it offers and falls back to its first one otherwise', () => {
        registry.add(LOGIC_SOURCE_REGISTRY_TYPE, 'acme:segment', ACME_SEGMENT);
        const provider = getLogicProvider('acme:segment')!;

        expect(sanitizeProviderOperator('notExists', provider)).toBe('notExists');
        expect(sanitizeProviderOperator('contains', provider)).toBe('equals');
    });

    it.each([
        ['an id without its module', 'segment', ACME_SEGMENT],
        ['a shared rule key as config key', 'acme:value', {...ACME_SEGMENT, configKey: 'value'}],
        ['the config key of a built-in', 'acme:cookie', {...ACME_SEGMENT, configKey: 'cookie'}],
        ['an operator no provider has', 'acme:numbers', {...ACME_SEGMENT, configKey: 'n', operators: ['gt']}],
        ['no operator', 'acme:empty', {...ACME_SEGMENT, configKey: 'e', operators: []}],
        ['required modules that are no list', 'acme:modules', {...ACME_SEGMENT, configKey: 'm', requiredModules: 'acme'}],
        ['a reference check that is no function', 'acme:check', {...ACME_SEGMENT, configKey: 'c', isValidRef: true}],
        ['a reference list without its reader', 'acme:list', {...ACME_SEGMENT, configKey: 'l', refChoices: {query: 'q'}}],
        ['a label that is no string', 'acme:label', {...ACME_SEGMENT, configKey: 'b', configLabelKey: 3}]
    ])('is ignored with %s', (_case, id, entry) => {
        vi.spyOn(console, 'warn').mockImplementation(() => undefined);
        registry.add(LOGIC_SOURCE_REGISTRY_TYPE, id, entry);

        expect(getLogicProvider(id)).toBeUndefined();
        expect(console.warn).toHaveBeenCalled();
    });
});
