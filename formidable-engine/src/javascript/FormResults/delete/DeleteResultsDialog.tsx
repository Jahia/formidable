import React, {useCallback, useMemo, useRef, useState} from 'react';
import {useMutation, useQuery} from '@apollo/client';
import {Button, Checkbox, Close, DeletePermanently, Input, Typography, Warning} from '@jahia/moonstone';
import {useTranslation} from 'react-i18next';
import {buildCountQuery, formResultsLabel, formStatus, type FormResultsNode, type FormStatus} from '../FormResults.utils';
import {DELETE_FORM_RESULTS, DELETE_SUBMISSIONS, GET_SUBMISSION_COUNT} from '../graphql';

/**
 * The count line of a whole-entry deletion says what goes with the submissions: the form leaves
 * the page until its next submission; until it is published again when it is unpublished; for
 * good when it no longer exists. A form not told apart yet reads like a published one.
 */
const ENTRY_COUNT_LABEL: Record<FormStatus, string> = {
    published: 'formResults.delete.count.all',
    unknown: 'formResults.delete.count.all',
    unpublished: 'formResults.delete.count.allUnpublished',
    deleted: 'formResults.delete.count.allDeleted'
};

interface DeleteResultsDialogProps {
    formResults: FormResultsNode;
    onClose: () => void;
    /** Called once the deletion went through; `entryRemoved` says the form's entry itself is gone from the page. */
    onDeleted: (entryRemoved: boolean) => Promise<void> | void;
}

/**
 * Deletes a form's submissions over a date range, or — "Delete all results" — the form's whole
 * entry: the fmdb:formResults node goes with its submissions, and the form leaves the page until
 * its next submission recreates the node. The entry removal is what clears an entry that is
 * already empty, or whose form was deleted in jContent (the results are never destroyed with
 * the form); both cases cannot be reached by the range deletion, which needs a submission to match.
 */
