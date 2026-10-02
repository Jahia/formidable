import {useEffect, useMemo, useState} from 'react';
import {CONDITIONAL_LOGIC_SOURCES} from './graphql';
import {buildLogicIdToSourceMap, buildSourceFieldOptions} from './ConditionalLogic.utils';
import type {GraphNode, SourceFieldOption} from './ConditionalLogic.types';

/**
 * What the rules editor needs of the form around the edited field: the sources a rule may read, in
 * form order and before the field, and the weakrefs the stored rules point at.
 */
export interface SourcesLoad {
    sources: SourceFieldOption[];
    logicIdToSource: Map<string, {name: string; uuid: string}>;
}

/** The coordinates of one load — also its sharing key: the same field, workspace and languages share one request. */
export interface SourcesInput {
    path: string;
    workspace: string;
    language: string;
    defaultLanguage: string;
}

interface GraphQLResponse {
    data?: {jcr?: {nodeByPath?: GraphNode | null} | null} | null;
    errors?: {message: string}[];
}

export type GraphQLPost = (query: string, variables: SourcesInput) => Promise<GraphQLResponse>;

const contextPath = (): string =>
    (globalThis as typeof globalThis & {contextJsParameters?: {contextPath?: string}}).contextJsParameters?.contextPath ?? '';

/**
 * One POST to the GraphQL endpoint, outside the editor's Apollo client. Measured on 8.2.4 (2026-10-02,
 * a field of the complete playground form): through that client each query of this editor left the
 * browser about a second after it was issued — a batching window, paid once per query — while the
 * server answered in 20 to 35 ms. The editor reads the form once and keeps nothing in the client's
 * cache, so a direct request loses nothing and skips the wait.
 */
export const postGraphQL: GraphQLPost = async (query, variables) => {
    const response = await fetch(`${contextPath()}/modules/graphql`, {
        method: 'POST',
        credentials: 'same-origin',
        headers: {'Content-Type': 'application/json'},
        body: JSON.stringify({query, variables})
    });
    if (!response.ok) {
        throw new Error(`GraphQL answered ${response.status}`);
    }

    return response.json() as Promise<GraphQLResponse>;
};

/** Reads the form around the field in one round trip and shapes it for the editor; fails when no form stands above the field. */
export const fetchSources = async (input: SourcesInput, post: GraphQLPost = postGraphQL): Promise<SourcesLoad> => {
    const result = await post(CONDITIONAL_LOGIC_SOURCES, input);
    if (result.errors?.length) {
        throw new Error(result.errors.map(error => error.message).join('; '));
    }

    const field = result.data?.jcr?.nodeByPath;
    const form = field?.ancestors?.find(ancestor => ancestor.primaryNodeType?.name === 'fmdb:form');
    if (!field || !form) {
        throw new Error(`no form above ${input.path}`);
    }

    return {
        sources: buildSourceFieldOptions(field.path, form.descendants?.nodes ?? []),
        logicIdToSource: buildLogicIdToSourceMap(field.descendant?.children?.nodes ?? [])
    };
};

interface SharedLoad {
    promise: Promise<SourcesLoad>;
    holders: number;
    eviction?: ReturnType<typeof setTimeout>;
}

/**
 * The loads in flight or kept, one per {@link SourcesInput}, shared by every rule row of the field being
 * edited: the editor mounts one rules component per stored rule, and remounts them all when a rule is
 * added or removed, each asking for the same form. A row acquires the load on mount and releases it on
 * unmount; the entry leaves once nobody holds it, after a grace long enough for a remount storm to
 * re-acquire it — so adding a rule costs nothing, while closing the editor forgets the form and a field
 * added elsewhere shows at the next opening.
 */
const loads = new Map<string, SharedLoad>();

export const RELEASE_GRACE_MS = 1500;

/** The sharing key of a load: the rows of one field, in one workspace and language pair, hold the same entry. */
export const sourcesKey = (input: SourcesInput): string => [input.workspace, input.path, input.language, input.defaultLanguage].join('|');

