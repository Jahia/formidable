import React, {useCallback, useEffect, useMemo, useRef, useState} from 'react';
import {useQuery} from '@apollo/client';
import {Button, Check, Close, Loader, RadioGroup, RadioItem, Typography, Upload} from '@jahia/moonstone';
import {useTranslation} from 'react-i18next';
import {formatFileSize, uiContext} from '../FormResults.utils';
import {GET_SITE_FORMS} from '../graphql';
import {AttachToForm} from './AttachToForm';
import {
    type AttachChoice,
    buildSiteFormsQuery,
    choicesReady,
    closeJob,
    fetchJob,
    formTitle,
    groupedFieldNotes,
    type ImportChoice,
    type ImportJob,
    type ImportReport,
    type ImportReportForm,
    type ImportSettings,
    kindOf,
    parseSiteForms,
    POLL_INTERVAL_MS,
    refuseFile,
    type SiteForm,
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
    const {t} = useTranslation('formidable-engine');
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
        variables: {formsQuery: buildSiteFormsQuery(siteKey), language},
        skip: state !== 'review',
        fetchPolicy: 'network-only'
    });
    const siteForms = useMemo<SiteForm[] | null>(() => (siteFormsData ? parseSiteForms(siteFormsData) : null), [siteFormsData]);

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
                            <ReportView
                                report={job.report}
                                language={language}
                                t={t}
                                choices={choices}
                                siteForms={siteForms}
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
                            <NextSteps report={job.report} language={language} t={t}/>
                            <ReportView report={job.report} language={language} t={t}/>
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

interface ReportProps {
    report: ImportReport;
    language: string;
    t: (key: string, options?: Record<string, unknown>) => string;
}

interface ReportViewProps extends ReportProps {
    /** The choices of the review, when the report is a dry run the administrator can still steer. */
    choices?: Record<string, ImportChoice>;
    /** The forms of the site an attachment can pick, null while they load. */
    siteForms?: SiteForm[] | null;
    /** The fields of the forms read so far, by identifier, and how to keep the ones read next. */
    targetFields?: Record<string, TargetField[]>;
    onTargetFields?: (formId: string, fields: TargetField[]) => void;
    onChoice?: (sourceName: string, choice: ImportChoice) => void;
}

const ReportView = ({report, language, t, choices, siteForms, targetFields, onTargetFields, onChoice}: ReportViewProps) => (
    <div data-sel-role="import-report" style={{display: 'flex', flexDirection: 'column', gap: '12px'}}>
        <Typography data-sel-role="import-totals">
            {t('formResults.import.report.totals', {
                forms: report.totals.forms,
                found: report.totals.submissionsFound,
                toImport: report.dryRun ? report.totals.submissionsToImport : report.totals.submissionsImported
            })}
        </Typography>
        {report.nothingToImport && (
            <Typography data-sel-role="import-nothing">{t('formResults.import.report.nothing')}</Typography>
        )}
        {report.forms.map(form => (
            <FormReport
                key={form.sourceName}
                form={form}
                report={report}
                language={language}
                t={t}
                choices={choices}
                siteForms={siteForms}
                targetFields={targetFields}
                onTargetFields={onTargetFields}
                onChoice={onChoice}
            />
        ))}
    </div>
);

/**
 * What to do next, per outcome, with the forms it concerns by name: a created form is to review and publish,
 * attached results show under their form, an entry alone just is.
 */
const NextSteps = ({report, language, t}: ReportProps) => {
    const groups = (['created', 'attached', 'resultsOnly'] as const)
        .map(outcome => ({outcome, forms: report.forms.filter(form => form.outcome === outcome)}))
        .filter(group => group.forms.length > 0);
    if (groups.length === 0) {
        return null;
    }
    return (
        <div data-sel-role="import-next-step" style={{display: 'flex', flexDirection: 'column', gap: '8px'}}>
            {groups.map(group => (
                <div key={group.outcome}>
                    <Typography variant="body">{t(`formResults.import.done.${group.outcome}`)}</Typography>
                    <ul style={{margin: '4px 0 0', paddingLeft: '20px'}}>
                        {group.forms.map(form => (
                            <li key={form.sourceName}>
                                <Typography variant="body" weight="bold">{formTitle(form, language)}</Typography>
                                {/* attached results are read under the form they went to: the step names it */}
                                {group.outcome === 'attached' && form.targetName && (
                                    <Typography variant="body"> → {form.targetName}</Typography>
                                )}
                            </li>
                        ))}
                    </ul>
                </div>
            ))}
        </div>
    );
};

interface FormReportProps extends ReportViewProps {
    form: ImportReportForm;
}

const OUTCOME_OF_CHOICE = {resultsOnly: 'resultsOnly', create: 'created', attach: 'attached'} as const;

