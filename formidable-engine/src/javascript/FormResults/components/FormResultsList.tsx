import React, {useCallback, useEffect, useRef} from 'react';
import {Badge, Paper, Typography} from '@jahia/moonstone';
import {useTranslation} from 'react-i18next';
import type {FormResultsNode} from '../FormResults.utils';
import {formResultsLabel, formStatus, nextEntryIndex} from '../FormResults.utils';
import {FormStatusIcon} from './FormStatusIcon';

interface FormResultsListProps {
    forms: FormResultsNode[];
    selectedId: string;
    onSelect: (id: string) => void;
    /** The right arrow hands the focus over, to the table. */
    onMoveRight?: () => void;
}

export const FormResultsList = ({forms, selectedId, onSelect, onMoveRight}: FormResultsListProps) => {
    const {t} = useTranslation('formidable-engine');
    const listRef = useRef<HTMLDivElement | null>(null);

    // Up and down move the selection, as they do in the table; right hands the focus to the table. On the
    // entries themselves, the native buttons, not on the list around them.
    const handleKeyDown = useCallback((event: React.KeyboardEvent) => {
        if (event.key === 'ArrowRight') {
            event.preventDefault();
            onMoveRight?.();
            return;
        }

        if (event.key !== 'ArrowUp' && event.key !== 'ArrowDown') {
            return;
        }

        event.preventDefault();
        if (forms.length === 0) {
            return;
        }

        const currentIndex = forms.findIndex(form => form.uuid === selectedId);
        onSelect(forms[nextEntryIndex(forms.length, currentIndex, event.key)].uuid);
    }, [forms, selectedId, onSelect, onMoveRight]);

    // The selected entry takes the focus when the selection moved from inside the list, so the
    // next arrow continues from it; a selection made elsewhere leaves the focus where it is.
    useEffect(() => {
        const list = listRef.current;
        if (!list || !selectedId || !list.contains(document.activeElement)) {
            return;
        }

        const selected = list.querySelector<HTMLElement>('[data-sel-role="form-results-entry"][aria-pressed="true"]');
        if (selected) {
            selected.focus({preventScroll: true});
            selected.scrollIntoView({block: 'nearest'});
        }
    }, [selectedId]);

    return (
        <aside
            style={{
                flex: '0 0 280px',
                overflow: 'hidden'
            }}
        >
            <Paper
                hasPadding={false}
                style={{
                    display: 'flex',
                    flexDirection: 'column',
                    height: '100%',
                    overflow: 'hidden',
                    borderRadius: '0'
                }}
            >
                <div
                    style={{
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'space-between',
                        gap: '8px',
                        minHeight: '48px',
                        padding: '0 16px',
                        borderBottom: '1px solid var(--color-gray_light40)'
                    }}
                >
                    <Typography variant="subheading" weight="bold">
                        {t('formResults.sidebar.title')}
                    </Typography>
                    <Badge label={String(forms.length)} color="accent"/>
                </div>

                <div
                    ref={listRef}
                    data-sel-role="form-results-list"
                    style={{padding: '8px', overflowY: 'auto'}}
                >
                {forms.map(form => {
                    const isSelected = form.uuid === selectedId;
                    const label = formResultsLabel(form);

                    return (
                        <button
                            key={form.uuid}
                            type="button"
                            aria-pressed={isSelected}
                            data-sel-role="form-results-entry"
                            data-sel-name={form.name}
                            onClick={() => onSelect(form.uuid)}
                            onKeyDown={handleKeyDown}
                            style={{
                                width: '100%',
                                display: 'flex',
                                alignItems: 'center',
                                gap: '10px',
                                padding: '10px 12px',
                                border: 'none',
                                borderRadius: '0',
                                backgroundColor: isSelected ? 'var(--color-accent)' : 'transparent',
                                borderLeft: isSelected ? '3px solid var(--color-accent_dark)' : '3px solid transparent',
                                cursor: 'pointer',
                                textAlign: 'left',
                                color: isSelected ? 'var(--color-light)' : 'inherit'
                            }}
                        >
                            <FormStatusIcon status={formStatus(form)}/>
                            <Typography
                                variant="body"
                                weight={isSelected ? 'bold' : 'default'}
                                style={{
                                    overflow: 'hidden',
                                    textOverflow: 'ellipsis',
                                    whiteSpace: 'nowrap',
                                    color: isSelected ? 'var(--color-light)' : 'inherit'
                                }}
                            >
                                {label}
                            </Typography>
                        </button>
                    );
                })}
                </div>
            </Paper>
        </aside>
    );
};
