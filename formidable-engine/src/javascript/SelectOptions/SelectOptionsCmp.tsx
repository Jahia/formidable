import React from 'react';
import {useTranslation} from 'react-i18next';
import { Input, Switch } from "@jahia/moonstone";
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
    id:string;
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

export const SelectOptionsCmp = ({field,id, value, onChange}: SelectOptionsCmpProps) => {
    const {t} = useTranslation('formidable-engine');
    const option = parseValue(value);

    const handleChange = (patch: Partial<SelectOption>) => {
        const updated = {...option, ...patch};
        console.log('[SelectOptionsCmp] handleChange', {id, currentValue: value, option, patch, updated, json: JSON.stringify(updated)});
        onChange(JSON.stringify(updated));
    };

    return (
      <div className="flexRow_nowrap flexFluid alignCenter" style={{ gap: "1rem" }}>
        <Switch
          id={`select-option-selected-${id}`}
          name={`select-option-selected-${id}`}
          title={t("selectOptions.selected")}
          checked={option.selected}
          isDisabled={field.readOnly}
          onChange={(_event, _value, checked) =>
            handleChange({ selected: checked })
          }
        />
        <div className="flexFluid">
          <Input
            id={`select-option-value-${id}`}
            name={`select-option-value-${id}`}
            placeholder={t("selectOptions.value")}
            value={option.value}
            isReadOnly={field.readOnly}
            onChange={(e: React.ChangeEvent<HTMLInputElement>) =>
              handleChange({ value: e.target.value })
            }
            size="big"
          />
        </div>

        <div className="flexFluid">
          <Input
            id={`select-option-label-${id}`}
            name={`select-option-label-${id}`}
            placeholder={t("selectOptions.label")}
            value={option.label}
            isReadOnly={field.readOnly}
            onChange={(e: React.ChangeEvent<HTMLInputElement>) =>
              handleChange({ label: e.target.value })
            }
            size="big"
          />
        </div>
      </div>
    );
};

SelectOptionsCmp.displayName = 'SelectOptionsCmp';
