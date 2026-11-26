import React, {useCallback, useEffect, useRef, useState} from 'react';
import {useMutation, useQuery} from '@apollo/client';
import {Button, Loader, Typography} from '@jahia/moonstone';
import {useTranslation} from 'react-i18next';
import {
    type DetailData,
    GET_QUARANTINE_LIST,
    GET_QUARANTINE_SUBMISSION,
    type HeldDetail,
    type ListData,
    OPERATE_QUARANTINE_SUBMISSION,
    type OperateData,
    type QuarantineList
} from './graphql';
import {
    CONFIRMED_OPERATIONS,
    currentSiteKey,
    isChangedUnderneath,
    nextOffset,
    pageRange,
    type QuarantineOperation,
    type QuarantineState,
    uiContext
} from './quarantine.utils';
import {QUARANTINE_CSS, ROOT_CLASS} from './styles';
import {type Announce, LiveRegions, useAnnouncer} from './components/Announcer';
import {FormFilter, StateFilter} from './components/Filters';
import {Paging, rowLabel, SubmissionsTable} from './components/SubmissionsTable';
import {ActionButtons, DetailFrame, SubmissionDetail} from './components/SubmissionDetail';
import {ConfirmDialog} from './components/ConfirmDialog';

/** Where the focus goes once the element exists: the screen sets it, the next render that shows the element applies it. */
type FocusRequest =
    | {kind: 'detailHeading'}
    | {kind: 'listHeading'}
    | {kind: 'reload'}
    | {kind: 'row'; id: string}
    | {kind: 'action'; operation: QuarantineOperation};

type Translate = (key: string, options?: Record<string, unknown>) => string;

interface Filters {
    state: QuarantineState | null;
    formId: string | null;
    offset: number;
}

const NO_FILTER: Filters = {state: null, formId: null, offset: 0};

/** The words that name the filters, for the caption of the table and the announcement of a page. */
function filterWords(filters: Filters, list: QuarantineList | null, t: Translate): string {
    const words: string[] = [];
    if (filters.state) {
        words.push(t('quarantine.filter.stateWord', {state: t(`quarantine.state.${filters.state}`)}));
    }

    if (filters.formId) {
        const form = list?.forms.find(one => one.formId === filters.formId);
        words.push(t('quarantine.filter.formWord', {form: form?.formTitle ?? filters.formId}));
    }

    return words.join(', ');
}

/** What the status region says once a page has arrived. */
function listSentence(list: QuarantineList | null, filters: Filters, t: Translate): string {
    if (!list) {
        return t('quarantine.noAccess');
    }

    const filtered = filters.state !== null || filters.formId !== null;
    const page = list.submissions;
    if (page.totalCount === 0) {
        return filtered ? t('quarantine.filter.noMatch') : t('quarantine.empty');
    }

    const range = pageRange(page.offset, page.items.length, page.totalCount);
    const words = filterWords(filters, list, t);
    return t('quarantine.announce.list', {from: range.from, to: range.to, total: range.total}) + (words ? `, ${words}` : '');
}

/** Moves the focus to the element a request names, as soon as it is rendered; the request is then spent. */
function useFocusRequests(elements: {
    listHeadingRef: React.RefObject<HTMLHeadingElement>;
    detailHeadingRef: React.RefObject<HTMLHeadingElement>;
    reload: React.RefObject<HTMLButtonElement>;
    rows: React.MutableRefObject<Map<string, HTMLButtonElement>>;
    actions: React.MutableRefObject<Map<QuarantineOperation, HTMLButtonElement>>;
}) {
    const requestRef = useRef<FocusRequest | null>(null);
    useEffect(() => {
        const wanted = requestRef.current;
        if (!wanted) {
            return;
        }

        let target: HTMLElement | null | undefined;
        switch (wanted.kind) {
            case 'detailHeading':
                target = elements.detailHeadingRef.current;
                break;
            case 'listHeading':
                target = elements.listHeadingRef.current;
                break;
            case 'reload':
                target = elements.reload.current;
                break;
            case 'row':
                target = elements.rows.current.get(wanted.id) ?? elements.listHeadingRef.current;
                break;
            default:
                target = elements.actions.current.get(wanted.operation) ?? elements.detailHeadingRef.current;
        }

        if (target) {
            target.focus();
            requestRef.current = null;
        }
    });
    return useCallback((wanted: FocusRequest) => {
        requestRef.current = wanted;
    }, []);
}

/** Announces the list once per filter and page: an operation that refreshes the same page does not repeat it. */
function useListAnnouncement(data: ListData | undefined, loading: boolean, filters: Filters, announce: Announce, t: Translate) {
    const announcedRef = useRef<string | null>(null);
    useEffect(() => {
        if (!data || loading) {
            return;
        }

        const key = JSON.stringify(filters);
        if (announcedRef.current !== key) {
            announcedRef.current = key;
            announce(listSentence(data.formidableQuarantine, filters, t));
        }
    }, [data, loading, filters, announce, t]);
}

const Message = ({children}: {children: React.ReactNode}) => <div style={{padding: '24px 0'}}>{children}</div>;

