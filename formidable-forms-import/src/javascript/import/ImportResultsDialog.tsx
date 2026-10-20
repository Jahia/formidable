import React, {useCallback, useEffect, useMemo, useRef, useState} from 'react';
import {useQuery} from '@apollo/client';
import {Button, Check, Close, Download, Loader, Typography, Upload} from '@jahia/moonstone';
import {useTranslation} from 'react-i18next';
import {downloadFile, uiContext} from './page';
import {GET_SITE_FORMS} from './graphql';
import {ReportTable, rowOf} from './ImportReportTable';
import {reportMarkdown, reportFileName} from './reportMarkdown';
import {
    choicesReady,
    closeJob,
    fetchJob,
    type ImportChoice,
    type ImportJob,
    type ImportSettings,
    parseSiteDefaultLanguage,
    parseSiteForms,
    POLL_INTERVAL_MS,
    refuseFile,
    type SiteForm,
    siteFormsVariables,
    startImport,
    type TargetField,
    uploadExport
} from './import.utils';

interface ImportResultsDialogProps {
    siteKey: string;
    settings: ImportSettings;
    /** The job to open on, running or not yet closed, when the page found one. */
    initialJob?: ImportJob | null;
    /** Called when the dialog closes; true once an import ended, so that the list is re-read. */
    onClose: (imported: boolean) => void;
}

type DialogState = 'waiting' | 'analysing' | 'review' | 'importing' | 'done' | 'failed';

const stateOf = (job: ImportJob): DialogState => job.state;

/**
 * The import dialog of the Results page (docs/architecture/forms-import.md, "The dialog"): the export
 * dropped or chosen, a dry run and its report, then the import and its report. The job lives on the
 * server; the dialog polls it every second while it runs, and can be closed meanwhile: a dry run closed
 * is cancelled, an import closed goes on, and the dialog opened again shows it.
 */
