import {useEffect, useState} from 'react';
import {postGraphQLRequest} from './sources';
import type {LogicProviderDescriptor} from './providers';
import type {
    LogicSourceRefChoice,
    LogicSourceRefChoices,
    LogicSourceRefChoicesSource
} from '@jahia/formidable-library/logic-sources';

/**
 * A provider's list answers its entries, or `available: false` when the list cannot be had — the module
 * that serves it is not installed, the service behind it is down, the request failed. The rule row then
 * keeps a text input, so a rule can still be written and an existing one still read.
 */
export const UNAVAILABLE: LogicSourceRefChoices = {available: false, choices: []};

interface GraphQLAnswer {
    data?: unknown;
    errors?: {message: string}[];
}

export type RefChoicesPost = (query: string, variables: {path: string}) => Promise<GraphQLAnswer>;

/**
 * The provider's list for the node at `path`. An answer with errors is unavailable: a schema without the
 * field — the module that serves it not installed — fails validation, and so does a refused path. What the
 * provider's `read` answers comes from another module's plain JavaScript: an entry that is no pair of
 * strings is left out.
 */
export const fetchRefChoices = async (
    source: LogicSourceRefChoicesSource,
    path: string,
    post: RefChoicesPost = (query, variables) => postGraphQLRequest<GraphQLAnswer>(query, variables)
): Promise<LogicSourceRefChoices> => {
    const answer = await post(source.query, {path});
    if (answer.errors?.length) {
        console.warn('[ConditionalLogic] no reference list:', answer.errors.map(error => error.message).join('; '));
        return UNAVAILABLE;
    }

    const load = source.read(answer.data) as Partial<LogicSourceRefChoices> | undefined;
    if (load?.available !== true) {
        return UNAVAILABLE;
    }

    const choices = Array.isArray(load.choices) ? load.choices : [];
    return {
        available: true,
        choices: choices.filter(choice => typeof choice?.value === 'string' && typeof choice.label === 'string')
    };
};

/**
 * The lists in flight or kept, one per provider and node. The editor mounts one row per rule and remounts
 * them all when a rule is added, each asking for the same list; the server keeps its own list for a minute,
 * so this one lives as long as the editor's page, less a failure, which the next row asks again.
 */
const loads = new Map<string, Promise<LogicSourceRefChoices>>();

export const acquireRefChoices = (
    provider: LogicProviderDescriptor,
    path: string,
    load: (source: LogicSourceRefChoicesSource, path: string) => Promise<LogicSourceRefChoices> = fetchRefChoices
): Promise<LogicSourceRefChoices> => {
    const source = provider.refChoices;
    if (!source) {
        return Promise.resolve(UNAVAILABLE);
    }

    const key = `${provider.id}|${path}`;
    let shared = loads.get(key);
    if (!shared) {
        const created = load(source, path).catch((error: unknown) => {
            console.warn('[ConditionalLogic] no reference list:', error);
            if (loads.get(key) === created) {
                loads.delete(key);
            }

            return UNAVAILABLE;
        });
        loads.set(key, created);
        shared = created;
    }

    return shared;
};

/** Test seam: forgets every list at once. */
export const forgetRefChoices = (): void => {
    loads.clear();
};

/**
 * The entries the dropdown offers: the list, less what the provider could never resolve, and the stored
 * reference when the list lacks it — gone from the schema, or one the author typed while the list was
 * unavailable — so that opening the rule never rewrites it.
 */
export const refChoiceOptions = (choices: LogicSourceRefChoice[], stored: string, isValidRef?: (ref: string) => boolean): LogicSourceRefChoice[] => {
    const offered = isValidRef ? choices.filter(choice => isValidRef(choice.value)) : choices;
    const current = stored.trim();
    return current === '' || offered.some(choice => choice.value === current)
        ? offered
        : [{value: current, label: current}, ...offered];
};

export type RefChoicesView = {status: 'none'} | {status: 'loading'} | {status: 'ready'; choices: LogicSourceRefChoice[]};

/**
 * The view of a list that answered: `none` when it is unavailable or offers nothing the provider can resolve,
 * so that the row keeps its text input and the rule can still be written.
 */
export const readyView = (load: LogicSourceRefChoices, isValidRef?: (ref: string) => boolean): RefChoicesView => {
    const offered = isValidRef ? load.choices.filter(choice => isValidRef(choice.value)) : load.choices;
    return load.available && offered.length > 0 ? {status: 'ready', choices: offered} : {status: 'none'};
};

/**
 * The reference list of a rule row's provider: `none` when the provider has no list or nothing to offer
 * (readyView), `loading` until it answers.
 */
export const useRefChoices = (provider: LogicProviderDescriptor | undefined, path: string | undefined): RefChoicesView => {
    const key = provider?.refChoices && path ? `${provider.id}|${path}` : null;
    const [held, setHeld] = useState<{key: string; load: LogicSourceRefChoices} | null>(null);

    useEffect(() => {
        if (!key || !provider || !path) {
            return undefined;
        }

        let cancelled = false;
        acquireRefChoices(provider, path).then(load => {
            if (!cancelled) {
                setHeld({key, load});
            }
        });

        return () => {
            cancelled = true;
        };
    }, [key, provider, path]);

    if (!key) {
        return {status: 'none'};
    }

    if (held?.key !== key) {
        return {status: 'loading'};
    }

    return readyView(held.load, provider?.isValidRef);
};