export const DeleteResultsDialog = ({formResults, onClose, onDeleted}: DeleteResultsDialogProps) => {
    const {t} = useTranslation('formidable-engine');
    const dialogRef = useRef<HTMLDialogElement | null>(null);
    const confirmationTarget = formResultsLabel(formResults);

    const [startDate, setStartDate] = useState('');
    const [endDate, setEndDate] = useState('');
    const [allResults, setAllResults] = useState(false);
    const [confirmationText, setConfirmationText] = useState('');
    const [isDeleting, setIsDeleting] = useState(false);
    const [errorMessage, setErrorMessage] = useState('');

    const [deleteSubmissions] = useMutation(DELETE_SUBMISSIONS);
    const [deleteFormResults] = useMutation(DELETE_FORM_RESULTS);

    const filters = useMemo(() => ({
        startDate: allResults ? undefined : startDate,
        endDate: allResults ? undefined : endDate
    }), [allResults, startDate, endDate]);

    const isRangeComplete = allResults || (startDate !== '' && endDate !== '');
    const hasValidRange = allResults || (startDate !== '' && endDate !== '' && startDate <= endDate);
    const hasDeleteAllConfirmation = !allResults || confirmationText === confirmationTarget;
    // A range must match a submission; the whole entry goes whatever it holds, zero included.
    const needsMatchingSubmissions = !allResults;

    const countQuery = useMemo(
        () => buildCountQuery(formResults.path, filters),
        [formResults.path, filters]
    );

    const {data: countData, loading: isCounting} = useQuery(GET_SUBMISSION_COUNT, {
        variables: {countQuery, workspace: 'LIVE'},
        fetchPolicy: 'network-only',
        skip: !hasValidRange
    });

    const submissionCount = countData?.jcr?.nodesByQuery?.pageInfo?.totalCount ?? 0;
    // A range takes submissions only; the whole entry takes the form off the page too.
    const countLabelKey = allResults ? ENTRY_COUNT_LABEL[formStatus(formResults)] : 'formResults.delete.count.label';

    // Escape closes a modal <dialog> natively, which would leave the React state open behind a
    // closed element; the cancel event becomes the same close as the buttons (not while deleting).
    const handleCancel = useCallback((event: React.SyntheticEvent<HTMLDialogElement>) => {
        event.preventDefault();
        if (!isDeleting) {
            onClose();
        }
    }, [isDeleting, onClose]);

    const handleDelete = async () => {
        if (!hasValidRange) {
            setErrorMessage(t('formResults.delete.validation.invalidRange'));
            return;
        }

        if (needsMatchingSubmissions && !isCounting && submissionCount === 0) {
            setErrorMessage(t('formResults.delete.validation.noResults'));
            return;
        }

        if (!hasDeleteAllConfirmation) {
            setErrorMessage(t('formResults.delete.validation.confirmationMismatch'));
            return;
        }

        setErrorMessage('');
        setIsDeleting(true);

        try {
            if (allResults) {
                await deleteFormResults({
                    variables: {
                        pathOrId: formResults.uuid,
                        workspace: 'LIVE'
                    }
                });
            } else {
                await deleteSubmissions({
                    variables: {
                        submissionsQuery: countQuery,
                        workspace: 'LIVE'
                    }
                });
            }

            await onDeleted(allResults);
            onClose();
        } catch (error) {
            setErrorMessage(error instanceof Error ? error.message : t('formResults.delete.validation.unexpectedError'));
        } finally {
            setIsDeleting(false);
        }
    };

    return (
        <dialog
            data-sel-role="delete-results-dialog"
            ref={element => {
                dialogRef.current = element;
                if (element && !element.open) {
                    element.showModal();
                }
            }}
            onCancel={handleCancel}
            style={{
                border: 'none',
                // borderRadius: '8px',
                padding: 0,
                width: '560px',
                maxWidth: 'calc(100vw - 32px)',
                backgroundColor: 'var(--color-light)',
                boxShadow: '0 8px 32px rgba(0, 0, 0, 0.3)'
            }}
        >
            <div style={{display: 'flex', flexDirection: 'column'}}>
                <div style={{
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'space-between',
                    gap: '16px',
                    padding: '16px 20px',
                    borderBottom: '1px solid var(--color-gray_light40)'
                }}>
                    <div>
                        <Typography variant="heading" weight="bold">
                            {t('formResults.delete.title')}
                        </Typography>
                        <Typography variant="body" style={{color: 'var(--color-gray)', marginTop: '4px'}}>
                            {t('formResults.delete.description')}
                        </Typography>
                    </div>
                    <Button
                        variant="ghost"
                        size="big"
                        icon={<Close/>}
                        aria-label={t('formResults.detail.close')}
                        isDisabled={isDeleting}
                        onClick={onClose}
                    />
                </div>

                <div style={{display: 'flex', flexDirection: 'column', gap: '16px', padding: '20px'}}>
                    <div style={{
                        display: 'flex',
                        alignItems: 'flex-start',
                        gap: '12px',
                        padding: '12px 14px',
                        // borderRadius: '6px',
                        // A Moonstone token: the former --color-warning_light40 does not exist, the box was white.
                        backgroundColor: 'var(--color-warning_plain20)'
                    }}>
                        <Warning color="yellow"/>
                        <Typography variant="body">
                            {t('formResults.delete.warning')}
                        </Typography>
                    </div>

                    <label style={{display: 'flex', flexDirection: 'column', gap: '8px'}}>
                        <Typography variant="body" weight="bold">
                            {t('formResults.delete.fields.startDate')}
                        </Typography>
                        <Input
                            size="big"
                            type="date"
                            value={startDate}
                            isDisabled={allResults || isDeleting}
                            onChange={event => setStartDate(event.target.value)}
                        />
                    </label>

                    <label style={{display: 'flex', flexDirection: 'column', gap: '8px'}}>
                        <Typography variant="body" weight="bold">
                            {t('formResults.delete.fields.endDate')}
                        </Typography>
                        <Input
                            size="big"
                            type="date"
                            value={endDate}
                            isDisabled={allResults || isDeleting}
                            onChange={event => setEndDate(event.target.value)}
                        />
                    </label>

                    <label style={{display: 'flex', alignItems: 'center', gap: '12px'}}>
                        <Checkbox
                            data-sel-role="delete-all-results"
                            checked={allResults}
                            isDisabled={isDeleting}
                            onChange={(_event, _value, checked) => {
                                setAllResults(checked);
                                setConfirmationText('');
                            }}
                        />
                        <Typography variant="body">
                            {t('formResults.delete.fields.allResults')}
                        </Typography>
                    </label>

                    {allResults && (
                        <label style={{display: 'flex', flexDirection: 'column', gap: '8px'}}>
                            <Typography variant="body" weight="bold">
                                {t('formResults.delete.fields.confirmationLabel')}
                            </Typography>
                            <Typography variant="body" style={{color: 'var(--color-gray)'}}>
                                {t('formResults.delete.fields.confirmationHelp', {name: confirmationTarget})}
                            </Typography>
                            <Input
                                data-sel-role="delete-results-confirmation"
                                size="big"
                                value={confirmationText}
                                isDisabled={isDeleting}
                                placeholder={confirmationTarget}
                                onChange={event => setConfirmationText(event.target.value)}
                            />
                        </label>
                    )}

                    {hasValidRange && (
                        <Typography variant="body" style={{color: 'var(--color-gray)'}} data-sel-role="delete-results-count">
                            {isCounting ? t('formResults.delete.count.loading') : t(countLabelKey, {count: submissionCount})}
                        </Typography>
                    )}

                    {errorMessage && (
                        <Typography variant="body" style={{color: 'var(--color-danger)'}}>
                            {errorMessage}
                        </Typography>
                    )}
                </div>

                <div style={{
                    display: 'flex',
                    justifyContent: 'flex-end',
                    gap: '8px',
                    padding: '0 20px 20px'
                }}>
                    <Button
                        size="big"
                        variant="outlined"
                        label={t('formResults.delete.cancel')}
                        isDisabled={isDeleting}
                        onClick={onClose}
                    />
                    <Button
                        size="big"
                        color="danger"
                        icon={<DeletePermanently/>}
                        label={t('formResults.delete.confirm')}
                        isDisabled={!isRangeComplete || !hasValidRange || !hasDeleteAllConfirmation || isCounting || (needsMatchingSubmissions && submissionCount === 0)}
                        data-sel-role="delete-results-confirm"
                        isLoading={isDeleting}
                        onClick={handleDelete}
                    />
                </div>
            </div>
        </dialog>
    );
};
