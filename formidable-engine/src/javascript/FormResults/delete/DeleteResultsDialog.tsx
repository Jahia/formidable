import React, {useCallback, useMemo, useRef, useState} from 'react';
import {useMutation, useQuery} from '@apollo/client';
import {Button, Checkbox, Close, DeletePermanently, Input, Typography, Warning} from '@jahia/moonstone';
import {useTranslation} from 'react-i18next';
import {buildCountQuery, type FormResultsNode} from '../FormResults.utils';
import {DELETE_SUBMISSIONS, GET_SUBMISSION_COUNT} from '../graphql';

interface DeleteResultsDialogProps {
    formResults: FormResultsNode;
    onClose: () => void;
    onDeleted: () => Promise<void> | void;
}

export const DeleteResultsDialog = ({formResults, onClose, onDeleted}: DeleteResultsDialogProps) => {
    const {t} = useTranslation('formidable-engine');
    const dialogRef = useRef<HTMLDialogElement | null>(null);
    const confirmationTarget = formResults.parentForm?.refNode?.displayName ?? formResults.displayName ?? formResults.name;

    const [startDate, setStartDate] = useState('');
    const [endDate, setEndDate] = useState('');
    const [allResults, setAllResults] = useState(false);
    const [confirmationText, setConfirmationText] = useState('');
    const [isDeleting, setIsDeleting] = useState(false);
    const [errorMessage, setErrorMessage] = useState('');

    const [deleteSubmissions] = useMutation(DELETE_SUBMISSIONS);

    const filters = useMemo(() => ({
        startDate: allResults ? undefined : startDate,
        endDate: allResults ? undefined : endDate
    }), [allResults, startDate, endDate]);

    const isRangeComplete = allResults || (startDate !== '' && endDate !== '');
    const hasValidRange = allResults || (startDate !== '' && endDate !== '' && startDate <= endDate);
    const hasDeleteAllConfirmation = !allResults || confirmationText === confirmationTarget;

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

    const handleBackdropClick = useCallback((event: React.MouseEvent) => {
        if (event.target === dialogRef.current && !isDeleting) {
            onClose();
        }
    }, [isDeleting, onClose]);

    const handleDelete = async () => {
        if (!hasValidRange) {
            setErrorMessage(t('formResults.delete.validation.invalidRange'));
            return;
        }

        if (!isCounting && submissionCount === 0) {
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
            await deleteSubmissions({
                variables: {
                    submissionsQuery: countQuery,
                    workspace: 'LIVE'
                }
            });

            await onDeleted();
            onClose();
        } catch (error) {
            setErrorMessage(error instanceof Error ? error.message : t('formResults.delete.validation.unexpectedError'));
        } finally {
            setIsDeleting(false);
        }
    };

    return (
        <dialog
            ref={element => {
                dialogRef.current = element;
                if (element && !element.open) {
                    element.showModal();
                }
            }}
            onClick={handleBackdropClick}
            style={{
                border: 'none',
                borderRadius: '8px',
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
                        borderRadius: '6px',
                        backgroundColor: 'var(--color-warning_light40)'
                    }}>
                        <Warning/>
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
                                size="big"
                                value={confirmationText}
                                isDisabled={isDeleting}
                                placeholder={confirmationTarget}
                                onChange={event => setConfirmationText(event.target.value)}
                            />
                        </label>
                    )}

                    {hasValidRange && (
                        <Typography variant="body" style={{color: 'var(--color-gray)'}}>
                            {isCounting ? t('formResults.delete.count.loading') : t('formResults.delete.count.label', {count: submissionCount})}
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
                        isDisabled={!isRangeComplete || !hasValidRange || !hasDeleteAllConfirmation || isCounting || submissionCount === 0}
                        isLoading={isDeleting}
                        onClick={handleDelete}
                    />
                </div>
            </div>
        </dialog>
    );
};