export const QuarantineApp = () => {
    const {t} = useTranslation('formidable-quarantine');
    const siteKey = currentSiteKey();
    const language = uiContext().uilang || 'en';
    const {polite, assertive, announce} = useAnnouncer();

    const [filters, setFilters] = useState<Filters>(NO_FILTER);
    const [openId, setOpenId] = useState<string | null>(null);
    const [confirm, setConfirm] = useState<'RELEASE' | 'REJECT' | null>(null);
    const [confirmFailure, setConfirmFailure] = useState<string | null>(null);
    const [running, setRunning] = useState<QuarantineOperation | null>(null);
    const [changed, setChanged] = useState(false);
    const [reloading, setReloading] = useState(false);

    const listHeadingRef = useRef<HTMLHeadingElement>(null);
    const detailHeadingRef = useRef<HTMLHeadingElement>(null);
    const reloadButtonRef = useRef<HTMLButtonElement>(null);
    const rowButtonsRef = useRef(new Map<string, HTMLButtonElement>());
    const actionButtonsRef = useRef(new Map<QuarantineOperation, HTMLButtonElement>());
    const focus = useFocusRequests({listHeadingRef, detailHeadingRef, reload: reloadButtonRef, rows: rowButtonsRef, actions: actionButtonsRef});

    const list = useQuery<ListData>(GET_QUARANTINE_LIST, {
        variables: {siteKey, formId: filters.formId, states: filters.state ? [filters.state] : null, offset: filters.offset},
        skip: !siteKey,
        fetchPolicy: 'network-only',
        notifyOnNetworkStatusChange: true
    });
    // the previous page stays on screen while the next one loads: the focused control never unmounts
    const listData = list.data ?? list.previousData;
    const quarantine = listData?.formidableQuarantine ?? null;

    const detail = useQuery<DetailData>(GET_QUARANTINE_SUBMISSION, {
        variables: {siteKey, id: openId},
        skip: !siteKey || !openId,
        fetchPolicy: 'network-only'
    });
    const submission: HeldDetail | null = openId ? detail.data?.formidableQuarantine?.submission ?? null : null;
    const [operate] = useMutation<OperateData>(OPERATE_QUARANTINE_SUBMISSION);

    useEffect(() => {
        // the first load: the sentence of the list replaces it once the page has arrived
        if (siteKey) {
            announce(t('quarantine.loading'));
        }
    }, [siteKey, announce, t]);
    useListAnnouncement(list.data, list.loading, filters, announce, t);
    useEffect(() => {
        if (list.error) {
            announce(t('quarantine.loadFailed'), true);
        }
    }, [list.error, announce, t]);

    const changeFilters = (next: Partial<Filters>) => setFilters(current => ({...current, offset: 0, ...next}));

    const open = (id: string) => {
        setOpenId(id);
        setChanged(false);
        focus({kind: 'detailHeading'});
    };

    const close = () => {
        if (openId) {
            focus({kind: 'row', id: openId});
        }

        setOpenId(null);
        setChanged(false);
    };

    const showChanged = () => {
        setConfirm(null);
        setChanged(true);
        focus({kind: 'reload'});
    };

    const run = async (operation: QuarantineOperation) => {
        if (!openId) {
            return;
        }

        const rows = quarantine?.submissions.items ?? [];
        const index = rows.findIndex(row => row.id === openId);
        const neighbour = rows[index + 1]?.id ?? rows[index - 1]?.id ?? null;
        setRunning(operation);
        setConfirmFailure(null);
        try {
            const result = await operate({variables: {siteKey, id: openId, operation}});
            const after = result.data?.formidableQuarantine?.operate;
            if (!after) {
                // the user no longer manages the site: what the screen shows is out of date
                showChanged();
                return;
            }

            setConfirm(null);
            const leaves = filters.state !== null && after.state !== filters.state;
            if (leaves) {
                setOpenId(null);
                focus(neighbour ? {kind: 'row', id: neighbour} : {kind: 'listHeading'});
                await list.refetch();
            } else {
                focus({kind: 'detailHeading'});
                await Promise.all([list.refetch(), detail.refetch()]);
            }

            announce(t(`quarantine.done.${operation}`, {state: t(`quarantine.state.${after.state}`)}));
        } catch (error) {
            if (isChangedUnderneath(error)) {
                showChanged();
            } else if (CONFIRMED_OPERATIONS.has(operation)) {
                setConfirmFailure(t('quarantine.failed'));
            } else {
                announce(t('quarantine.failed'), true);
            }
        } finally {
            setRunning(null);
        }
    };

    const reload = async () => {
        setReloading(true);
        try {
            const [, again] = await Promise.all([list.refetch(), detail.refetch()]);
            setChanged(false);
            const now = again.data?.formidableQuarantine?.submission;
            announce(now ? t('quarantine.reloaded', {state: t(`quarantine.state.${now.state}`)}) : t('quarantine.detail.gone'));
            focus({kind: 'detailHeading'});
        } catch {
            announce(t('quarantine.loadFailed'), true);
        } finally {
            setReloading(false);
        }
    };

    const chrome = (body: React.ReactNode) => (
        <div className={ROOT_CLASS} style={{height: '100%', overflow: 'auto', padding: '24px', backgroundColor: 'var(--color-light)'}} data-sel-role="quarantine-app">
            <style>{QUARANTINE_CSS}</style>
            <LiveRegions polite={polite} assertive={assertive}/>
            <Typography ref={listHeadingRef} component="h1" variant="title" weight="bold" tabIndex={-1} style={{marginBottom: '16px'}}>
                {t('quarantine.title')}
            </Typography>
            {body}
        </div>
    );

    if (!siteKey) {
        return chrome(<Message><p>{t('quarantine.noSite')}</p></Message>);
    }

    if (!listData && list.loading) {
        return chrome(<Message><Loader size="big"/><span className="fmdbq-sr-only">{t('quarantine.loading')}</span></Message>);
    }

    if (!listData) {
        return chrome(
            <Message>
                <p data-sel-role="quarantine-load-failed">{t('quarantine.loadFailed')}</p>
                <Button label={t('quarantine.tryAgain')} variant="outlined" onClick={() => list.refetch()}/>
            </Message>
        );
    }

    if (!quarantine) {
        return chrome(<Message><p data-sel-role="quarantine-no-access">{t('quarantine.noAccess')}</p></Message>);
    }

    if (quarantine.counts.every(count => count.count === 0)) {
        return chrome(<Message><p data-sel-role="quarantine-empty">{t('quarantine.empty')}</p></Message>);
    }

    const page = quarantine.submissions;
    const range = pageRange(page.offset, page.items.length, page.totalCount);
    const words = filterWords(filters, quarantine, t);
    const caption = words ? t('quarantine.table.captionFiltered', {filters: words}) : t('quarantine.table.caption');
    const subject = submission ? rowLabel(submission, language, t) : '';

    return chrome(
        <>
            <div style={{display: 'flex', flexWrap: 'wrap', alignItems: 'flex-end', gap: '16px', marginBottom: '16px'}}>
                <StateFilter counts={quarantine.counts} selected={filters.state} onSelect={state => changeFilters({state})}/>
                <FormFilter forms={quarantine.forms} selected={filters.formId} onSelect={formId => changeFilters({formId})}/>
            </div>
            <div style={{display: 'flex', gap: '16px', alignItems: 'flex-start'}}>
                <div style={{flex: '1 1 0', minWidth: 0}}>
                    <SubmissionsTable
                        rows={page.items}
                        caption={caption}
                        openId={openId}
                        busy={list.loading}
                        language={language}
                        rowButtonsRef={rowButtonsRef}
                        noMatch={(
                            <span data-sel-role="quarantine-no-match">
                                {t('quarantine.filter.noMatch')}{' '}
                                <Button
                                    label={t('quarantine.filter.clear')}
                                    variant="ghost"
                                    onClick={() => {
                                        setFilters(NO_FILTER);
                                        focus({kind: 'listHeading'});
                                    }}
                                />
                            </span>
                        )}
                        onOpen={open}
                    />
                    {page.totalCount > 0 && (
                        <div style={{marginTop: '12px'}}>
                            <Paging range={range} onPage={direction => setFilters(current => ({...current, offset: nextOffset(current.offset, direction, page.totalCount)}))}/>
                        </div>
                    )}
                </div>
                {openId && (
                    <DetailFrame onClose={close}>
                        {renderDetail()}
                    </DetailFrame>
                )}
            </div>
            {confirm && submission && (
                <ConfirmDialog
                    operation={confirm}
                    subject={subject}
                    busy={running === confirm}
                    failure={confirmFailure}
                    onConfirm={() => run(confirm)}
                    onCancel={() => {
                        setConfirm(null);
                        setConfirmFailure(null);
                        focus({kind: 'action', operation: confirm});
                    }}
                />
            )}
        </>
    );

    function renderDetail() {
        if (!submission && detail.loading) {
            return <><Loader/><span className="fmdbq-sr-only">{t('quarantine.detail.loading')}</span></>;
        }

        if (detail.error && !detail.data) {
            return (
                <>
                    <p className="fmdbq-alert">{t('quarantine.detail.loadFailed')}</p>
                    <Button label={t('quarantine.tryAgain')} variant="outlined" onClick={() => detail.refetch()}/>
                </>
            );
        }

        if (!submission) {
            return <p data-sel-role="quarantine-gone">{t('quarantine.detail.gone')}</p>;
        }

        return (
            <SubmissionDetail
                submission={submission}
                language={language}
                headingRef={detailHeadingRef}
                changed={changed}
                reloading={reloading}
                reloadRef={reloadButtonRef}
                actions={(
                    <ActionButtons
                        allowedOperations={submission.allowedOperations}
                        running={running}
                        actionButtonsRef={actionButtonsRef}
                        onOperation={operation => {
                            if (CONFIRMED_OPERATIONS.has(operation)) {
                                setConfirmFailure(null);
                                setConfirm(operation as 'RELEASE' | 'REJECT');
                            } else {
                                run(operation);
                            }
                        }}
                    />
                )}
                onReload={reload}
            />
        );
    }
};
