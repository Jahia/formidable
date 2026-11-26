import React, {useEffect, useId, useRef} from 'react';
import {Banner, Button, Modal, ModalBody, ModalFooter, ModalHeader, Typography} from '@jahia/moonstone';
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

/** Focuses the Cancel button of the footer: the Moonstone Button takes no ref, so it is found by its role attribute. */
const focusCancel = (footer: HTMLElement | null) => {
    requestAnimationFrame(() => footer?.querySelector<HTMLButtonElement>('[data-sel-role="quarantine-confirm-cancel"]')?.focus());
};

/**
 * The confirm dialog of Release and Reject: a Moonstone Modal, an alert dialog named by its title and described by its
 * text. Cancel comes first and takes the first focus, so that Enter never releases or rejects by accident. Escape and
 * a press outside cancel, except while the request runs: the confirm button then says what runs and ignores a second
 * press. The Modal returns the focus on close; the screen then moves it where the operation left the page.
 */
export const ConfirmDialog = ({operation, subject, busy, failure, onConfirm, onCancel}: ConfirmDialogProps) => {
    const {t} = useTranslation('formidable-quarantine');
    const footerRef = useRef<HTMLDivElement | null>(null);
    const descriptionId = useId();
    const reject = operation === 'REJECT';
    const title = t(`quarantine.confirm.${operation}.title`);

    useEffect(() => {
        focusCancel(footerRef.current);
    }, []);

    useEffect(() => {
        // a failure hands the focus back to Cancel, which stays the safe choice
        if (failure) {
            focusCancel(footerRef.current);
        }
    }, [failure]);

    return (
        <Modal
            isOpen
            size="small"
            role="alertdialog"
            // the Modal names itself by its own id: the title names it instead
            aria-labelledby={undefined}
            aria-label={title}
            aria-describedby={descriptionId}
            data-sel-role={`quarantine-confirm-${operation.toLowerCase()}`}
            onOpenChange={open => {
                if (!open && !busy) {
                    onCancel();
                }
            }}
        >
            <>
                <ModalHeader title={title}/>
                <ModalBody>
                    <Typography id={descriptionId} variant="body">
                        {t(`quarantine.confirm.${operation}.description`, {subject})}
                    </Typography>
                    <div role="alert" data-sel-role="quarantine-confirm-failure">
                        {failure && <Banner role="note" variant="danger" title={t('quarantine.failedTitle')}>{failure}</Banner>}
                    </div>
                </ModalBody>
                <ModalFooter ref={footerRef} className="fmdbq-modal-footer">
                    <Button
                        size="big"
                        variant="outlined"
                        label={t('quarantine.confirm.cancel')}
                        aria-disabled={busy}
                        data-sel-role="quarantine-confirm-cancel"
                        onClick={() => {
                            if (!busy) {
                                onCancel();
                            }
                        }}
                    />
                    <Button
                        size="big"
                        color={reject ? 'danger' : 'accent'}
                        className={reject ? 'fmdbq-reject' : undefined}
                        label={busy ? t(`quarantine.action.busy.${operation}`) : t(`quarantine.action.${operation}`)}
                        aria-disabled={busy}
                        data-sel-role="quarantine-confirm-ok"
                        onClick={() => {
                            if (!busy) {
                                onConfirm();
                            }
                        }}
                    />
                </ModalFooter>
            </>
        </Modal>
    );
};
