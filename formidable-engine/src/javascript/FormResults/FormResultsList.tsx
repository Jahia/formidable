import React from 'react';
import {Badge, Form as FormIcon, Paper, Typography} from '@jahia/moonstone';
import {useTranslation} from 'react-i18next';
import type {FormResultsNode} from './FormResults.utils';

interface FormResultsListProps {
    forms: FormResultsNode[];
    selectedId: string;
    onSelect: (id: string) => void;
}

export const FormResultsList = ({forms, selectedId, onSelect}: FormResultsListProps) => {
    const {t} = useTranslation('formidable-engine');

    return (
        <aside
            style={{
                width: '280px',
                minWidth: '280px',
                overflow: 'hidden'
            }}
        >
            <Paper
                hasPadding={false}
                style={{
                    display: 'flex',
                    flexDirection: 'column',
                    height: '100%',
                    overflow: 'hidden'
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

                <div style={{padding: '8px', overflowY: 'auto'}}>
                {forms.map(form => {
                    const isSelected = form.uuid === selectedId;
                    const label = form.parentForm?.refNode?.displayName ?? form.displayName ?? form.name;

                    return (
                        <button
                            key={form.uuid}
                            type="button"
                            onClick={() => onSelect(form.uuid)}
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
                            <FormIcon size="small"/>
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
