import React, {useEffect, useId, useRef} from 'react';
import {Button, ButtonGroup, Check, ChevronDown, Close, Menu, RadioGroup, RadioItem, Typography} from '@jahia/moonstone';
import {useTranslation} from 'react-i18next';
import type {FormEntry, StateCount} from '../graphql';
import {STATES, type QuarantineState} from '../quarantine.utils';

interface StateFilterProps {
    counts: StateCount[];
    selected: QuarantineState | null;
    onSelect: (state: QuarantineState | null) => void;
}

/**
 * The counts per state, which filter the table, one state at a time: pressing the pressed state clears the filter. A
 * count of 0 stays a button, so the six are always there. Moonstone Buttons with aria-pressed in a labelled
 * ButtonGroup: the ButtonToggle of Moonstone blurs itself on each click, which would drop the focus of a keyboard user.
 */
export const StateFilter = ({counts, selected, onSelect}: StateFilterProps) => {
    const {t} = useTranslation('formidable-quarantine');
    const labelId = useId();
    const countOf = (state: QuarantineState) => counts.find(count => count.state === state)?.count ?? 0;

    return (
        <div className="fmdbq-filter" data-sel-role="quarantine-state-filter">
            <Typography id={labelId} variant="caption" className="fmdbq-secondary">{t('quarantine.filter.state')}</Typography>
            <ButtonGroup aria-labelledby={labelId} variant="outlined" className="fmdbq-state-filter">
                {STATES.map(state => {
                    const pressed = selected === state;
                    return (
                        <Button
                            key={state}
                            label={`${t(`quarantine.state.${state}`)} ${countOf(state)}`}
                            icon={pressed ? <Check aria-hidden="true"/> : undefined}
                            aria-pressed={pressed}
                            data-state={state}
                            onClick={() => onSelect(pressed ? null : state)}
                        />
                    );
                })}
            </ButtonGroup>
        </div>
    );
};

interface FormFilterProps {
    forms: FormEntry[];
    selected: string | null;
    isOpen: boolean;
    onOpen: () => void;
    onClose: () => void;
    onSelect: (formId: string | null) => void;
}

const ALL_FORMS = '';

/**
 * The form filter as a filter chip, the pattern of module-management-community: a dashed chip while no form is
 * chosen, a filled chip with a clear button once one is. The chip opens a Moonstone Menu used as a dialog, which holds
 * a radio group of the forms: native radios, named by their text, with the arrow keys of a radio group. Escape and
 * Close close it and give the focus back to the chip. The Dropdown of Moonstone is named by its value and takes no
 * arrow key in its menu, so it is not used.
 */
export const FormFilter = ({forms, selected, isOpen, onOpen, onClose, onSelect}: FormFilterProps) => {
    const {t} = useTranslation('formidable-quarantine');
    const anchorRef = useRef<HTMLSpanElement | null>(null);
    const editorRef = useRef<HTMLDivElement | null>(null);
    const groupLabelId = useId();
    const radioId = useId();
    const name = t('quarantine.filter.form');
    const current = forms.find(form => form.formId === selected);
    const value = selected ? current?.formTitle ?? selected : '';
    // a selected form that left the list (its last submission purged) stays a choice, so the filter never lies
    const options = selected && !current ? [...forms, {formId: selected, formTitle: null, count: 0}] : forms;

    // Moonstone Button takes no ref, and a value swaps the Button for a ButtonGroup: the focus moves after the render
    const focusChip = () => setTimeout(() => anchorRef.current?.querySelector('button')?.focus());

    const close = () => {
        onClose();
        focusChip();
    };

    useEffect(() => {
        if (!isOpen) {
            return undefined;
        }

        const frame = requestAnimationFrame(() => {
            editorRef.current?.querySelector<HTMLInputElement>('input:checked, input')?.focus({preventScroll: true});
        });
        // the Menu of Moonstone does not close on Escape
        const listener = (event: KeyboardEvent) => {
            if (event.key === 'Escape') {
                onClose();
                focusChip();
            }
        };

        document.addEventListener('keydown', listener);
        return () => {
            cancelAnimationFrame(frame);
            document.removeEventListener('keydown', listener);
        };
    }, [isOpen, onClose]);

    const editButton = (
        <Button
            variant="outlined"
            className={value ? 'fmdbq-chip-filled' : 'fmdbq-chip-empty'}
            label={value ? `${name}: ${value}` : name}
            iconEnd={<ChevronDown aria-hidden="true"/>}
            aria-haspopup="dialog"
            aria-expanded={isOpen}
            data-sel-role="quarantine-form-filter"
            onClick={() => (isOpen ? close() : onOpen())}
        />
    );

    return (
        <div className="fmdbq-filter">
            <Typography variant="caption" className="fmdbq-secondary" aria-hidden="true">{name}</Typography>
            <span ref={anchorRef} className="fmdbq-chip">
                {value ? (
                    <ButtonGroup variant="outlined" color="accent">
                        {editButton}
                        <Button
                            icon={<Close aria-hidden="true"/>}
                            aria-label={t('quarantine.filter.clearForm')}
                            data-sel-role="quarantine-form-filter-clear"
                            onClick={() => {
                                onSelect(null);
                                focusChip();
                            }}
                        />
                    </ButtonGroup>
                ) : editButton}
            </span>
            {isOpen && (
                <Menu
                    isDisplayed
                    hasSearch={false}
                    anchorEl={anchorRef as React.MutableRefObject<HTMLElement>}
                    // the Menu gives the position and the outside click; its list role is replaced, the editor is a form
                    {...{role: 'dialog', 'aria-label': name}}
                    onClose={close}
                >
                    <div ref={editorRef} className="fmdbq-filter-editor" data-sel-role="quarantine-form-editor">
                        <span id={groupLabelId} className="fmdbq-sr-only">{name}</span>
                        <RadioGroup
                            name={radioId}
                            role="radiogroup"
                            aria-labelledby={groupLabelId}
                            value={selected ?? ALL_FORMS}
                            onChange={(_event, formId) => onSelect(formId || null)}
                        >
                            {[{formId: ALL_FORMS, label: t('quarantine.filter.allForms')},
                                ...options.map(form => ({
                                    formId: form.formId,
                                    label: t('quarantine.filter.formOption', {title: form.formTitle ?? form.formId, count: form.count})
                                }))].map((option, index) => (
                                <RadioItem key={option.formId || 'all'} id={`${radioId}-${index}`} label={option.label} value={option.formId}/>
                            ))}
                        </RadioGroup>
                        <div className="fmdbq-filter-footer">
                            <Button variant="ghost" label={t('quarantine.filter.close')} data-sel-role="quarantine-form-editor-close" onClick={close}/>
                        </div>
                    </div>
                </Menu>
            )}
        </div>
    );
};
