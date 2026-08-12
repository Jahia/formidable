import React, {useEffect, useState} from 'react';
import {useApolloClient, gql} from '@apollo/client';
import {useTranslation} from 'react-i18next';
import {Dropdown, Typography} from '@jahia/moonstone';

interface ValueConstraint {
    displayValue?: string;
    value?: {string?: string};
}

interface SourcedOptionsCmpProps {
    field: {
        readOnly?: boolean;
        valueConstraints?: ValueConstraint[];
    };
    id: string;
    value?: string;
    onChange: (value: string) => void;
    editorContext?: {
        path?: string;
        lang?: string;
        uilang?: string;
        nodeData?: {
            path?: string;
            primaryNodeType?: {name?: string};
        };
    };
}

interface PreviewState {
    status: 'idle' | 'loading' | 'ready' | 'error';
    options: Array<{value: string; label: string}>;
}

const PREVIEW_QUERY = gql`
    query formidableSourcedOptionsPreview(
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
                fieldNodeType: "fmdbmix:sourcedOptions",
                fieldName: "fmdb:optionsSourceKey",
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

export const SourcedOptionsCmp = ({field, id, value, onChange, editorContext}: SourcedOptionsCmpProps) => {
    const {t} = useTranslation('formidable-engine');
    const client = useApolloClient();
    const [preview, setPreview] = useState<PreviewState>({status: 'idle', options: []});

    const sources = (field.valueConstraints ?? [])
        .map(constraint => ({
            label: constraint.displayValue ?? constraint.value?.string ?? '',
            value: constraint.value?.string ?? ''
        }))
        .filter(source => source.value !== '');

    const nodePath = editorContext?.nodeData?.path;
    const parent = parentPathOf(nodePath) ?? editorContext?.path ?? '/sites';
    const primaryNodeType = editorContext?.nodeData?.primaryNodeType?.name ?? 'fmdb:select';
    const uiLocale = editorContext?.uilang
        ?? (window as unknown as {contextJsParameters?: {uilang?: string}}).contextJsParameters?.uilang
        ?? 'en';
    const locale = editorContext?.lang ?? uiLocale;

    useEffect(() => {
        if (!value) {
            // The render gates every preview block on a non-empty value: no reset needed.
            return;
        }

        let cancelled = false;
        // Fetch effect: the loading state has to be flagged when the query starts.
        // eslint-disable-next-line @eslint-react/hooks-extra/no-direct-set-state-in-use-effect
        setPreview({status: 'loading', options: []});
        client.query({
            query: PREVIEW_QUERY,
            fetchPolicy: 'network-only',
            variables: {
                parent,
                primaryNodeType,
                context: [{key: 'sourceKey', value: [value]}],
                uiLocale,
                locale
            }
        }).then(result => {
            if (cancelled) {
                return;
            }

            const constraints: ValueConstraint[] = result.data?.forms?.fieldConstraints ?? [];
            setPreview({
                status: 'ready',
                options: constraints.map(constraint => ({
                    value: constraint.value?.string ?? '',
                    label: constraint.displayValue ?? constraint.value?.string ?? ''
                }))
            });
        }).catch(() => {
            if (!cancelled) {
                setPreview({status: 'error', options: []});
            }
        });

        return () => {
            cancelled = true;
        };
    }, [client, value, parent, primaryNodeType, uiLocale, locale]);

    return (
        <div className="flexCol flexFluid" style={{gap: '0.5rem'}}>
            <Dropdown
                id={id}
                data={sources}
                value={value ?? ''}
                placeholder={t('sourcedOptions.selectSource')}
                isDisabled={field.readOnly}
                onChange={(_event: unknown, item: {value: string}) => onChange(item.value)}
            />

            {value && preview.status === 'loading' && (
                <Typography variant="caption">{t('sourcedOptions.previewLoading')}</Typography>
            )}
            {value && preview.status === 'error' && (
                <Typography variant="caption">{t('sourcedOptions.previewUnavailable')}</Typography>
            )}
            {value && preview.status === 'ready' && (
                <>
                    <Typography variant="caption">
                        {t('sourcedOptions.previewCount', {count: preview.options.length})}
                    </Typography>
                    {preview.options.length > 0 && (
                        <select
                            aria-label={t('sourcedOptions.previewLabel')}
                            style={{maxWidth: '100%'}}
                            value=""
                            onChange={() => { /* browse-only preview: nothing is stored */ }}
                        >
                            <option value="" disabled>{t('sourcedOptions.previewLabel')}</option>
                            {preview.options.map(option => (
                                <option key={option.value} value={option.value} disabled>
                                    {option.label}
                                </option>
                            ))}
                        </select>
                    )}
                </>
            )}
        </div>
    );
};