export const ImportResultsDialog = ({siteKey, settings, initialJob, onClose}: ImportResultsDialogProps) => {
    const {t} = useTranslation('formidable-forms-import');
    const language = uiContext().uilang || 'en';
    const dialogRef = useRef<HTMLDialogElement | null>(null);
    const fileInputRef = useRef<HTMLInputElement | null>(null);
    const [state, setState] = useState<DialogState>(() => (initialJob ? stateOf(initialJob) : 'waiting'));
    const [job, setJob] = useState<ImportJob | null>(initialJob ?? null);
    const [fileName, setFileName] = useState('');
    const [errorMessage, setErrorMessage] = useState('');
    // What receives the results of each form of the export, chosen in the review; the results alone by default.
    const [choices, setChoices] = useState<Record<string, ImportChoice>>({});
    // The fields of the forms an attachment read so far, by identifier: held here so that a card remounted keeps its rows.
    const [targetFields, setTargetFields] = useState<Record<string, TargetField[]>>({});
    const rememberTargetFields = useCallback((formId: string, fields: TargetField[]) => {
        setTargetFields(previous => ({...previous, [formId]: fields}));
    }, []);
    const [isDragging, setIsDragging] = useState(false);
    const importedRef = useRef(initialJob?.state === 'done');
    // The forms of the site an attachment can pick: read once the review shows, fresh, a form may have been built for it.
    const {data: siteFormsData} = useQuery(GET_SITE_FORMS, {
        variables: siteFormsVariables(siteKey, language),
        skip: state !== 'review',
        fetchPolicy: 'network-only'
    });
    const siteForms = useMemo<SiteForm[] | null>(() => (siteFormsData ? parseSiteForms(siteFormsData) : null), [siteFormsData]);
    // the options of a choice field are read in the default language of the site, which always holds them
    const optionsLanguage = parseSiteDefaultLanguage(siteFormsData) ?? language;

    // The server job goes on without the dialog: while it runs, its state is read every second.
    useEffect(() => {
        if (!job || (state !== 'analysing' && state !== 'importing')) {
            return undefined;
        }
        const timer = setInterval(async () => {
            try {
                const refreshed = await fetchJob(siteKey, job.id);
                setJob(refreshed);
                setState(stateOf(refreshed));
                if (refreshed.state === 'done') {
                    importedRef.current = true;
                }
                if (refreshed.state === 'failed') {
                    setErrorMessage(refreshed.message ?? t('formResults.import.error.unexpected'));
                }
            } catch (error) {
                setErrorMessage(error instanceof Error ? error.message : t('formResults.import.error.unexpected'));
                setState('failed');
            }
        }, POLL_INTERVAL_MS);
        return () => clearInterval(timer);
    }, [job, state, siteKey, t]);

    // The node of a job that is not importing goes with the dialog; a running import keeps its node.
    const close = useCallback(async () => {
        if (job && state !== 'importing') {
            try {
                await closeJob(siteKey, job.id);
            } catch {
                // the node goes by itself after an hour
            }
        }
        onClose(importedRef.current);
    }, [job, state, siteKey, onClose]);

    // Escape closes a modal <dialog> natively: the cancel event becomes the same close as the buttons.
    const handleCancel = useCallback((event: React.SyntheticEvent<HTMLDialogElement>) => {
        event.preventDefault();
        close();
    }, [close]);

    const takeFile = async (file: File | undefined) => {
        if (!file) {
            return;
        }
        const refused = refuseFile(file, settings.maxFileSizeMb);
        if (refused) {
            setErrorMessage(t(`formResults.import.error.${refused}`, {max: settings.maxFileSizeMb}));
            return;
        }
        setErrorMessage('');
        setFileName(file.name);
        setState('analysing');
        try {
            const created = await uploadExport(siteKey, file);
            setJob(created);
            setState(stateOf(created));
        } catch (error) {
            setErrorMessage(error instanceof Error ? error.message : t('formResults.import.error.unexpected'));
            setState('failed');
        }
    };

    const handleImport = async () => {
        if (!job) {
            return;
        }
        setErrorMessage('');
        try {
            const started = await startImport(siteKey, job.id, choices);
            setJob(started);
            setState(stateOf(started));
        } catch (error) {
            // a refused start (another import runs on the site) keeps the review on screen, with the reason
            setErrorMessage(error instanceof Error ? error.message : t('formResults.import.error.unexpected'));
        }
    };

    // The failed job is closed on the server too, or the dialog would open on it again.
    const tryAgain = async () => {
        if (job) {
            try {
                await closeJob(siteKey, job.id);
            } catch {
                // the node goes by itself after an hour
            }
        }
        setJob(null);
        setFileName('');
        setErrorMessage('');
        // the choices went with the report they were made on
        setChoices({});
        setTargetFields({});
        setState('waiting');
    };

    // The report of the import as a Markdown file, built here from the report of the job: nothing more is stored on the server.
    const downloadReport = () => {
        if (!job?.report) {
            return;
        }
        const date = new Date();
        const rowOfForm = (form: Parameters<typeof rowOf>[0]) => rowOf(form, choices, siteForms, false, t);
        const content = reportMarkdown(job.report, {
            siteKey,
            fileName,
            language,
            date,
            t,
            outcomeOf: form => rowOfForm(form).text,
            notesApply: form => rowOfForm(form).outcome === 'created'
        });
        downloadFile(reportFileName(siteKey, date), content, 'text/markdown;charset=utf-8');
    };

    const handleFileChange = (event: React.ChangeEvent<HTMLInputElement>) => {
        const file = event.target.files?.[0];
        event.target.value = '';
        takeFile(file);
    };

    const handleDrop = (event: React.DragEvent<HTMLButtonElement>) => {
        event.preventDefault();
        setIsDragging(false);
        takeFile(event.dataTransfer.files?.[0]);
    };

    return (
        <dialog
            ref={el => {
                dialogRef.current = el;
                if (el && !el.open) {
                    el.showModal();
                }
            }}
            data-sel-role="import-results-dialog"
            data-sel-state={state}
            onCancel={handleCancel}
            style={{
                border: 'none',
                padding: 0,
                // a report needs room: the dialog widens once it shows one
                width: state === 'review' || state === 'done' ? '960px' : '640px',
                maxWidth: 'calc(100vw - 48px)',
                maxHeight: 'calc(100vh - 32px)',
                backgroundColor: 'var(--color-light)',
                boxShadow: '0 8px 32px rgba(0, 0, 0, 0.3)'
            }}
        >
            <div style={{display: 'flex', flexDirection: 'column', maxHeight: 'calc(100vh - 32px)'}}>
                <div style={{
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'space-between',
                    gap: '16px',
                    padding: '16px 20px',
                    borderBottom: '1px solid var(--color-gray_light40)'
                }}>
                    <div>
                        <Typography variant="heading" weight="bold">{t('formResults.import.title')}</Typography>
                        <Typography variant="body" style={{color: 'var(--color-gray)', marginTop: '4px'}}>
                            {t('formResults.import.description')}
                        </Typography>
                    </div>
                    <Button
                        variant="ghost"
                        size="big"
                        icon={<Close/>}
                        aria-label={t('formResults.detail.close')}
                        data-sel-role="import-close-x"
                        onClick={close}
                    />
                </div>
                <div style={{padding: '20px', overflow: 'auto', display: 'flex', flexDirection: 'column', gap: '16px'}}>
                    {state === 'waiting' && (
                        <>
                            {/* The whole zone is one button: a click or Enter opens the file picker, a drop takes the file */}
                            <button
                                type="button"
                                data-sel-role="import-dropzone"
                                style={{
                                    border: `2px dashed ${isDragging ? 'var(--color-accent)' : 'var(--color-gray_light)'}`,
                                    padding: '32px',
                                    textAlign: 'center',
                                    display: 'flex',
                                    flexDirection: 'column',
                                    alignItems: 'center',
                                    gap: '12px',
                                    width: '100%',
                                    background: 'transparent',
                                    font: 'inherit',
                                    color: 'inherit',
                                    cursor: 'pointer'
                                }}
                                onClick={() => fileInputRef.current?.click()}
                                onDragOver={event => {
                                    event.preventDefault();
                                    setIsDragging(true);
                                }}
                                onDragLeave={() => setIsDragging(false)}
                                onDrop={handleDrop}
                            >
                                <Upload size="big"/>
                                <Typography>{t('formResults.import.dropzone')}</Typography>
                                <Typography weight="bold" data-sel-role="import-choose-file">{t('formResults.import.chooseFile')}</Typography>
                            </button>
                            <input
                                ref={fileInputRef}
                                type="file"
                                accept=".zip,application/zip"
                                data-sel-role="import-file-input"
                                style={{display: 'none'}}
                                onChange={handleFileChange}
                            />
                            <Typography variant="caption" style={{color: 'var(--color-gray)'}}>
                                {t('formResults.import.fileHint', {max: settings.maxFileSizeMb})}
                            </Typography>
                        </>
                    )}
                    {(state === 'analysing' || state === 'importing') && (
                        <div style={{display: 'flex', alignItems: 'center', gap: '16px', padding: '24px 0'}}>
                            <Loader size="big"/>
                            <Typography data-sel-role="import-progress">
                                {state === 'analysing' ?
                                    t('formResults.import.analysing', {file: fileName}) :
                                    t('formResults.import.importing', {file: fileName})}
                            </Typography>
                        </div>
                    )}
                    {state === 'review' && job?.report && (
                        <>
                            <Typography variant="subheading" weight="bold">{t('formResults.import.review.title')}</Typography>
                            <ReportTable
                                report={job.report}
                                language={language}
                                t={t}
                                choices={choices}
                                siteForms={siteForms}
                                optionsLanguage={optionsLanguage}
                                targetFields={targetFields}
                                onTargetFields={rememberTargetFields}
                                onChoice={(sourceName, choice) => setChoices(previous => ({...previous, [sourceName]: choice}))}
                            />
                        </>
                    )}
                    {state === 'done' && job?.report && (
                        <>
                            <div style={{display: 'flex', alignItems: 'center', gap: '8px'}} data-sel-role="import-done">
                                <Check size="big" color="green"/>
                                <Typography variant="subheading" weight="bold">{t('formResults.import.done.title')}</Typography>
                            </div>
                            <ReportTable report={job.report} language={language} t={t}/>
                        </>
                    )}
                    {state === 'failed' && (
                        <div data-sel-role="import-failed">
                            <Typography weight="bold" color="danger">{t('formResults.import.failed.title')}</Typography>
                            <Typography color="danger">{errorMessage || job?.message}</Typography>
                        </div>
                    )}
                    {errorMessage && state !== 'failed' && (
                        <Typography color="danger" data-sel-role="import-error">{errorMessage}</Typography>
                    )}
                </div>
                <div style={{display: 'flex', justifyContent: 'flex-end', gap: '8px', padding: '0 20px 20px'}}>
                    {(state === 'done' || state === 'failed') && job?.report && !job.report.dryRun && (
                        <Button
                            size="big"
                            variant="ghost"
                            icon={<Download/>}
                            label={t('formResults.import.actions.downloadReport')}
                            data-sel-role="import-download-report"
                            style={{marginRight: 'auto'}}
                            onClick={downloadReport}
                        />
                    )}
                    {(state === 'waiting' || state === 'analysing') && (
                        <Button size="big" variant="outlined" label={t('formResults.import.actions.cancel')} data-sel-role="import-cancel" onClick={close}/>
                    )}
                    {state === 'review' && job?.report?.nothingToImport && (
                        <Button size="big" variant="outlined" label={t('formResults.import.actions.close')} data-sel-role="import-close" onClick={close}/>
                    )}
                    {state === 'review' && !job?.report?.nothingToImport && (
                        <>
                            <Button size="big" variant="outlined" label={t('formResults.import.actions.cancel')} data-sel-role="import-cancel" onClick={close}/>
                            <Button
                                size="big"
                                color="accent"
                                icon={<Upload/>}
                                label={t('formResults.import.actions.import')}
                                data-sel-role="import-confirm"
                                isDisabled={!job?.report || !choicesReady(choices, job.report)}
                                onClick={handleImport}
                            />
                        </>
                    )}
                    {(state === 'importing' || state === 'done') && (
                        <Button size="big" color="accent" label={t('formResults.import.actions.close')} data-sel-role="import-close" onClick={close}/>
                    )}
                    {state === 'failed' && (
                        <>
                            <Button size="big" variant="outlined" label={t('formResults.import.actions.close')} data-sel-role="import-close" onClick={close}/>
                            <Button size="big" color="accent" label={t('formResults.import.actions.tryAgain')} data-sel-role="import-try-again" onClick={tryAgain}/>
                        </>
                    )}
                </div>
            </div>
        </dialog>
    );
};