export const acquireSources = (
    input: SourcesInput,
    load: (input: SourcesInput) => Promise<SourcesLoad> = fetchSources
): Promise<SourcesLoad> => {
    const key = sourcesKey(input);
    let shared = loads.get(key);
    if (!shared) {
        const created: SharedLoad = {promise: load(input), holders: 0};
        // A failed load is not kept: the next row to ask, or this one remounted, tries again
        created.promise.catch(() => {
            if (loads.get(key) === created) {
                loads.delete(key);
            }
        });
        loads.set(key, created);
        shared = created;
    }

    if (shared.eviction) {
        clearTimeout(shared.eviction);
        shared.eviction = undefined;
    }

    shared.holders += 1;
    return shared.promise;
};

export const releaseSources = (input: SourcesInput): void => {
    const key = sourcesKey(input);
    const shared = loads.get(key);
    if (!shared) {
        return;
    }

    shared.holders = Math.max(0, shared.holders - 1);
    if (shared.holders === 0 && !shared.eviction) {
        shared.eviction = setTimeout(() => {
            if (loads.get(key) === shared && shared.holders === 0) {
                loads.delete(key);
            }
        }, RELEASE_GRACE_MS);
    }
};

/** Test seam: forgets every load at once. */
export const forgetSources = (): void => {
    for (const shared of loads.values()) {
        if (shared.eviction) {
            clearTimeout(shared.eviction);
        }
    }

    loads.clear();
};

interface HeldLoad {
    key: string;
    sources: SourceFieldOption[];
    logicIdToSource: Map<string, {name: string; uuid: string}>;
    failed: boolean;
}

export interface SharedSources extends Pick<HeldLoad, 'sources' | 'logicIdToSource'> {
    /** True until the load this row holds has answered. */
    loading: boolean;
    /** The i18n key of what prevents a list: no field path to load for, or a failed request. */
    errorKey: 'conditionalLogic.unresolvedContext' | 'conditionalLogic.loadError' | null;
}

const NOTHING: Pick<HeldLoad, 'sources' | 'logicIdToSource'> = {sources: [], logicIdToSource: new Map()};

/** What a row shows, from the load that answered last — pure, so the tests cover it without rendering. */
export const sharedSourcesView = (input: SourcesInput | null, held: HeldLoad | null): SharedSources => {
    if (!input) {
        return {...NOTHING, loading: false, errorKey: 'conditionalLogic.unresolvedContext'};
    }

    if (held?.key !== sourcesKey(input)) {
        return {...NOTHING, loading: true, errorKey: null};
    }

    return {
        sources: held.sources,
        logicIdToSource: held.logicIdToSource,
        loading: false,
        errorKey: held.failed ? 'conditionalLogic.loadError' : null
    };
};

/**
 * The shared load as a rule row uses it: acquired on mount, released on unmount, held by its key so that
 * `loading` and the error derive from what answered rather than being set ahead of the request.
 */
export const useSharedSources = (
    path: string | undefined,
    context: {workspace: string; language: string; defaultLanguage: string}
): SharedSources => {
    const {workspace, language, defaultLanguage} = context;
    const input = useMemo(
        () => (path ? {path, workspace, language, defaultLanguage} : null),
        [path, workspace, language, defaultLanguage]
    );
    const [held, setHeld] = useState<HeldLoad | null>(null);

    useEffect(() => {
        if (!input) {
            return undefined;
        }

        let cancelled = false;
        const key = sourcesKey(input);
        acquireSources(input)
            .then(load => {
                if (!cancelled) {
                    setHeld({key, sources: load.sources, logicIdToSource: load.logicIdToSource, failed: false});
                }
            })
            .catch((error: unknown) => {
                if (!cancelled) {
                    console.error('[ConditionalLogic] failed to load source fields', error);
                    setHeld({key, ...NOTHING, failed: true});
                }
            });

        return () => {
            cancelled = true;
            releaseSources(input);
        };
    }, [input]);

    return sharedSourcesView(input, held);
};
