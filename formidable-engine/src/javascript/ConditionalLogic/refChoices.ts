import {useEffect, useState} from 'react';
import {postGraphQLRequest} from './sources';
import type {LogicProviderDescriptor} from './providers';

/** One entry of a provider's reference dropdown: what the rule stores, and what the author reads. */
export interface RefChoice {
    value: string;
    label: string;
}

/**
 * What a provider's reference list answered: the entries, or `available: false` when the list cannot be
 * had — the module that serves it is not installed, the service behind it is down, the request failed.
 * The rule row then keeps a text input, so a rule can still be written and an existing one still read.
 */
export interface RefChoicesLoad {
    available: boolean;
    choices: RefChoice[];
}

export const UNAVAILABLE: RefChoicesLoad = {available: false, choices: []};

/** How a provider lists the references a rule may pick: a GraphQL document and how to read its answer. */
export interface RefChoicesSource {
    query: string;
    read: (data: unknown) => RefChoicesLoad;
}

interface GraphQLAnswer {
    data?: unknown;
    errors?: {message: string}[];
}

export type RefChoicesPost = (query: string, variables: {path: string}) => Promise<GraphQLAnswer>;

/**
 * The provider's list for the node at `path`. An answer with errors is unavailable: a schema without the
 * field — the module that serves it not installed — fails validation, and so does a refused path.
 */
export const fetchRefChoices = async (
    source: RefChoicesSource,
    path: string,
    post: RefChoicesPost = (query, variables) => postGraphQLRequest<GraphQLAnswer>(query, variables)
): Promise<RefChoicesLoad> => {
    const answer = await post(source.query, {path});
    if (answer.errors?.length) {
        console.warn('[ConditionalLogic] no reference list:', answer.errors.map(error => error.message).join('; '));
        return UNAVAILABLE;
    }

    return source.read(answer.data);
};

/**
 * The lists in flight or kept, one per provider and node. The editor mounts one row per rule and remounts
 * them all when a rule is added, each asking for the same list; the server keeps its own list for a minute,
 * so this one lives as long as the editor's page, less a failure, which the next row asks again.
 */
const loads = new Map<string, Promise<RefChoicesLoad>>();

export const acquireRefChoices = (
    provider: LogicProviderDescriptor,
    path: string,
    load: (source: RefChoicesSource, path: string) => Promise<RefChoicesLoad> = fetchRefChoices
): Promise<RefChoicesLoad> => {
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
export const refChoiceOptions = (choices: RefChoice[], stored: string, isValidRef?: (ref: string) => boolean): RefChoice[] => {
    const offered = isValidRef ? choices.filter(choice => isValidRef(choice.value)) : choices;
    const current = stored.trim();
    return current === '' || offered.some(choice => choice.value === current)
        ? offered
        : [{value: current, label: current}, ...offered];
};

export type RefChoicesView = {status: 'none'} | {status: 'loading'} | {status: 'ready'; choices: RefChoice[]};

/**
 * The reference list of a rule row's provider: `none` when the provider has no list or it is unavailable
 * (the row keeps its text input), `loading` until it answers.
 */
export const useRefChoices = (provider: LogicProviderDescriptor | undefined, path: string | undefined): RefChoicesView => {
    const key = provider?.refChoices && path ? `${provider.id}|${path}` : null;
    const [held, setHeld] = useState<{key: string; load: RefChoicesLoad} | null>(null);

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

    return held.load.available ? {status: 'ready', choices: held.load.choices} : {status: 'none'};
};
