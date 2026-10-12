import React, {useCallback, useEffect, useMemo, useRef, useState} from 'react';
import {useQuery} from '@apollo/client';
import {Button, Check, Close, Dropdown, Link, Loader, Typography} from '@jahia/moonstone';
import {useTranslation} from 'react-i18next';
import {formResultsLabel, type FormResultsNode, uiContext} from '../FormResults.utils';
import {GET_SITE_FORMS} from '../graphql';
import {AttachToForm} from './AttachToForm';
import {
    type AttachChoice,
    type AttachmentJob,
    type AttachmentReport,
    attachProblem,
    buildSiteFormsQuery,
    closeJob,
    describeEntry,
    type EntryDescription,
    fetchJob,
    parseSiteForms,
    POLL_INTERVAL_MS,
    type SiteForm,
    sourceFieldsOf,
    sourceLabel,
    startAttachment
} from './import.utils';

interface AttachResultsDialogProps {
    siteKey: string;
    /** The entry whose imported results move. */
    formResults: FormResultsNode;
    /** Called when the dialog closes; true once an attachment ended, so that the list is re-read. */
    onClose: (attached: boolean) => void;
}

type DialogState = 'loading' | 'review' | 'attaching' | 'done' | 'failed';

const NO_CHOICE: AttachChoice = {choice: 'attach', form: '', mapping: {}};

/**
 * The <b>Attach to another form</b> dialog of an entry of the Results page (docs/architecture/forms-import.md,
 * "Iteration 2"): the source form, when the entry holds several, the form of the site the results go to and
 * the mapping of the field names, as the import dialog proposes them, then the attachment, a job of the server
 * the dialog polls, and its report.
 */
