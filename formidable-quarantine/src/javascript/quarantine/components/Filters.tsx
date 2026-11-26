import React, {useId} from 'react';
import {Check} from '@jahia/moonstone';
import {useTranslation} from 'react-i18next';
import type {FormEntry, StateCount} from '../graphql';
import {STATES, type QuarantineState} from '../quarantine.utils';

interface StateFilterProps {
    counts: StateCount[];
    selected: QuarantineState | null;
    onSelect: (state: QuarantineState | null) => void;
}

/**
 * The counts per state, which filter the table: native toggle buttons, one state at a time. Pressing the pressed
 * state clears the filter. A count of 0 stays a button, so the six are always there, in the same place.
 */
export const StateFilter = ({counts, selected, onSelect}: StateFilterProps) => {
    const {t} = useTranslation('formidable-quarantine');
    const labelId = useId();
    const countOf = (state: QuarantineState) => counts.find(count => count.state === state)?.count ?? 0;

    return (
        <div role="group" aria-labelledby={labelId} data-sel-role="quarantine-state-filter">
            <div id={labelId} className="fmdbq-secondary" style={{marginBottom: '4px'}}>{t('quarantine.filter.state')}</div>
            <div style={{display: 'flex', flexWrap: 'wrap', gap: '8px'}}>
                {STATES.map(state => {
                    const pressed = selected === state;
                    return (
                        <button
                            key={state}
                            type="button"
                            className="fmdbq-toggle"
                            aria-pressed={pressed}
                            data-state={state}
                            onClick={() => onSelect(pressed ? null : state)}
                        >
                            {pressed && <Check aria-hidden="true" size="small"/>}
                            <span>{t(`quarantine.state.${state}`)}</span>
                            <span className="fmdbq-count">{countOf(state)}</span>
                        </button>
                    );
                })}
            </div>
        </div>
    );
};

interface FormFilterProps {
    forms: FormEntry[];
    selected: string | null;
    onSelect: (formId: string | null) => void;
}

/** The form filter: a native select with its label, "All forms" first. */
export const FormFilter = ({forms, selected, onSelect}: FormFilterProps) => {
    const {t} = useTranslation('formidable-quarantine');
    const selectId = useId();
    // a selected form that left the list (its last submission purged) stays an option, so the select never lies
    const options = selected && !forms.some(form => form.formId === selected)
        ? [...forms, {formId: selected, formTitle: null, count: 0}]
        : forms;

    return (
        <div style={{display: 'flex', alignItems: 'center', gap: '8px'}}>
            <label htmlFor={selectId}>{t('quarantine.filter.form')}</label>
            <select
                id={selectId}
                data-sel-role="quarantine-form-filter"
                value={selected ?? ''}
                style={{minHeight: '32px', minWidth: '240px', font: 'inherit'}}
                onChange={event => onSelect(event.target.value || null)}
            >
                <option value="">{t('quarantine.filter.allForms')}</option>
                {options.map(form => (
                    <option key={form.formId} value={form.formId}>
                        {t('quarantine.filter.formOption', {title: form.formTitle ?? form.formId, count: form.count})}
                    </option>
                ))}
            </select>
        </div>
    );
};
