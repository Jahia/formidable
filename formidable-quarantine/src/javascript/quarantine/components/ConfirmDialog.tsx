import React, {useEffect, useId, useRef} from 'react';
import {Button, Typography} from '@jahia/moonstone';
import {useTranslation} from 'react-i18next';

interface ConfirmDialogProps {
    operation: 'RELEASE' | 'REJECT';
    /** The form and the date of the submission, as its heading names it. */
    subject: string;
    busy: boolean;
    /** The message of a failure that kept the dialog open; null when there is none. */
    failure: string | null;
    onConfirm: () => void;
    onCancel: () => void;
}

/**
 * The confirm dialog of Release and Reject: a native modal dialog, named by its heading and described by its text.
 * Cancel takes the first focus and comes first, so that Enter never releases or rejects by accident. Escape cancels,
 * except while the request runs: the confirm button then keeps the focus, says what runs, and ignores a second press.
 */
export const ConfirmDialog = ({operation, subject, busy, failure, onConfirm, onCancel}: ConfirmDialogProps) => {
    const {t} = useTranslation('formidable-quarantine');
    const dialogRef = useRef<HTMLDialogElement | null>(null);
    const cancelRef = useRef<HTMLButtonElement | null>(null);
    const titleId = useId();
    const descriptionId = useId();
    const reject = operation === 'REJECT';

    useEffect(() => {
        const dialog = dialogRef.current;
        if (dialog && !dialog.open) {
            dialog.showModal();
        }

        cancelRef.current?.focus();
        return () => {
            // close before the unmount: the screen then moves the focus where it belongs
            if (dialog?.open) {
                dialog.close();
            }
        };
    }, []);

    useEffect(() => {
        // a failure hands the focus back to Cancel, whose button stays the safe choice
        if (failure) {
            cancelRef.current?.focus();
        }
    }, [failure]);

    const handleCancel = (event: React.SyntheticEvent<HTMLDialogElement>) => {
        event.preventDefault();
        if (!busy) {
            onCancel();
        }
    };

    return (
        <dialog
            ref={dialogRef}
            className="fmdbq-dialog"
            role="alertdialog"
            aria-labelledby={titleId}
            aria-describedby={descriptionId}
            data-sel-role={`quarantine-confirm-${operation.toLowerCase()}`}
            onCancel={handleCancel}
        >
            <Typography id={titleId} component="h2" variant="heading" weight="bold">
                {t(`quarantine.confirm.${operation}.title`)}
            </Typography>
            <p id={descriptionId}>{t(`quarantine.confirm.${operation}.description`, {subject})}</p>
            <div role="alert" className="fmdbq-alert" data-sel-role="quarantine-confirm-failure">{failure}</div>
            <div style={{display: 'flex', justifyContent: 'flex-end', gap: '8px', marginTop: '16px'}}>
                <Button
                    ref={cancelRef}
                    label={t('quarantine.confirm.cancel')}
                    variant="outlined"
                    aria-disabled={busy}
                    data-sel-role="quarantine-confirm-cancel"
                    onClick={() => {
                        if (!busy) {
                            onCancel();
                        }
                    }}
                />
                <Button
                    label={busy ? t(`quarantine.action.busy.${operation}`) : t(`quarantine.action.${operation}`)}
                    color={reject ? 'danger' : 'accent'}
                    className={reject ? 'fmdbq-reject' : undefined}
                    aria-disabled={busy}
                    data-sel-role="quarantine-confirm-ok"
                    onClick={() => {
                        if (!busy) {
                            onConfirm();
                        }
                    }}
                />
            </div>
        </dialog>
    );
};
