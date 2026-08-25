import React, {useEffect, useRef} from 'react';
import {useTranslation} from 'react-i18next';
import {Input, Switch, Typography} from '@jahia/moonstone';
import {extractContentLanguage, extractEditorContext} from '../ConditionalLogic/ConditionalLogic.utils';
import type {SelectorProps} from '../ConditionalLogic/ConditionalLogic.types';
import './selectOptions.css';

interface SelectOption {
    value: string;
    label: string;
    selected: boolean;
}

/**
 * The human-readable name of a language code, in the UI language ("anglais"
 * for 'en' in a French UI); falls back to the raw code when the runtime or
 * the code itself cannot be resolved.
 */
const STRUCTURE_LOCK_CLASS = 'fmdbOptionsStructureLocked';
// The lock is held on a wrapper shared by every row of the field, so it is
// reference-counted: one row unmounting must not unlock the rows still standing.
const STRUCTURE_LOCK_HOLDERS = 'data-fmdb-options-lock-holders';

const languageDisplayName = (code: string, uiLanguage: string): string => {
    try {
        return new Intl.DisplayNames([uiLanguage], {type: 'language'}).of(code) ?? code;
    } catch {
        return code;
    }
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
    const {t, i18n} = useTranslation('formidable-engine');
    const rootRef = useRef<HTMLDivElement>(null);
    const option = parseValue(value);

    // Options are AUTHORED in the site's default language: the value is the
    // identity shared by every language (submissions, conditional logic,
    // forged-value validation) and the default selection travels with it. In
    // any other language a master-fed row locks its value and selection (only
    // the label translates), the row structure hides (see below), and a row
    // WITHOUT a value — nothing synced from the default language yet — shows a
    // plain pointer to the default language instead of inputs. The default
    // language comes synchronously from the editor context.
    //
    // The component never calls onChange outside a user edit: Content Editor
    // derives its per-language dirtiness from the form values (useSwitchLanguage
    // stashes what differs when leaving a language), so a programmatic write
    // flags languages as edited on a mere language switch.
    //
    // Fail OPEN: locking the values takes positive proof that this is NOT the
    // default language. An unstated content language leaves the gate off, exactly
    // like an absent siteInfo — a wrong lock would tell a contributor to go author
    // the options in the very language they are already in.
    const defaultLanguage = extractEditorContext(props)?.siteInfo?.defaultLanguage;
    const language = extractContentLanguage(props);
    const otherLanguage = Boolean(defaultLanguage) && Boolean(language) && language !== defaultLanguage;
    const optionsShared = otherLanguage && option.value !== '';
    const contributeInMain = otherLanguage && option.value === '';
    const defaultLanguageName = defaultLanguage
        ? languageDisplayName(defaultLanguage, i18n.language)
        : defaultLanguage;

    useEffect(() => {
        // Outside the default language the row structure is not editable: an
        // added row could never receive a value, and removals/reorders would
        // only be reverted by the save-time re-alignment. The add/remove/drag controls
        // belong to the Content Editor's multiple field, outside this
        // component's DOM: flag the field wrapper so the stylesheet hides them.
        const wrapper = rootRef.current?.closest('[data-sel-content-editor-field]');
        if (!wrapper) {
            // The Content Editor markup this lock hangs on moved: say so rather
            // than silently leaving the structure controls open.
            console.warn('[Formidable] No Content Editor field wrapper to lock the options structure on');
            return;
        }

        if (!otherLanguage) {
            // Nothing to hold here — and nothing to release: the flag belongs to
            // whichever rows are locked, so a row that stops locking must not
            // clear it for them.
            return;
        }

        const holders = Number(wrapper.getAttribute(STRUCTURE_LOCK_HOLDERS) ?? '0') + 1;
        wrapper.setAttribute(STRUCTURE_LOCK_HOLDERS, String(holders));
        wrapper.classList.add(STRUCTURE_LOCK_CLASS);

        return () => {
            const left = Number(wrapper.getAttribute(STRUCTURE_LOCK_HOLDERS) ?? '1') - 1;
            if (left > 0) {
                wrapper.setAttribute(STRUCTURE_LOCK_HOLDERS, String(left));
                return;
            }

            // Last holder out: a language switch back to the default one runs every
            // row's cleanup before the new effects, so the flag follows the language
            // both ways.
            wrapper.removeAttribute(STRUCTURE_LOCK_HOLDERS);
            wrapper.classList.remove(STRUCTURE_LOCK_CLASS);
        };
    }, [otherLanguage]);

    const handleChange = (patch: Partial<SelectOption>) => {
        const updated = {...option, ...patch};
        onChange(JSON.stringify(updated));
    };

    if (contributeInMain) {
        return (
            <div ref={rootRef} className="flexRow_nowrap flexFluid alignCenter" style={{gap: '1rem'}}>
                <Typography variant="body">
                    {t('selectOptions.contributeInMain', {lang: defaultLanguageName})}
                </Typography>
            </div>
        );
    }

    return (
        <div ref={rootRef} className="flexRow_nowrap flexFluid alignCenter" style={{gap: '1rem'}}>
            <Switch
                id={`select-option-selected-${id}`}
                name={`select-option-selected-${id}`}
                title={optionsShared
                    ? t('selectOptions.valueShared', {lang: defaultLanguageName})
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
                        ? t('selectOptions.valueShared', {lang: defaultLanguageName})
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
