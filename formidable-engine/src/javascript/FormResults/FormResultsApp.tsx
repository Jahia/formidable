import React, {useCallback, useEffect, useState, useMemo} from 'react';
import {useQuery} from '@apollo/client';
import {Button, DeletePermanently, Download, Loader, Reload, Typography} from '@jahia/moonstone';
import {useTranslation} from 'react-i18next';
import {GET_FORM_RESULTS_LIST, GET_FORM_FIELD_LABELS, GET_FORMS_IN_EDIT} from './graphql';
import {DeleteResultsDialog} from './delete';
import {ExportResultsDialog} from './export';
import {FormResultsList, FormStatusChip, SubmissionDetailPanel, SubmissionsTable} from './components';
import type {FormResultsNode, SubmissionRow} from './FormResults.utils';
import {
    buildFormsInEditQuery,
    currentSiteKey,
    EMPTY_FORM_FIELDS,
    formResultsLabel,
    formStatus,
    isMissingInLive,
    parseFormFields,
    uiContext,
    withEditForms
} from './FormResults.utils';

export const FormResultsApp = () => {
    const {t} = useTranslation('formidable-engine');
    const siteKey = currentSiteKey();
    const language = uiContext().uilang || 'en';
    const resultsPath = `/sites/${siteKey}/formidable-results`;

    const [selectedFormResultsId, setSelectedFormResultsId] = useState<string | null>(null);
    const [selectedSubmission, setSelectedSubmission] = useState<SubmissionRow | null>(null);
    const [isExportDialogOpen, setIsExportDialogOpen] = useState(false);
    const [isDeleteDialogOpen, setIsDeleteDialogOpen] = useState(false);
    const [isRefreshing, setIsRefreshing] = useState(false);
    const [refreshSelectedForm, setRefreshSelectedForm] = useState<(() => Promise<unknown>) | null>(null);

    const {loading, error, data, refetch: refetchForms} = useQuery(GET_FORM_RESULTS_LIST, {
        variables: {resultsPath, workspace: 'LIVE', language},
        fetchPolicy: 'network-only',
        skip: !siteKey
    });

    const liveForms: FormResultsNode[] = useMemo(() => data?.jcr?.nodeByPath?.children?.nodes ?? [], [data]);
    // The entries whose form is not in live are told apart — unpublished or deleted — by one lookup in EDIT.
    const formsQuery = useMemo(
        () => buildFormsInEditQuery(liveForms.filter(isMissingInLive).map(form => form.parentForm?.value ?? '')),
        [liveForms]
    );
    const {data: editFormsData} = useQuery(GET_FORMS_IN_EDIT, {
        variables: {formsQuery: formsQuery!, language},
        skip: !formsQuery,
        fetchPolicy: 'network-only'
    });
    const forms = useMemo(
        () => withEditForms(liveForms, formsQuery ? editFormsData?.jcr?.nodesByQuery?.nodes : []),
        [liveForms, formsQuery, editFormsData]
    );
    const selectedForm = forms.find(f => f.uuid === selectedFormResultsId) ?? null;
    const selectedFormUuid = selectedForm?.uuid ?? null;
    const selectedFormLabel = selectedForm ? formResultsLabel(selectedForm) : '';
    // One Delete button for both deletions: the submissions of a range, or the whole entry. The
    // user needs the rights of both — administrative access today, the results-reader role carrying
    // none of them (docs/administration/results-permissions.md, "Deletion permissions").
    const canDeleteSelectedForm = Boolean(
        selectedForm?.canRemoveNode &&
        selectedForm?.submissionsContainer?.nodes?.[0]?.canRemoveNode &&
        selectedForm?.submissionsContainer?.nodes?.[0]?.canRemoveChildNodes
    );

    const formUuid = selectedForm?.parentForm?.refNode?.uuid;
    const {data: fieldLabelsData} = useQuery(GET_FORM_FIELD_LABELS, {
        variables: {formUuid: formUuid!, language, workspace: 'LIVE'},
        skip: !formUuid,
        fetchPolicy: 'cache-first'
    });
    const formFields = useMemo(() => (formUuid ? parseFormFields(fieldLabelsData) : EMPTY_FORM_FIELDS), [formUuid, fieldLabelsData]);

    useEffect(() => {
        // eslint-disable-next-line @eslint-react/hooks-extra/no-direct-set-state-in-use-effect -- deliberate reset when the selected form changes
        setSelectedSubmission(null);
    }, [selectedFormUuid]);

    useEffect(() => {
        // eslint-disable-next-line @eslint-react/hooks-extra/no-direct-set-state-in-use-effect -- deliberate reset when the selected form changes
        setIsExportDialogOpen(false);
        // eslint-disable-next-line @eslint-react/hooks-extra/no-direct-set-state-in-use-effect -- deliberate reset when the selected form changes
        setIsDeleteDialogOpen(false);
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

    const handleDeleteSuccess = async (entryRemoved: boolean) => {
        setSelectedSubmission(null);

        if (entryRemoved) {
            // The entry is gone: nothing to refresh under it, the list alone is re-read.
            setSelectedFormResultsId(null);
            await refetchForms();
            return;
        }

        if (!selectedForm || !refreshSelectedForm) {
            await refetchForms();
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
                <div style={{display: 'flex', justifyContent: 'center', alignItems: 'center', height: '100%', flexDirection: 'column', gap: '1rem', padding: '48px', textAlign: 'center'}}>
                    <Typography variant="heading" weight="bold">{t('formResults.empty.noForms')}</Typography>
                    <Typography>{t('formResults.empty.noFormsDescription')}</Typography>
                </div>
            );
        }

        return <Typography color="danger">{error.message}</Typography>;
    }

    if (forms.length === 0) {
        return (
            <div style={{display: 'flex', justifyContent: 'center', alignItems: 'center', height: '100%', flexDirection: 'column', gap: '1rem', padding: '48px', textAlign: 'center'}}>
                <Typography variant="heading" weight="bold">{t('formResults.empty.noForms')}</Typography>
                <Typography>{t('formResults.empty.noFormsDescription')}</Typography>
            </div>
        );
    }

    return (
        <div style={{display: 'flex', flexDirection: 'column', height: '100%', width: '100%', minWidth: 0, overflow: 'hidden'}}>
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
                    <div style={{display: 'flex', alignItems: 'center', gap: '8px', marginTop: '4px'}}>
                        <Typography variant="body" style={{color: 'var(--color-gray)'}}>
                            {selectedFormLabel}
                        </Typography>
                        {selectedForm && <FormStatusChip status={formStatus(selectedForm)}/>}
                    </div>
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
                    title={t('formResults.actions.exportTitle')}
                    isDisabled={!selectedForm}
                    onClick={() => setIsExportDialogOpen(true)}
                />
                {canDeleteSelectedForm && (
                    <Button
                        variant="ghost"
                        color="danger"
                        icon={<DeletePermanently/>}
                        label={t('formResults.actions.delete')}
                        title={t('formResults.actions.deleteTitle')}
                        data-sel-role="delete-results"
                        isDisabled={!selectedForm}
                        onClick={() => setIsDeleteDialogOpen(true)}
                    />
                )}
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
                    minWidth: 0,
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
                <div role="main" style={{display: 'flex', flex: '1 1 0', minWidth: 0, gap: '1px', overflow: 'hidden', backgroundColor: 'var(--color-gray_light40)'}}>
                    {selectedForm ? (
                        <div style={{flex: '1 1 0', minWidth: 0, overflow: 'hidden'}}>
                            <SubmissionsTable
                                formResults={selectedForm}
                                fieldOrder={formFields.order}
                                selectedSubmission={selectedSubmission}
                                onSelectSubmission={setSelectedSubmission}
                                onRegisterRefresh={handleRegisterRefresh}
                            />
                        </div>
                    ) : (
                        <div style={{flex: '1 1 0', minWidth: 0, display: 'flex', alignItems: 'center', justifyContent: 'center', backgroundColor: 'var(--color-light)'}}>
                            <Typography variant="heading">
                                {t('formResults.empty.selectForm')}
                            </Typography>
                        </div>
                    )}
                    {selectedSubmission && (
                        <SubmissionDetailPanel
                            submission={selectedSubmission}
                            formFields={formFields}
                            onClose={() => setSelectedSubmission(null)}
                        />
                    )}
                </div>
            </div>
            {selectedForm && isExportDialogOpen && (
                <ExportResultsDialog
                    formResults={selectedForm}
                    onClose={() => setIsExportDialogOpen(false)}
                />
            )}
            {selectedForm && canDeleteSelectedForm && isDeleteDialogOpen && (
                <DeleteResultsDialog
                    formResults={selectedForm}
                    onClose={() => setIsDeleteDialogOpen(false)}
                    onDeleted={handleDeleteSuccess}
                />
            )}
        </div>
    );
};
