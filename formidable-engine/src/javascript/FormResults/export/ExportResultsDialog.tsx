import React, {useCallback, useMemo, useRef, useState} from 'react';
import {useApolloClient} from '@apollo/client';
import {Button, Checkbox, Close, Download, Dropdown, Input, Loader, Typography} from '@jahia/moonstone';
import {useTranslation} from 'react-i18next';
import {GET_SUBMISSIONS} from '../graphql';
import {buildSubmissionsQuery, parseSubmissionNode, type FormResultsNode, type SubmissionRow} from '../FormResults.utils';
import {buildFilename, downloadFile} from './export.utils';
import {exportFormats} from './formats';

interface ExportResultsDialogProps {
    formResults: FormResultsNode;
    onClose: () => void;
}

const EXPORT_BATCH_SIZE = 100;

export const ExportResultsDialog = ({formResults, onClose}: ExportResultsDialogProps) => {
    const client = useApolloClient();
    const {t} = useTranslation('formidable-engine');
    const dialogRef = useRef<HTMLDialogElement | null>(null);

    const [startDate, setStartDate] = useState('');
    const [endDate, setEndDate] = useState('');
    const [allResults, setAllResults] = useState(false);
    const [isExporting, setIsExporting] = useState(false);
    const [errorMessage, setErrorMessage] = useState('');
    const [selectedFormatIds, setSelectedFormatIds] = useState<string[]>([exportFormats[0].id]);

    const handleFormatChange = (_event: React.MouseEvent, item: {value?: string}) => {
        if (!item.value) {
            return;
        }

        setSelectedFormatIds(prev =>
            prev.includes(item.value!) ?
                (prev.length > 1 ? prev.filter(id => id !== item.value) : prev) :
                [...prev, item.value!]
        );
    };

    const filters = useMemo(() => ({
        startDate: allResults ? undefined : startDate,
        endDate: allResults ? undefined : endDate
    }), [allResults, startDate, endDate]);

    const handleBackdropClick = useCallback((e: React.MouseEvent) => {
        if (e.target === dialogRef.current && !isExporting) {
            onClose();
        }
    }, [isExporting, onClose]);

    const handleExport = async () => {
        if (!allResults && (startDate === '' || endDate === '' || startDate > endDate)) {
            setErrorMessage(t('formResults.export.validation.invalidRange'));
            return;
        }

        setErrorMessage('');
        setIsExporting(true);

        try {
            const submissionsQuery = buildSubmissionsQuery(formResults.path, 'created', 'descending', filters);
            const submissions: SubmissionRow[] = [];
            let offset = 0;
            let totalCount = 0;

            do {
                const {data} = await client.query({
                    query: GET_SUBMISSIONS,
                    variables: {
                        submissionsQuery,
                        limit: EXPORT_BATCH_SIZE,
                        offset,
                        workspace: 'LIVE'
                    },
                    fetchPolicy: 'network-only'
                });

                const result = data?.jcr?.nodesByQuery;
                const batch = (result?.nodes ?? []).map(parseSubmissionNode);
                totalCount = result?.pageInfo?.totalCount ?? 0;

                submissions.push(...batch);
                offset += batch.length;

                if (batch.length === 0) {
                    break;
                }
            } while (offset < totalCount);

            for (const format of exportFormats.filter(f => selectedFormatIds.includes(f.id))) {
                const content = format.buildContent(submissions, t);
                const filename = buildFilename(formResults, {startDate, endDate, allResults}, format.extension);
                downloadFile(filename, content, format.mimeType);
            }

            onClose();
        } catch (error) {
            setErrorMessage(error instanceof Error ? error.message : t('formResults.export.validation.unexpectedError'));
        } finally {
            setIsExporting(false);
        }
    };

    return (
        <dialog
            ref={el => {
                dialogRef.current = el;
                if (el && !el.open) {
                    el.showModal();
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
                            {t('formResults.export.title')}
                        </Typography>
                        <Typography variant="body" style={{color: 'var(--color-gray)', marginTop: '4px'}}>
                            {t('formResults.export.description')}
                        </Typography>
                    </div>
                    <Button
                        variant="ghost"
                        size="big"
                        icon={<Close/>}
                        aria-label={t('formResults.detail.close')}
                        isDisabled={isExporting}
                        onClick={onClose}
                    />
                </div>

                <div style={{display: 'flex', flexDirection: 'column', gap: '16px', padding: '20px'}}>
                    <label style={{display: 'flex', flexDirection: 'column', gap: '8px'}}>
                        <Typography variant="body" weight="bold">
                            {t('formResults.export.fields.startDate')}
                        </Typography>
                        <Input
                            size="big"
                            type="date"
                            value={startDate}
                            isDisabled={allResults || isExporting}
                            onChange={event => setStartDate(event.target.value)}
                        />
                    </label>

                    <label style={{display: 'flex', flexDirection: 'column', gap: '8px'}}>
                        <Typography variant="body" weight="bold">
                            {t('formResults.export.fields.endDate')}
                        </Typography>
                        <Input
                            size="big"
                            type="date"
                            value={endDate}
                            isDisabled={allResults || isExporting}
                            onChange={event => setEndDate(event.target.value)}
                        />
                    </label>

                    <label style={{display: 'flex', alignItems: 'center', gap: '12px'}}>
                        <Checkbox
                            checked={allResults}
                            isDisabled={isExporting}
                            onChange={(_event, _value, checked) => setAllResults(checked)}
                        />
                        <Typography variant="body">
                            {t('formResults.export.fields.allResults')}
                        </Typography>
                    </label>

                    <div style={{display: 'flex', flexDirection: 'column', gap: '8px'}}>
                        <Typography variant="body" weight="bold">
                            {t('formResults.export.fields.format')}
                        </Typography>
                        <Dropdown
                            data={exportFormats.map(f => ({label: f.label, value: f.id}))}
                            values={selectedFormatIds}
                            isDisabled={isExporting}
                            onChange={handleFormatChange}
                        />
                    </div>

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
                    {isExporting && <Loader size="small"/>}
                    <Button
                        size="big"
                        variant="outlined"
                        label={t('formResults.export.cancel')}
                        isDisabled={isExporting}
                        onClick={onClose}
                    />
                    <Button
                        size="big"
                        color="accent"
                        icon={<Download/>}
                        label={t('formResults.export.confirm')}
                        isDisabled={selectedFormatIds.length === 0 || (!allResults && (startDate === '' || endDate === ''))}
                        isLoading={isExporting}
                        onClick={handleExport}
                    />
                </div>
            </div>
        </dialog>
    );
};

