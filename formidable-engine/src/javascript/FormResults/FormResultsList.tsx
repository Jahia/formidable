import React from 'react';
import {Typography} from '@jahia/moonstone';
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
        <aside style={{
            width: '280px',
            minWidth: '280px',
            borderRight: '1px solid var(--color-gray_light40)',
            overflowY: 'auto',
            backgroundColor: 'var(--color-light)',
            padding: '8px 0'
        }}>
            <div style={{padding: '12px 16px'}}>
                <Typography variant="subheading" weight="bold">
                    {t('formResults.sidebar.title')}
                </Typography>
            </div>
            {forms.map(form => {
                const isSelected = form.uuid === selectedId;
                const label = form.parentForm?.refNode?.displayName ?? form.displayName ?? form.name;

                return (
                    <button
                        key={form.uuid}
                        type="button"
                        onClick={() => onSelect(form.uuid)}
                        style={{
                            display: 'flex',
                            alignItems: 'center',
                            justifyContent: 'space-between',
                            width: '100%',
                            padding: '10px 16px',
                            border: 'none',
                            cursor: 'pointer',
                            textAlign: 'left',
                            backgroundColor: isSelected ? 'var(--color-blue10)' : 'transparent',
                            borderLeft: isSelected ? '3px solid var(--color-blue)' : '3px solid transparent'
                        }}
                    >
                        <Typography
                            variant="body"
                            weight={isSelected ? 'bold' : 'default'}
                            style={{overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap'}}
                        >
                            {label}
                        </Typography>
                    </button>
                );
            })}
        </aside>
    );
};

