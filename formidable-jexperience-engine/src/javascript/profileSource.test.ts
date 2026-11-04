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

type TrackerPage = {
    formidableJxp?: unknown;
    wem?: unknown;
    wemLoaded?: boolean;
    digitalDataOverrides?: unknown[];
};
const page = globalThis as unknown as TrackerPage;

/**
 * A tracked page: one form whose rules read `countryName`, and jExperience's tracker, whose context
 * holds the visitor's profile once it ran. `startedFirst` is a tracker that ran before the script.
 */
const trackedPage = ({startedFirst = false, overrides = true} = {}) => {
    document.body.innerHTML = '<script type="application/json" data-formidable-jxp="f1">{"formId":"f1","logic":["countryName"]}</script>';
    const callbacks: (() => void)[] = [];
    page.wem = {
        getLoadedContext: () => ({profileId: 'p1', profileProperties: {countryName: 'FR'}}),
        _registerCallback: (callback: () => void) => callbacks.push(callback)
    };
    page.wemLoaded = startedFirst;
    page.digitalDataOverrides = overrides ? [] : undefined;
    return {
        /** The tracker loads the context and runs its callbacks, as wem.js does. */
        load: () => {
            page.wemLoaded = true;
            callbacks.forEach(callback => callback());
        }
    };
};

afterEach(() => {
    registry.remove(LOGIC_SOURCE_REGISTRY_TYPE, PROFILE_SOURCE_ID);
    forgetRegisteredLogicProviders();
    delete page.formidableJxp;
    delete page.wem;
    delete page.wemLoaded;
    delete page.digitalDataOverrides;
    document.body.innerHTML = '';
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

    it('reads the profile once the tracker loaded the properties the script asked for', async () => {
        const tracker = trackedPage();
        await runPageScript();
        installLogicSourceQueue();
        const source = getPageProvider(PROFILE_SOURCE_ID)!;

        expect(page.digitalDataOverrides).toEqual([{wemInitConfig: {requiredProfileProperties: ['countryName']}}]);
        expect(source.available!()).toBe(false);
        tracker.load();

        expect(source.available!()).toBe(true);
        expect(source.read('countryName')).toBe('FR');
    });

    it.each([
        ['the tracker started before the script', {startedFirst: true}],
        ['the page has no digitalDataOverrides', {overrides: false}]
    ])('stays unavailable, not absent, when %s: the properties were never asked for', async (_case, setup) => {
        vi.spyOn(console, 'warn').mockImplementation(() => undefined);
        const tracker = trackedPage(setup);
        await runPageScript();
        installLogicSourceQueue();
        tracker.load();

        // the tracker itself is ready: only the request gate keeps the source closed
        expect((page.formidableJxp as {trackerReady: () => boolean}).trackerReady()).toBe(true);
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
