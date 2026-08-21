import React, {useEffect, useRef} from 'react';
import {useTranslation} from 'react-i18next';
import {Input, Switch} from '@jahia/moonstone';
import {extractEditorContext, extractLanguage} from '../ConditionalLogic/ConditionalLogic.utils';
import type {SelectorProps} from '../ConditionalLogic/ConditionalLogic.types';
import './selectOptions.css';

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
    const rootRef = useRef<HTMLDivElement>(null);
    const option = parseValue(value);

    // Options are AUTHORED in the site's default language: the value is the
    // identity shared by every language (submissions, conditional logic,
    // forged-value validation) and the default selection travels with it, so
    // both lock outside that language, along with the row structure — the
    // save-time re-alignment would revert any change anyway. Creation is the
    // one exception: whatever language a field is created in, its entries seed
    // the default language at save, so nothing locks there (a locked mandatory
    // list would make the field unsavable). The default language and the mode
    // come synchronously from the editor context.
    const editorContext = extractEditorContext(props);
    const defaultLanguage = editorContext?.siteInfo?.defaultLanguage;
    const language = extractLanguage(props);
    const optionsShared = Boolean(defaultLanguage)
        && language !== defaultLanguage
        && editorContext?.mode !== 'create';

    useEffect(() => {
        if (!optionsShared) {
            return;
        }

        // The add/remove/drag controls belong to the Content Editor's multiple
        // field, outside this component's DOM: flag the field wrapper so the
        // stylesheet hides them. Add-only: rows of one field agree on the flag,
        // and a language switch remounts the whole form.
        rootRef.current
            ?.closest('[data-sel-content-editor-field]')
            ?.classList.add('fmdbOptionsStructureLocked');
    }, [optionsShared]);

    const handleChange = (patch: Partial<SelectOption>) => {
        const updated = {...option, ...patch};
        onChange(JSON.stringify(updated));
    };

    return (
        <div ref={rootRef} className="flexRow_nowrap flexFluid alignCenter" style={{gap: '1rem'}}>
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
