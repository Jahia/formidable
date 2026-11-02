import {describe, expect, it} from 'vitest';
import {getLogicProvider as getRuntimeProvider, logicProviderConfigKeys} from '../../../../formidable-elements/src/utils/logicProviders';
import {normalizeStoredProviderRule, parseRule, sanitizeProviderOperator} from './ConditionalLogic.utils';
import {getLogicProvider, listLogicProviders, offeredLogicProviders} from './providers';

describe('the providers of the editor and of the browser', () => {
    // The editor, the browser runtime and the server each declare the providers; a provider
    // missing from one of them is a rule that the editor saves and the page or the server drops.
    // The server's list is checked against this one by FormLogicRuleCleanupTest.
    it('declare the same ids and config keys', () => {
        const editor = Object.fromEntries(listLogicProviders().map(provider => [provider.id, provider.configKey]));

        for (const [id, configKey] of Object.entries(editor)) {
            expect(getRuntimeProvider(id)?.configKey, id).toBe(configKey);
        }

        const byName = (a: string, b: string) => a.localeCompare(b);
        expect([...logicProviderConfigKeys()].sort(byName)).toEqual(Object.values(editor).sort(byName));
    });

    it.each(listLogicProviders().map(provider => [provider.id, provider.configKey] as const))(
        'keep the reference of %s through a parse and a save, as the rule row does at every keystroke',
        (id, configKey) => {
            const stored = JSON.stringify({logicId: 'l1', sourceType: id, [configKey]: 'country', operator: 'exists'});

            const saved = normalizeStoredProviderRule(parseRule(stored));

            expect(saved[configKey]).toBe('country');
            expect(saved.sourceType).toBe(id);
        }
    );
});

describe('the jExperience profile provider', () => {
    it('is offered only on a site with jExperience and the Formidable integration', () => {
        const ids = (modules: string[]) => offeredLogicProviders(modules).map(provider => provider.id);

        expect(ids(['formidable-elements'])).not.toContain('jexperience:profile');
        expect(ids(['formidable-elements', 'jexperience'])).not.toContain('jexperience:profile');
        expect(ids(['formidable-elements', 'jexperience', 'formidable-jexperience-engine'])).toContain('jexperience:profile');
        expect(ids([])).toEqual(['jsVariable', 'urlParam', 'cookie']);
    });

    it('accepts a property name and refuses whitespace or path syntax', () => {
        const provider = getLogicProvider('jexperience:profile')!;

        expect(provider.isValidRef!('country')).toBe(true);
        expect(provider.isValidRef!(' nbOfVisits ')).toBe(true);
        expect(provider.isValidRef!('first name')).toBe(false);
        expect(provider.isValidRef!('properties[country]')).toBe(false);
    });

    it('keeps an operator it offers and falls back to its first one otherwise', () => {
        const provider = getLogicProvider('jexperience:profile')!;

        expect(sanitizeProviderOperator('notExists', provider)).toBe('notExists');
        expect(sanitizeProviderOperator('gt', provider)).toBe(provider.operators[0]);
    });
});
