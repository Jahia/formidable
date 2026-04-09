import {useTranslation} from 'react-i18next';
import { Input, Checkbox } from "@jahia/moonstone";
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
        onChange(JSON.stringify({...option, ...patch}));
    };

    return (
      <div className="flexRow_nowrap flexFluid alignCenter" style={{ gap: "1rem" }}>
        <Checkbox
          id={`select-option-selected-${id}`}
          name={`select-option-selected-${id}`}
          title={t("selectOptions.selected")}
          checked={option.selected}
          disabled={field.readOnly}
          onChange={(e: React.ChangeEvent<HTMLInputElement>) =>
            handleChange({ selected: e.target.checked })
          }
        />
        <div className="flexFluid">
          <Input
            id={`select-option-value-${id}`}
            name={`select-option-value-${id}`}
            placeholder={t("selectOptions.value")}
            value={option.value}
            readOnly={field.readOnly}
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
            readOnly={field.readOnly}
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
