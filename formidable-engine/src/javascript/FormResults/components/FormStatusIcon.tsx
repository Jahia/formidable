import React from 'react';
import {Form as FormIcon} from '@jahia/moonstone';
import {useTranslation} from 'react-i18next';
import type {FormStatus} from '../FormResults.utils';

interface FormStatusIconProps {
    status: FormStatus;
}

/**
 * The form icon of a list entry, coloured by the status of its form: red when the form is deleted,
 * orange when it is not published, plain otherwise. A list column has no room for words next to
 * the title, so the colour carries the status there; the words sit in the icon's tooltip and in a
 * visually hidden text next to it (an SVG icon has no alt and Sonar refuses an img role on a span),
 * and in the header's chip once the entry is selected. The wrapping span carries the test hook.
 */
const VISUALLY_HIDDEN: React.CSSProperties = {
    position: 'absolute', width: 1, height: 1, overflow: 'hidden', clip: 'rect(0 0 0 0)', whiteSpace: 'nowrap'
};
export const FormStatusIcon = ({status}: FormStatusIconProps) => {
    const {t} = useTranslation('formidable-engine');

    if (status === 'deleted' || status === 'unpublished' || status === 'imported') {
        const labels = {deleted: 'formResults.sidebar.formDeleted', unpublished: 'formResults.sidebar.formUnpublished', imported: 'formResults.sidebar.formImported'};
        const colors = {deleted: 'red', unpublished: 'yellow', imported: 'blue'} as const;
        const label = t(labels[status]);
        return (
            <span
                data-sel-role={`form-${status}`}
                title={label}
                style={{display: 'inline-flex', position: 'relative'}}
            >
                <FormIcon size="small" color={colors[status]}/>
                <span style={VISUALLY_HIDDEN}>{label}</span>
            </span>
        );
    }

    return <FormIcon size="small"/>;
};