export const AttachResultsDialog = ({siteKey, formResults, onClose}: AttachResultsDialogProps) => {
    const {t} = useTranslation('formidable-engine');
    const language = uiContext().uilang || 'en';
    const dialogRef = useRef<HTMLDialogElement | null>(null);
    const [state, setState] = useState<DialogState>('loading');
    const [description, setDescription] = useState<EntryDescription | null>(null);
    const [sourceId, setSourceId] = useState('');
    const [choice, setChoice] = useState<AttachChoice>(NO_CHOICE);
    const [job, setJob] = useState<AttachmentJob | null>(null);
    const [errorMessage, setErrorMessage] = useState('');
    const attachedRef = useRef(false);

    const {data: siteFormsData} = useQuery(GET_SITE_FORMS, {
        variables: {formsQuery: buildSiteFormsQuery(siteKey), language},
        fetchPolicy: 'network-only'
    });
    const siteForms = useMemo<SiteForm[] | null>(() => (siteFormsData ? parseSiteForms(siteFormsData) : null), [siteFormsData]);

    // The sources of the entry, read once: the one source is picked by itself, several wait for the choice.
    useEffect(() => {
        let cancelled = false;
        describeEntry(siteKey, formResults.uuid, language)
            .then(described => {
                if (cancelled) {
                    return;
                }
                setDescription(described);
                setSourceId(described.sources.length === 1 ? described.sources[0].id : '');
                setState('review');
            })
            .catch(error => {
                if (cancelled) {
                    return;
                }
                setErrorMessage(error instanceof Error ? error.message : t('formResults.import.error.unexpected'));
                setState('failed');
            });
        return () => {
            cancelled = true;
        };
    }, [siteKey, formResults.uuid, language, t]);

    // The job goes on without the dialog: while it runs, its state is read every second.
    useEffect(() => {
        if (!job || state !== 'attaching') {
            return undefined;
        }
        const timer = setInterval(async () => {
            try {
                const refreshed = await fetchJob<AttachmentReport>(siteKey, job.id);
                setJob(refreshed);
                if (refreshed.state === 'done') {
                    attachedRef.current = true;
                    setState('done');
                } else if (refreshed.state === 'failed') {
                    setErrorMessage(refreshed.message ?? t('formResults.import.error.unexpected'));
                    setState('failed');
                }
            } catch (error) {
                setErrorMessage(error instanceof Error ? error.message : t('formResults.import.error.unexpected'));
                setState('failed');
            }
        }, POLL_INTERVAL_MS);
        return () => clearInterval(timer);
    }, [job, state, siteKey, t]);

    const close = useCallback(async () => {
        if (job && state !== 'attaching') {
            try {
                await closeJob(siteKey, job.id);
            } catch {
                // the node goes by itself after an hour
            }
        }
        onClose(attachedRef.current);
    }, [job, state, siteKey, onClose]);

    const handleCancel = useCallback((event: React.SyntheticEvent<HTMLDialogElement>) => {
        event.preventDefault();
        close();
    }, [close]);

    const source = description?.sources.find(candidate => candidate.id === sourceId) ?? null;
    const sourceFields = useMemo(() => (source ? sourceFieldsOf(source, language) : []), [source, language]);
    const ready = Boolean(source) && attachProblem(choice, sourceFields) === null;

    const handleAttach = async () => {
        if (!source) {
            return;
        }
        setErrorMessage('');
        try {
            const started = await startAttachment(siteKey, formResults.uuid, source.id, choice);
            setJob(started);
            setState(started.state === 'done' ? 'done' : started.state === 'failed' ? 'failed' : 'attaching');
        } catch (error) {
            // a refused start (an import runs on the site, a mapping the server will not take) keeps the review on screen
            setErrorMessage(error instanceof Error ? error.message : t('formResults.import.error.unexpected'));
        }
    };

    const report = job?.report ?? null;
    const picked = siteForms?.find(siteForm => siteForm.uuid === choice.form);
    return (
        <dialog
            ref={el => {
                dialogRef.current = el;
                if (el && !el.open) {
                    el.showModal();
                }
            }}
            data-sel-role="attach-results-dialog"
            data-sel-state={state}
            onCancel={handleCancel}
            style={{
                border: 'none',
                padding: 0,
                width: '960px',
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
                        <Typography variant="heading" weight="bold">{t('formResults.attach.title')}</Typography>
                        <Typography variant="body" style={{color: 'var(--color-gray)', marginTop: '4px'}}>
                            {t('formResults.attach.description', {entry: formResultsLabel(formResults)})}
                        </Typography>
                    </div>
                    <Button
                        variant="ghost"
                        size="big"
                        icon={<Close/>}
                        aria-label={t('formResults.detail.close')}
                        data-sel-role="attach-close-x"
                        onClick={close}
                    />
                </div>
                <div style={{padding: '20px', overflow: 'auto', display: 'flex', flexDirection: 'column', gap: '16px'}}>
                    {state === 'loading' && (
                        <div style={{display: 'flex', alignItems: 'center', gap: '16px', padding: '24px 0'}}>
                            <Loader size="big"/>
                            <Typography>{t('formResults.attach.loading')}</Typography>
                        </div>
                    )}
                    {state === 'review' && description && description.sources.length === 0 && (
                        <Typography data-sel-role="attach-no-source">{t('formResults.attach.noSource')}</Typography>
                    )}
                    {state === 'review' && description && description.sources.length > 1 && (
                        <div style={{display: 'flex', alignItems: 'center', gap: '12px'}}>
                            <Typography variant="body">{t('formResults.attach.source')}</Typography>
                            <div data-sel-role="attach-source" style={{minWidth: '320px'}}>
                                <Dropdown
                                    variant="outlined"
                                    data={description.sources.map(candidate => ({
                                        label: sourceLabel(candidate),
                                        description: t('formResults.attach.sourceSubmissions', {count: candidate.submissions}),
                                        value: candidate.id
                                    }))}
                                    value={sourceId || undefined}
                                    onChange={(_event, item) => {
                                        if (item.value && item.value !== sourceId) {
                                            setSourceId(item.value);
                                            setChoice(NO_CHOICE);
                                        }
                                    }}
                                />
                            </div>
                        </div>
                    )}
                    {state === 'review' && source && (
                        <>
                            <Typography variant="body" data-sel-role="attach-source-figures">
                                {description?.sources.length === 1 ? `${sourceLabel(source)} · ` : ''}
                                {t('formResults.attach.sourceSubmissions', {count: source.submissions})}
                            </Typography>
                            <AttachToForm
                                sourceFields={sourceFields}
                                choice={choice}
                                siteForms={siteForms}
                                language={language}
                                t={t}
                                onChange={setChoice}
                            />
                        </>
                    )}
                    {state === 'attaching' && (
                        <div style={{display: 'flex', alignItems: 'center', gap: '16px', padding: '24px 0'}}>
                            <Loader size="big"/>
                            <Typography data-sel-role="attach-progress">{t('formResults.attach.attaching')}</Typography>
                        </div>
                    )}
                    {state === 'done' && report && (
                        <>
                            <div style={{display: 'flex', alignItems: 'center', gap: '8px'}} data-sel-role="attach-done">
                                <Check size="big" color="green"/>
                                <Typography variant="subheading" weight="bold">{t('formResults.attach.done.title')}</Typography>
                            </div>
                            <Typography variant="body" data-sel-role="attach-report">
                                {t(report.moved > 0 || report.previousEntryRemoved ? 'formResults.attach.done.summary' : 'formResults.attach.done.relinked', {
                                    form: picked ? picked.title : report.formName,
                                    path: report.form,
                                    moved: report.moved,
                                    renamed: report.renamed
                                })}
                            </Typography>
                            {report.formLeftWithoutResults && (
                                <Typography variant="body" data-sel-role="attach-form-left">
                                    {t('formResults.attach.done.formLeft', {path: report.formLeftWithoutResults})}
                                </Typography>
                            )}
                        </>
                    )}
                    {state === 'failed' && (
                        <div data-sel-role="attach-failed">
                            <Typography weight="bold" color="danger">{t('formResults.attach.failed.title')}</Typography>
                            <Typography color="danger">{errorMessage || job?.message}</Typography>
                        </div>
                    )}
                    {errorMessage && state !== 'failed' && (
                        <Typography color="danger" data-sel-role="attach-error">{errorMessage}</Typography>
                    )}
                </div>
                <div style={{display: 'flex', justifyContent: 'flex-end', gap: '8px', padding: '0 20px 20px'}}>
                    {(state === 'loading' || state === 'review') && (
                        <Button size="big" variant="outlined" label={t('formResults.attach.cancel')} data-sel-role="attach-cancel" onClick={close}/>
                    )}
                    {state === 'review' && (
                        <Button
                            size="big"
                            color="accent"
                            icon={<Link/>}
                            label={t('formResults.attach.attach')}
                            data-sel-role="attach-confirm"
                            isDisabled={!ready}
                            onClick={handleAttach}
                        />
                    )}
                    {(state === 'attaching' || state === 'done' || state === 'failed') && (
                        <Button size="big" color="accent" label={t('formResults.attach.close')} data-sel-role="attach-close" onClick={close}/>
                    )}
                </div>
            </div>
        </dialog>
    );
};
