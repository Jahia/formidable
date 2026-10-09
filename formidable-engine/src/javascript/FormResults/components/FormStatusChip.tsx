import React from 'react';
import {Chip} from '@jahia/moonstone';
import {useTranslation} from 'react-i18next';
import type {FormStatus} from '../FormResults.utils';

interface FormStatusChipProps {
    status: FormStatus;
    style?: React.CSSProperties;
}

/**
 * Flags an entry whose form is not in live: "not published" (orange, like the list icon) when it still
 * stands in EDIT, "deleted" (red, like the list icon) when it is gone from both workspaces. Nothing for a published form, nor while the page has not
 * told the two apart yet. The wrapping span carries the test hook: the Chip does not forward it.
 */
export const FormStatusChip = ({status, style}: FormStatusChipProps) => {
    const {t} = useTranslation('formidable-engine');

    if (status === 'unpublished') {
        return (
            <span data-sel-role="form-unpublished" style={style}>
                <Chip label={t('formResults.sidebar.formUnpublished')} color="warning"/>
            </span>
        );
    }

    if (status === 'deleted') {
        return (
            <span data-sel-role="form-deleted" style={style}>
                <Chip label={t('formResults.sidebar.formDeleted')} color="danger"/>
            </span>
        );
    }

    if (status === 'imported') {
        return (
            <span data-sel-role="form-imported" style={style}>
                <Chip label={t('formResults.sidebar.formImported')} color="accent"/>
            </span>
        );
    }

    return null;
};
