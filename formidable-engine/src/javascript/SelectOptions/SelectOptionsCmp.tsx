import React, {useEffect, useState} from 'react';
import {useTranslation} from 'react-i18next';
import {useApolloClient} from '@apollo/client';
import {gql} from '@apollo/client';
import {Input, Switch} from '@jahia/moonstone';
import {extractCurrentNodePath, extractLanguage} from '../ConditionalLogic/ConditionalLogic.utils';
import type {SelectorProps} from '../ConditionalLogic/ConditionalLogic.types';

interface SelectOption {
    value: string;
    label: string;
    selected: boolean;
}

const SITE_DEFAULT_LANGUAGE = gql`
    query siteDefaultLanguage($path: String!) {
        jcr {
            nodeByPath(path: $path) {
                uuid
                workspace
                property(name: "j:defaultLanguage") {
                    value
                }
            }
        }
    }
`;

// One lookup per site and session: every option row of every choice field asks
// for the same answer.
const defaultLanguageCache = new Map<string, Promise<string | null>>();

const fetchSiteDefaultLanguage = (
    client: ReturnType<typeof useApolloClient>,
    sitePath: string
): Promise<string | null> => {
    let cached = defaultLanguageCache.get(sitePath);
    if (!cached) {
        cached = client.query({query: SITE_DEFAULT_LANGUAGE, variables: {path: sitePath}})
            .then(result => (result.data?.jcr?.nodeByPath?.property?.value as string | undefined) ?? null)
            .catch(() => null);
        defaultLanguageCache.set(sitePath, cached);
    }

    return cached;
};

const parseValue = (value?: string): SelectOption => {
    try {
        const parsed = JSON.parse(value ?? '') as SelectOption;
        return {value: parsed.value ?? '', label: parsed.label ?? '', selected: parsed.selected === true};
    } catch {
        return {value: '', label: '', selected: false};
    }
};

export const SelectOptionsCmp = (props: SelectorProps) => {
    const {field, id, value, onChange} = props;
    const {t} = useTranslation('formidable-engine');
    const client = useApolloClient();
    const option = parseValue(value);

    // The option VALUE is the identity shared by every language (submissions,
    // conditional logic, forged-value validation): it is edited in the site's
    // default language only, and the server re-aligns the other languages on it
    // at save. Here the value input locks outside that language so contributors
    // are steered before the sync has to correct anything.
    const [defaultLanguage, setDefaultLanguage] = useState<string | null>(null);
    const language = extractLanguage(props);
    useEffect(() => {
        const nodePath = extractCurrentNodePath(props);
        const siteMatch = nodePath ? /^\/sites\/[^/]+/.exec(nodePath) : null;
        if (!siteMatch) {
            return;
        }

        let cancelled = false;
        fetchSiteDefaultLanguage(client, siteMatch[0]).then(lang => {
            if (!cancelled) {
                setDefaultLanguage(lang);
            }
        });
        return () => {
            cancelled = true;
        };
    }, []);

    const valueShared = Boolean(defaultLanguage) && language !== defaultLanguage;

    const handleChange = (patch: Partial<SelectOption>) => {
        const updated = {...option, ...patch};
        onChange(JSON.stringify(updated));
    };

    return (
        <div className="flexRow_nowrap flexFluid alignCenter" style={{gap: '1rem'}}>
            <Switch
                id={`select-option-selected-${id}`}
                name={`select-option-selected-${id}`}
                title={t('selectOptions.selected')}
                checked={option.selected}
                isDisabled={field.readOnly}
                onChange={(_event, _value, checked) =>
                    handleChange({selected: checked})}
            />
            <div className="flexFluid">
                <Input
                    id={`select-option-value-${id}`}
                    name={`select-option-value-${id}`}
                    placeholder={t('selectOptions.value')}
                    title={valueShared
                        ? t('selectOptions.valueShared', {lang: defaultLanguage})
                        : t('selectOptions.value')}
                    value={option.value}
                    isReadOnly={field.readOnly || valueShared}
                    onChange={(e: React.ChangeEvent<HTMLInputElement>) =>
                        handleChange({value: e.target.value})}
                    size="big"
                />
            </div>

            <div className="flexFluid">
                <Input
                    id={`select-option-label-${id}`}
                    name={`select-option-label-${id}`}
                    placeholder={t('selectOptions.label')}
                    title={t('selectOptions.label')}
                    value={option.label}
                    isReadOnly={field.readOnly}
                    onChange={(e: React.ChangeEvent<HTMLInputElement>) =>
                        handleChange({label: e.target.value})}
                    size="big"
                />
            </div>
        </div>
    );
};

SelectOptionsCmp.displayName = 'SelectOptionsCmp';
