import React, {useEffect, useState} from 'react';
import {useApolloClient, gql} from '@apollo/client';
import {useTranslation} from 'react-i18next';
import {Dropdown, Typography} from '@jahia/moonstone';

interface ValueConstraint {
    displayValue?: string;
    value?: {string?: string};
}

interface ContentOptionsCmpProps {
    field: {
        readOnly?: boolean;
    };
    id: string;
    value?: string;
    onChange: (value: string) => void;
    editorContext?: {
        path?: string;
        site?: string;
        lang?: string;
        uilang?: string;
        nodeData?: {
            path?: string;
            primaryNodeType?: {name?: string};
        };
    };
    form?: {
        values?: Record<string, unknown>;
    };
}

// Value of the single preview entry the server returns when the query cap is
// exceeded; its label carries the limit. Mirrors FormidableOptionsPreviewInitializer.
const CAP_EXCEEDED_MARKER = '__fmdbCapExceeded__';

const ROOT_NODE_FIELD_KEY = 'fmdbmix:contentOptions_fmdb:optionsRootNode';

interface WorkspacePreview {
    status: 'loading' | 'ready' | 'error';
    capLimit?: string;
    options: Array<{value: string; label: string}>;
}

interface PreviewState {
    status: 'idle' | 'loading' | 'ready' | 'error';
    edit: WorkspacePreview;
    live: WorkspacePreview;
}

const IDLE_PREVIEW: PreviewState = {
    status: 'idle',
    edit: {status: 'loading', options: []},
    live: {status: 'loading', options: []}
};

const CONTENT_TYPES_QUERY = gql`
    query formidableContentOptionTypes($sitePath: String!, $uiLocale: String!) {
        forms {
            contentTypesAsTree(
                nodeTypes: ["jmix:editorialContent"],
                includeSubTypes: true,
                uuidOrPath: $sitePath,
                uiLocale: $uiLocale
            ) {
                label
                children {
                    name
                    label
                }
            }
        }
    }
`;

const PREVIEW_QUERY = gql`
    query formidableContentOptionsPreview(
        $parent: String!,
        $primaryNodeType: String!,
        $context: [InputContextEntryInput],
        $uiLocale: String!,
        $locale: String!
    ) {
        forms {
            fieldConstraints(
                parentNodeUuidOrPath: $parent,
                primaryNodeType: $primaryNodeType,
                fieldNodeType: "fmdbmix:contentOptions",
                fieldName: "fmdb:optionsNodeType",
                context: $context,
                uiLocale: $uiLocale,
                locale: $locale
            ) {
                displayValue
                value {
                    string
                }
            }
        }
    }
`;

const parentPathOf = (path?: string): string | undefined => {
    if (!path || !path.includes('/')) {
        return undefined;
    }

    const parent = path.substring(0, path.lastIndexOf('/'));
    return parent.length > 0 ? parent : undefined;
};

const toWorkspacePreview = (constraints: ValueConstraint[]): WorkspacePreview => {
    if (constraints.length === 1 && constraints[0].value?.string === CAP_EXCEEDED_MARKER) {
        return {status: 'ready', capLimit: constraints[0].displayValue ?? '', options: []};
    }

    return {
        status: 'ready',
        options: constraints.map(constraint => ({
            value: constraint.value?.string ?? '',
            label: constraint.displayValue ?? constraint.value?.string ?? ''
        }))
    };
};

const PreviewList = ({id, label, options}: {id: string; label: string; options: Array<{value: string; label: string}>}) => (
    <>
        <Typography variant="caption">{label}</Typography>
        {options.length > 0 && (
            <ul
                id={id}
                aria-label={label}
                style={{
                    margin: 0,
                    padding: 'var(--moon-spacing-small, 8px)',
                    listStyle: 'none',
                    maxHeight: '160px',
                    overflowY: 'auto',
                    backgroundColor: 'var(--moon-color-gray_light40)',
                    border: 'var(--border-selector, 1px solid var(--color-gray))',
                    borderRadius: '2px'
                }}
            >
                {options.map(option => (
                    <li key={option.value}>
                        <Typography variant="body">{option.label}</Typography>
                    </li>
                ))}
            </ul>
        )}
    </>
);

