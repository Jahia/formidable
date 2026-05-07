import React, {useCallback, useEffect, useState} from 'react';
import {useQuery} from '@apollo/client';
import {Button, Download, Loader, Paper, Reload, Typography} from '@jahia/moonstone';
import {useTranslation} from 'react-i18next';
import {GET_FORM_RESULTS_LIST} from './graphql';
import {ExportResultsDialog} from './export';
import {FormResultsList, SubmissionDetailPanel, SubmissionsTable} from './components';
import type {FormResultsNode, SubmissionRow} from './FormResults.utils';

export const FormResultsApp = () => {
    const {t} = useTranslation('formidable-engine');
    const siteKey = (window as any).contextJsParameters?.siteKey;
    const language = (window as any).contextJsParameters?.uilang || 'en';
    const resultsPath = `/sites/${siteKey}/formidable-results`;

    const [selectedFormResultsId, setSelectedFormResultsId] = useState<string | null>(null);
    const [selectedSubmission, setSelectedSubmission] = useState<SubmissionRow | null>(null);
    const [isExportDialogOpen, setIsExportDialogOpen] = useState(false);
    const [isRefreshing, setIsRefreshing] = useState(false);
    const [refreshSelectedForm, setRefreshSelectedForm] = useState<(() => Promise<unknown>) | null>(null);

    const {loading, error, data, refetch: refetchForms} = useQuery(GET_FORM_RESULTS_LIST, {
        variables: {resultsPath, workspace: 'LIVE', language},
        fetchPolicy: 'network-only',
        skip: !siteKey
    });

    const forms: FormResultsNode[] = data?.jcr?.nodeByPath?.children?.nodes ?? [];
    const selectedForm = forms.find(f => f.uuid === selectedFormResultsId) ?? null;
    const selectedFormUuid = selectedForm?.uuid ?? null;
    const selectedFormLabel = selectedForm
        ? selectedForm.parentForm?.refNode?.displayName ?? selectedForm.displayName ?? selectedForm.name
        : '';

    useEffect(() => {
        setSelectedSubmission(null);
    }, [selectedFormUuid]);

    useEffect(() => {
        setIsExportDialogOpen(false);
    }, [selectedFormUuid]);

    const handleRegisterRefresh = useCallback((refresh: (() => Promise<unknown>) | null) => {
        setRefreshSelectedForm(() => refresh);
    }, []);

    const handleRefresh = async () => {
        if (!selectedForm || !refreshSelectedForm) {
            return;
        }

        setIsRefreshing(true);
        try {
            await Promise.all([refetchForms(), refreshSelectedForm()]);
        } finally {
            setIsRefreshing(false);
        }
    };

    if (!siteKey) {
        return <Typography>{t('formResults.error.noSite')}</Typography>;
    }

    if (loading) {
        return (
            <div style={{display: 'flex', justifyContent: 'center', alignItems: 'center', height: '100%'}}>
                <Loader size="big"/>
            </div>
        );
    }

    if (error) {
        if (error.graphQLErrors?.some(e => e.message?.includes('javax.jcr.PathNotFoundException'))) {
            return (
                <div style={{display: 'flex', justifyContent: 'center', alignItems: 'center', height: '100%', flexDirection: 'column', gap: '1rem'}}>
                    <Typography variant="heading" weight="bold">{t('formResults.empty.noForms')}</Typography>
                    <Typography>{t('formResults.empty.noFormsDescription')}</Typography>
                </div>
            );
        }

        return <Typography color="danger">{error.message}</Typography>;
    }

    if (forms.length === 0) {
        return (
            <div style={{display: 'flex', justifyContent: 'center', alignItems: 'center', height: '100%', flexDirection: 'column', gap: '1rem'}}>
                <Typography variant="heading" weight="bold">{t('formResults.empty.noForms')}</Typography>
                <Typography>{t('formResults.empty.noFormsDescription')}</Typography>
            </div>
        );
    }

    return (
        <div style={{display: 'flex', flexDirection: 'column', height: '100%', overflow: 'hidden'}}>
            <div
                style={{
                    padding: '24px 24px 16px',
                    borderBottom: '1px solid var(--color-gray_light40)',
                    backgroundColor: 'var(--color-light)'
                }}
            >
                <Typography variant="heading" weight="bold">
                    {t('formResults.nav.title')}
                </Typography>
                {selectedFormLabel && (
                    <Typography variant="body" style={{marginTop: '4px', color: 'var(--color-gray)'}}>
                        {selectedFormLabel}
                    </Typography>
                )}
            </div>

            <div
                style={{
                    display: 'flex',
                    alignItems: 'center',
                    gap: '8px',
                    padding: '12px 24px',
                    borderBottom: '1px solid var(--color-gray_light40)',
                    backgroundColor: 'var(--color-light)'
                }}
            >
                <Button
                    variant="ghost"
                    icon={<Download/>}
                    label={t('formResults.actions.export')}
                    isDisabled={!selectedForm}
                    onClick={() => setIsExportDialogOpen(true)}
                />
                <Button
                    variant="ghost"
                    icon={<Reload/>}
                    label={t('formResults.actions.refresh')}
                    isDisabled={!selectedForm || !refreshSelectedForm}
                    isLoading={isRefreshing}
                    onClick={handleRefresh}
                />
            </div>

            <div
                style={{
                    display: 'flex',
                    flex: 1,
                    minHeight: 0,
                    overflow: 'hidden',
                    gap: '16px',
                    padding: '16px',
                    backgroundColor: 'var(--color-gray_light40)'
                }}
            >
                <FormResultsList
                    forms={forms}
                    selectedId={selectedForm?.uuid ?? ''}
                    onSelect={setSelectedFormResultsId}
                />
                <div style={{flex: 1, minWidth: 0, overflow: 'hidden'}}>
                    {selectedForm ? (
                        <SubmissionsTable
                            formResults={selectedForm}
                            selectedSubmission={selectedSubmission}
                            onSelectSubmission={setSelectedSubmission}
                            onRegisterRefresh={handleRegisterRefresh}
                        />
                    ) : (
                        <Paper
                            hasPadding
                            style={{
                                display: 'flex',
                                alignItems: 'center',
                                justifyContent: 'center',
                                height: '100%'
                            }}
                        >
                            <Typography variant="heading">
                                {t('formResults.empty.selectForm')}
                            </Typography>
                        </Paper>
                    )}
                </div>
                {selectedSubmission && (
                    <SubmissionDetailPanel
                        submission={selectedSubmission}
                        onClose={() => setSelectedSubmission(null)}
                    />
                )}
            </div>
            {selectedForm && isExportDialogOpen && (
                <ExportResultsDialog
                    formResults={selectedForm}
                    onClose={() => setIsExportDialogOpen(false)}
                />
            )}
        </div>
    );
};
