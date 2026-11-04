// @vitest-environment jsdom
import {afterEach, describe, expect, it, vi} from 'vitest';
import {registry} from '@jahia/ui-extender';
import {LOGIC_SOURCE_REGISTRY_TYPE} from '@jahia/formidable-library/logic-sources';
import {getLogicProvider as getEditorProvider} from '../../../formidable-engine/src/javascript/ConditionalLogic/providers';
import {
    forgetRegisteredLogicProviders,
    getLogicProvider as getPageProvider,
    installLogicSourceQueue
} from '../../../formidable-elements/src/utils/logicProviders';
import {PROFILE_SOURCE_ID, profileSource, readProfileProperties} from './profileSource';
// A path held apart, so that the type-check leaves the page script to its own module's build
const PAGE_SCRIPT = '../main/resources/javascript/formidable-jxp.js';

/** Runs the page script anew, as the render filter's page would, before or after Formidable's script. */
const runPageScript = async () => {
    vi.resetModules();
    await import(/* @vite-ignore */ PAGE_SCRIPT);
};

afterEach(() => {
    registry.remove(LOGIC_SOURCE_REGISTRY_TYPE, PROFILE_SOURCE_ID);
    forgetRegisteredLogicProviders();
    delete (globalThis as {formidableJxp?: unknown}).formidableJxp;
    vi.restoreAllMocks();
});

describe('the jExperience profile source', () => {
    it('is the same source in the editor and on the page', async () => {
        registry.add(LOGIC_SOURCE_REGISTRY_TYPE, PROFILE_SOURCE_ID, profileSource);
        await runPageScript();
        installLogicSourceQueue();

        const editor = getEditorProvider(PROFILE_SOURCE_ID);
        const page = getPageProvider(PROFILE_SOURCE_ID);

        expect(editor?.configKey).toBe('property');
        expect(page?.configKey).toBe(editor?.configKey);
    });

    it('registers on the page whether Formidable\'s script runs before or after it', async () => {
        installLogicSourceQueue();
        await runPageScript();

        expect(getPageProvider(PROFILE_SOURCE_ID)?.configKey).toBe('property');
    });

    it('is unavailable on the page until the tracker loaded the profile', async () => {
        await runPageScript();
        installLogicSourceQueue();

        expect(getPageProvider(PROFILE_SOURCE_ID)!.available!()).toBe(false);
    });

    it('is offered only on a site jExperience is enabled on, as the page decides', () => {
        expect(profileSource.requiredModules).toEqual(['jexperience']);
    });

    it('accepts a property name and refuses whitespace or path syntax', () => {
        const isValidRef = profileSource.isValidRef!;

        expect(isValidRef('country')).toBe(true);
        expect(isValidRef(' nbOfVisits ')).toBe(true);
        expect(isValidRef('first name')).toBe(false);
        expect(isValidRef('properties[country]')).toBe(false);
        // read as one key of the profile's properties, so a path never resolves
        expect(isValidRef('address.city')).toBe(false);
    });
});

describe('the profile property list', () => {
    it('reads the answer as dropdown entries', () => {
        const data = {formidableJExperienceProfileProperties: {available: true, properties: [
            {name: 'country', label: 'Country (country)'},
            {name: 'nbOfVisits', label: ''}
        ]}};

        expect(readProfileProperties(data)).toEqual({available: true, choices: [
            {value: 'country', label: 'Country (country)'},
            {value: 'nbOfVisits', label: 'nbOfVisits'}
        ]});
    });

    it('is unavailable when jCustomer could not be asked', () => {
        expect(readProfileProperties({formidableJExperienceProfileProperties: {available: false, properties: []}}).available).toBe(false);
        expect(readProfileProperties(null).available).toBe(false);
    });
});
