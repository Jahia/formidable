import React, {useEffect, useRef, useState} from 'react';
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
        // The offerable content types, computed server-side from the contents under
        // the picked root (formidableContentTypes initializer) and re-resolved by the
        // editor whenever the root changes (dependentProperties on the CND).
        valueConstraints?: ValueConstraint[];
    };
    id: string;
    value?: string | null;
    // forceTouch=true makes the editor revalidate right away (it validates in
    // bulk otherwise), clearing or raising the required error on the spot.
    onChange: (value: string | null, forceTouch?: boolean) => void;
    onBlur?: () => void;
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
    // flex-basis 0 (not a length): a length basis makes the wrapping flex ancestor
    // chain reserve a phantom second line below the lists in the editor layout.
    <div style={{flex: '1 1 0', minWidth: 0, display: 'flex', flexDirection: 'column', gap: '0.25rem'}}>
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
    </div>
);

export const ContentOptionsCmp = ({field, id, value, onChange, onBlur, editorContext, form}: ContentOptionsCmpProps) => {
    const {t} = useTranslation('formidable-engine');
    const client = useApolloClient();
    const [preview, setPreview] = useState<PreviewState>(IDLE_PREVIEW);
    const prevValueRef = useRef<string | null | undefined>(undefined);

    const rootUuid = typeof form?.values?.[ROOT_NODE_FIELD_KEY] === 'string'
        ? (form.values[ROOT_NODE_FIELD_KEY] as string)
        : '';

    const nodePath = editorContext?.nodeData?.path;
    const parent = parentPathOf(nodePath) ?? editorContext?.path ?? '/sites';
    const primaryNodeType = editorContext?.nodeData?.primaryNodeType?.name ?? 'fmdb:select';
    const uiLocale = editorContext?.uilang
        ?? (window as unknown as {contextJsParameters?: {uilang?: string}}).contextJsParameters?.uilang
        ?? 'en';
    const locale = editorContext?.lang ?? uiLocale;

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

    // Standard choicelist behavior (jcontent SingleSelect): a value that the
    // refreshed constraints no longer contain is reset — picking another root
    // blanks the type and its preview — and restored if constraints arriving
    // late (async refresh) do contain it again.
    const valueConstraints = field.valueConstraints;
    useEffect(() => {
        if (value && !valueConstraints?.some(constraint => constraint.value?.string === value)) {
            prevValueRef.current = value;
            onChange(null);
        } else if (value === null && prevValueRef.current
            && valueConstraints?.some(constraint => constraint.value?.string === prevValueRef.current)) {
            onChange(prevValueRef.current, true);
        }
    }, [value, valueConstraints, onChange]);

    const showPreview = Boolean(value) && Boolean(rootUuid);
    const capLimit = preview.edit.capLimit ?? preview.live.capLimit;
    const missingInLive = preview.edit.options.length - preview.live.options.length;

    const rootOptions = (field.valueConstraints ?? [])
        .map(constraint => ({
            value: constraint.value?.string ?? '',
            label: constraint.displayValue ?? constraint.value?.string ?? ''
        }))
        .filter(option => option.value !== '');
    const noTypesUnderRoot = Boolean(rootUuid) && rootOptions.length === 0 && !value;

    return (
        <div className="flexCol flexFluid" style={{gap: '0.5rem'}}>
            <Dropdown
                className="flexFluid"
                id={id}
                variant="outlined"
                size="medium"
                data={rootOptions}
                value={value ?? ''}
                placeholder={rootUuid ? t('contentOptions.selectType') : t('contentOptions.pickRootFirst')}
                isDisabled={field.readOnly || !rootUuid || noTypesUnderRoot}
                hasSearch
                onClear={value && !field.readOnly ? () => {
                    // A deliberate clear must not be undone by the constraints-late
                    // restore branch of the reset effect.
                    prevValueRef.current = undefined;
                    onChange(null, true);
                } : undefined}
                onChange={(_event: unknown, item: {value?: string}) => {
                    if (item.value) {
                        onChange(item.value, true);
                    }
                }}
                onBlur={onBlur}
            />

            {noTypesUnderRoot && (
                <Typography variant="caption" style={{color: 'var(--color-warning_dark, #a05e03)'}}>
                    {t('contentOptions.noTypesUnderRoot')}
                </Typography>
            )}
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
                    <div style={{display: 'flex', gap: '0.5rem', alignItems: 'flex-start'}}>
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
                    </div>
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