/** What the import will do for a form: the choice of the review while it can still change, else the outcome the server reports. */
function outcomeOf(form: ImportReportForm, choice: ImportChoice | undefined, steerable: boolean): ImportReportForm['outcome'] {
    if (form.outcome === 'found' || !steerable) {
        return form.outcome;
    }
    return OUTCOME_OF_CHOICE[kindOf(choice)];
}

const FormReport = ({form, report, language, t, choices, siteForms, targetFields, onTargetFields, onChoice}: FormReportProps) => {
    const steerable = Boolean(onChoice) && form.outcome !== 'found';
    const choice = choices?.[form.sourceName];
    const outcome = outcomeOf(form, choice, Boolean(onChoice));
    const attachment = typeof choice === 'object' ? choice : null;
    // what the form itself cannot carry over only matters when a form is created
    const notes = outcome === 'created' ? [...form.notes, ...groupedFieldNotes(form.fields)] : [];
    const describe = (what: ImportReportForm['outcome']) => {
        switch (what) {
            case 'found':
                return t('formResults.import.report.found', {path: form.targetPath});
            case 'created':
                return t('formResults.import.report.created', {name: form.targetName, folder: report.importedFormsFolder});
            case 'attached': {
                // in the review, the form is the one picked here; once imported, the one the server reports
                const picked = attachment && siteForms?.find(siteForm => siteForm.uuid === attachment.form);
                if (steerable && !picked) {
                    return t('formResults.import.report.attachPending');
                }
                return t('formResults.import.report.attached', {
                    name: picked ? picked.title : form.targetName,
                    path: picked ? picked.path : form.targetPath
                });
            }
            default:
                return t('formResults.import.report.resultsOnly', {name: form.targetName});
        }
    };
    const choose = (value: string) => {
        onChoice?.(form.sourceName, value === 'attach' ? {choice: 'attach', form: '', mapping: {}} : value as ImportChoice);
    };
    const figures = [
        t('formResults.import.report.submissions', {
            found: form.submissions.found,
            toImport: report.dryRun ? form.submissions.toImport : form.submissions.imported,
            already: form.submissions.alreadyImported
        }),
        t('formResults.import.report.fields', {count: form.fields.length}),
        form.files.count > 0 && t('formResults.import.report.files', {count: form.files.count, size: formatFileSize(form.files.bytes)}),
        form.files.missing > 0 && t('formResults.import.report.filesMissing', {count: form.files.missing}),
        (form.values.dropped > 0 || form.values.notConverted > 0) &&
            t('formResults.import.report.values', {dropped: form.values.dropped, notConverted: form.values.notConverted})
    ].filter(Boolean).join(' · ');
    return (
        <div data-sel-role="import-report-form" data-sel-name={form.sourceName} data-sel-outcome={outcome ?? ''} style={{border: '1px solid var(--color-gray_light40)', padding: '16px', display: 'flex', flexDirection: 'column', gap: '12px'}}>
            <div style={{display: 'flex', justifyContent: 'space-between', alignItems: 'baseline', gap: '16px', flexWrap: 'wrap'}}>
                <Typography variant="subheading" weight="bold">{formTitle(form, language)}</Typography>
                <Typography variant="body" data-sel-role="import-report-figures">{figures}</Typography>
            </div>
            {steerable ? (
                <RadioGroup
                    name={`import-choice-${form.sourceName}`}
                    value={kindOf(choice)}
                    aria-label={t('formResults.import.choice.label')}
                    data-sel-role="import-choice"
                    onChange={(_event: React.ChangeEvent<HTMLInputElement>, value: string) => choose(value)}
                >
                    <RadioItem id={`import-choice-${form.sourceName}-resultsOnly`} value="resultsOnly" label={t('formResults.import.choice.resultsOnly')}/>
                    <RadioItem id={`import-choice-${form.sourceName}-create`} value="create" label={t('formResults.import.choice.create')}/>
                    <RadioItem id={`import-choice-${form.sourceName}-attach`} value="attach" label={t('formResults.import.choice.attach')}/>
                </RadioGroup>
            ) : null}
            {steerable && attachment && (
                <AttachToForm
                    sourceFields={form.fields}
                    choice={attachment}
                    siteForms={siteForms ?? null}
                    targetFields={targetFields ?? {}}
                    language={language}
                    t={t}
                    onTargetFields={onTargetFields ?? (() => undefined)}
                    onChange={(next: AttachChoice) => onChoice?.(form.sourceName, next)}
                />
            )}
            <Typography variant="body" data-sel-role="import-report-outcome">{describe(outcome)}</Typography>
            {notes.length > 0 && (
                <ul style={{margin: 0, paddingLeft: '20px'}} data-sel-role="import-report-notes">
                    {notes.map(note => (
                        <li key={note}><Typography variant="body">{note}</Typography></li>
                    ))}
                </ul>
            )}
        </div>
    );
};
