import React from 'react';
import {useTranslation} from 'react-i18next';
import {Input, Switch} from '@jahia/moonstone';
import {extractEditorContext, extractLanguage} from '../ConditionalLogic/ConditionalLogic.utils';
import type {SelectorProps} from '../ConditionalLogic/ConditionalLogic.types';

interface SelectOption {
    value: string;
    label: string;
    selected: boolean;
}

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
    const option = parseValue(value);

    // The option VALUE is the identity shared by every language (submissions,
    // conditional logic, forged-value validation): it is edited in the site's
    // default language, and the server re-aligns the other languages on it at
    // save. Outside that language an EXISTING value locks — it came from the
    // master and must not drift — while an empty one stays editable, so a field
    // authored only in a non-default language (no master to align on) remains
    // authorable. The default language comes synchronously from the editor
    // context (Content Editor resolves the site info before selectors mount).
    const defaultLanguage = extractEditorContext(props)?.siteInfo?.defaultLanguage;
    const language = extractLanguage(props);
    const valueShared = Boolean(defaultLanguage) && language !== defaultLanguage && option.value !== '';

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
