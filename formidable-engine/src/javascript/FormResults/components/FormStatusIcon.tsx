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
 * the title, so the colour carries the status there; the words sit in the icon's tooltip and its
 * accessible name, and in the header's chip once the entry is selected. The wrapping span carries
 * the test hook.
 */
export const FormStatusIcon = ({status}: FormStatusIconProps) => {
    const {t} = useTranslation('formidable-engine');

    if (status === 'deleted' || status === 'unpublished') {
        const label = t(status === 'deleted' ? 'formResults.sidebar.formDeleted' : 'formResults.sidebar.formUnpublished');
        return (
            <span
                data-sel-role={status === 'deleted' ? 'form-deleted' : 'form-unpublished'}
                role="img"
                aria-label={label}
                title={label}
                style={{display: 'inline-flex'}}
            >
                <FormIcon size="small" color={status === 'deleted' ? 'red' : 'yellow'}/>
            </span>
        );
    }

    return <FormIcon size="small"/>;
};