export const ContentOptionsCmp = ({field, id, value, onChange, editorContext, form}: ContentOptionsCmpProps) => {
    const {t} = useTranslation('formidable-engine');
    const client = useApolloClient();
    const [types, setTypes] = useState<Array<{groupLabel: string; options: Array<{label: string; value: string}>}>>([]);
    const [preview, setPreview] = useState<PreviewState>(IDLE_PREVIEW);

    const rootUuid = typeof form?.values?.[ROOT_NODE_FIELD_KEY] === 'string'
        ? (form.values[ROOT_NODE_FIELD_KEY] as string)
        : '';

    const nodePath = editorContext?.nodeData?.path;
    const parent = parentPathOf(nodePath) ?? editorContext?.path ?? '/sites';
    const primaryNodeType = editorContext?.nodeData?.primaryNodeType?.name ?? 'fmdb:select';
    const sitePath = editorContext?.site ? `/sites/${editorContext.site}` : parent;
    const uiLocale = editorContext?.uilang
        ?? (window as unknown as {contextJsParameters?: {uilang?: string}}).contextJsParameters?.uilang
        ?? 'en';
    const locale = editorContext?.lang ?? uiLocale;

    useEffect(() => {
        let cancelled = false;
        client.query({query: CONTENT_TYPES_QUERY, variables: {sitePath, uiLocale}}).then(result => {
            if (cancelled) {
                return;
            }

            const tree: Array<{label?: string; children?: Array<{name?: string; label?: string}>}> =
                result.data?.forms?.contentTypesAsTree ?? [];
            setTypes(tree
                .map(group => ({
                    groupLabel: group.label ?? '',
                    options: (group.children ?? [])
                        // Form elements as options of a form field are never what a
                        // contributor is after.
                        .filter(child => Boolean(child.name) && !child.name!.startsWith('fmdb'))
                        .map(child => ({label: child.label ?? child.name!, value: child.name!}))
                }))
                .filter(group => group.options.length > 0));
        }).catch(() => {
            if (!cancelled) {
                setTypes([]);
            }
        });

        return () => {
            cancelled = true;
        };
    }, [client, sitePath, uiLocale]);

    useEffect(() => {
        if (!value || !rootUuid) {
            return;
        }

        let cancelled = false;
        // Fetch effect: the loading state has to be flagged when the queries start.
        // eslint-disable-next-line @eslint-react/hooks-extra/no-direct-set-state-in-use-effect
        setPreview({...IDLE_PREVIEW, status: 'loading'});

        const queryWorkspace = (workspace: 'default' | 'live') => client.query({
            query: PREVIEW_QUERY,
            fetchPolicy: 'network-only',
            variables: {
                parent,
                primaryNodeType,
                context: [
                    {key: 'rootNode', value: [rootUuid]},
                    {key: 'nodeType', value: [value]},
                    {key: 'workspace', value: [workspace]}
                ],
                uiLocale,
                locale
            }
        }).then(result => toWorkspacePreview(result.data?.forms?.fieldConstraints ?? []))
            .catch((): WorkspacePreview => ({status: 'error', options: []}));

        Promise.all([queryWorkspace('default'), queryWorkspace('live')]).then(([edit, live]) => {
            if (!cancelled) {
                setPreview({status: 'ready', edit, live});
            }
        });

        return () => {
            cancelled = true;
        };
    }, [client, value, rootUuid, parent, primaryNodeType, uiLocale, locale]);

    const showPreview = Boolean(value) && Boolean(rootUuid);
    const capLimit = preview.edit.capLimit ?? preview.live.capLimit;
    const missingInLive = preview.edit.options.length - preview.live.options.length;

    return (
        <div className="flexCol flexFluid" style={{gap: '0.5rem'}}>
            <Dropdown
                className="flexFluid"
                id={id}
                variant="outlined"
                size="medium"
                data={types}
                value={value ?? ''}
                placeholder={t('contentOptions.selectType')}
                isDisabled={field.readOnly}
                hasSearch
                onChange={(_event: unknown, item: {value: string}) => onChange(item.value)}
            />

            {Boolean(value) && !rootUuid && (
                <Typography variant="caption">{t('contentOptions.rootMissing')}</Typography>
            )}
            {showPreview && preview.status === 'loading' && (
                <Typography variant="caption">{t('contentOptions.previewLoading')}</Typography>
            )}
            {showPreview && preview.status === 'ready' && capLimit && (
                <Typography variant="caption" style={{color: 'var(--color-warning_dark, #a05e03)'}}>
                    {t('contentOptions.capExceeded', {limit: capLimit})}
                </Typography>
            )}
            {showPreview && preview.status === 'ready' && !capLimit && (
                <>
                    {preview.edit.status === 'error' && (
                        <Typography variant="caption">{t('contentOptions.previewUnavailable')}</Typography>
                    )}
                    {preview.edit.status === 'ready' && (
                        <PreviewList
                            id={`${id}-preview-edit`}
                            label={t('contentOptions.previewEdit', {count: preview.edit.options.length})}
                            options={preview.edit.options}
                        />
                    )}
                    {preview.live.status === 'ready' && (
                        <PreviewList
                            id={`${id}-preview-live`}
                            label={t('contentOptions.previewLive', {count: preview.live.options.length})}
                            options={preview.live.options}
                        />
                    )}
                    {preview.edit.status === 'ready' && preview.live.status === 'ready'
                        && preview.live.options.length === 0 && preview.edit.options.length > 0 && (
                        <Typography variant="caption" style={{color: 'var(--color-warning_dark, #a05e03)'}}>
                            {t('contentOptions.liveEmpty')}
                        </Typography>
                    )}
                    {preview.edit.status === 'ready' && preview.live.status === 'ready'
                        && preview.live.options.length > 0 && missingInLive > 0 && (
                        <Typography variant="caption" style={{color: 'var(--color-warning_dark, #a05e03)'}}>
                            {t('contentOptions.liveFewer', {missing: missingInLive})}
                        </Typography>
                    )}
                </>
            )}
        </div>
    );
};
