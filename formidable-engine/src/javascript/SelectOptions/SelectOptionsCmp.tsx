import React from 'react';
import {useTranslation} from 'react-i18next';
import {Input, Switch, Typography} from '@jahia/moonstone';
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

    // Options are AUTHORED in the site's default language: the value is the
    // identity shared by every language (submissions, conditional logic,
    // forged-value validation) and the default selection travels with it. In
    // any other language a master-fed row locks its value and selection (only
    // the label translates) and a row WITHOUT a value — nothing authored in
    // the default language yet, or a freshly added row — shows a plain pointer
    // to the default language instead of inputs. The row structure keeps the
    // Content Editor's standard controls: a structural change made here is
    // simply reverted by the save-time re-alignment, which the tooltips
    // explain. The default language comes synchronously from the editor
    // context.
    const defaultLanguage = extractEditorContext(props)?.siteInfo?.defaultLanguage;
    const language = extractLanguage(props);
    const otherLanguage = Boolean(defaultLanguage) && language !== defaultLanguage;
    const optionsShared = otherLanguage && option.value !== '';
    const contributeInMain = otherLanguage && option.value === '';

    const handleChange = (patch: Partial<SelectOption>) => {
        const updated = {...option, ...patch};
        onChange(JSON.stringify(updated));
    };

    if (contributeInMain) {
        return (
            <div className="flexRow_nowrap flexFluid alignCenter" style={{gap: '1rem'}}>
                <Typography variant="body">
                    {t('selectOptions.contributeInMain', {lang: defaultLanguage})}
                </Typography>
            </div>
        );
    }

    return (
        <div className="flexRow_nowrap flexFluid alignCenter" style={{gap: '1rem'}}>
            <Switch
                id={`select-option-selected-${id}`}
                name={`select-option-selected-${id}`}
                title={optionsShared
                    ? t('selectOptions.valueShared', {lang: defaultLanguage})
                    : t('selectOptions.selected')}
                checked={option.selected}
                isDisabled={field.readOnly || optionsShared}
                onChange={(_event, _value, checked) =>
                    handleChange({selected: checked})}
            />
            <div className="flexFluid">
                <Input
                    id={`select-option-value-${id}`}
                    name={`select-option-value-${id}`}
                    placeholder={t('selectOptions.value')}
                    title={optionsShared
                        ? t('selectOptions.valueShared', {lang: defaultLanguage})
                        : t('selectOptions.value')}
                    value={option.value}
                    isReadOnly={field.readOnly || optionsShared}
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
