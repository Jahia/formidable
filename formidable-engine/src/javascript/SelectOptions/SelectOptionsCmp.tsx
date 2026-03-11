import React from 'react';
import {useTranslation} from 'react-i18next';
import {Checkbox, Input} from '@jahia/moonstone';

interface SelectOption {
    value: string;
    label: string;
    selected: boolean;
}

interface Field {
    readOnly?: boolean;
}

interface SelectOptionsCmpProps {
    field: Field;
    value?: string;
    onChange: (value: string) => void;
}

const parseValue = (value?: string): SelectOption => {
    try {
        const parsed = JSON.parse(value ?? '') as SelectOption;
        return {value: parsed.value ?? '', label: parsed.label ?? '', selected: parsed.selected === true};
    } catch {
        return {value: '', label: '', selected: false};
    }
};

export const SelectOptionsCmp = ({field, value, onChange}: SelectOptionsCmpProps) => {
    const {t} = useTranslation('formidable-engine');
    const option = parseValue(value);

    const handleChange = (patch: Partial<SelectOption>) => {
        onChange(JSON.stringify({...option, ...patch}));
    };

    return (
        <div>
            <Input
                id="select-option-value"
                label={t('selectOptions.value')}
                value={option.value}
                readOnly={field.readOnly}
                onChange={(e: React.ChangeEvent<HTMLInputElement>) => handleChange({value: e.target.value})}
            />
            <Input
                id="select-option-label"
                label={t('selectOptions.label')}
                value={option.label}
                readOnly={field.readOnly}
                onChange={(e: React.ChangeEvent<HTMLInputElement>) => handleChange({label: e.target.value})}
            />
            <Checkbox
                id="select-option-selected"
                label={t('selectOptions.selected')}
                checked={option.selected}
                readOnly={field.readOnly}
                onChange={(e: React.ChangeEvent<HTMLInputElement>) => handleChange({selected: e.target.checked})}
            />
        </div>
    );
};

SelectOptionsCmp.displayName = 'SelectOptionsCmp';
