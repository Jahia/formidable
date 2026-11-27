import React, {useCallback, useEffect, useRef, useState} from 'react';
import {useMutation, useQuery} from '@apollo/client';
import {Banner, Button, EmptyData, Header, LayoutContent, Paper, Reload, Typography} from '@jahia/moonstone';
import {useTranslation} from 'react-i18next';
import {
    type DetailData,
    type FormEntry,
    GET_QUARANTINE_LIST,
    GET_QUARANTINE_SUBMISSION,
    GET_QUARANTINE_SUMMARY,
    type HeldDetail,
    type ListData,
    OPERATE_QUARANTINE_SUBMISSION,
    type OperateData,
    type QuarantineSummary,
    type SubmissionPage,
    type SummaryData
} from './graphql';
import {
    CONFIRMED_OPERATIONS,
    currentSiteKey,
    isChangedUnderneath,
    offsetInsideList,
    pageRange,
    type QuarantineOperation,
    type QuarantineState,
    uiContext
} from './quarantine.utils';
import {QUARANTINE_CSS, ROOT_CLASS} from './styles';
import {type Announce, LiveRegions, useAnnouncer} from './components/Announcer';
import {FormFilter, StateFilter} from './components/Filters';
import {LIST_HEADING_ID, rowLabel, type SortDirection, SubmissionsTable} from './components/SubmissionsTable';
import {ActionButtons, DetailFrame, SubmissionDetail} from './components/SubmissionDetail';
import {ConfirmDialog} from './components/ConfirmDialog';

/**
 * Where the focus goes once the element exists: the screen sets it, and the next render that shows the element applies
 * it. Moonstone Buttons take no ref, so a target is found by its data attributes.
 */
type FocusRequest =
    | {kind: 'detailHeading'}
    | {kind: 'listHeading'}
    | {kind: 'reload'}
    | {kind: 'refresh'}
    | {kind: 'empty'}
    | {kind: 'row'; id: string}
    | {kind: 'action'; operation: QuarantineOperation}
    | {kind: 'tryAgain'; where: 'list' | 'detail'};

type Translate = (key: string, options?: Record<string, unknown>) => string;

interface Filters {
    state: QuarantineState | null;
    formId: string | null;
    offset: number;
    sort: SortDirection;
}

const NO_FILTER: Filters = {state: null, formId: null, offset: 0, sort: 'ascending'};

/** The words that name the filters, for the heading of the table and the announcement of a page. */
function filterWords(filters: Filters, forms: FormEntry[], t: Translate): string {
    const words: string[] = [];
    if (filters.state) {
        words.push(t('quarantine.filter.stateWord', {state: t(`quarantine.state.${filters.state}`)}));
    }

    if (filters.formId) {
        const form = forms.find(one => one.formId === filters.formId);
        words.push(t('quarantine.filter.formWord', {form: form?.formTitle ?? filters.formId}));
    }

    return words.join(', ');
}

/** What the status region says once a page has arrived. */
function listSentence(page: SubmissionPage, filters: Filters, forms: FormEntry[], t: Translate): string {
    const filtered = filters.state !== null || filters.formId !== null;
    if (page.totalCount === 0) {
        return filtered ? t('quarantine.filter.noMatch') : t('quarantine.empty');
    }

    const range = pageRange(page.offset, page.items.length, page.totalCount);
    const words = filterWords(filters, forms, t);
    return t('quarantine.announce.list', {from: range.from, to: range.to, total: range.total}) + (words ? `, ${words}` : '');
}

const bySelRole = (root: HTMLElement | null, role: string) => root?.querySelector<HTMLElement>(`[data-sel-role="${role}"]`) ?? null;

/** The Try again button of the failure banner of the list (or of the whole screen), and of the detail. */
const TRY_AGAIN = {
    list: '[data-sel-role="quarantine-list-failed"] [data-sel-role="quarantine-try-again"], [data-sel-role="quarantine-load-failed"] [data-sel-role="quarantine-try-again"]',
    detail: '[data-sel-role="quarantine-detail-failed"] [data-sel-role="quarantine-try-again"]'
};

function targetOf(wanted: FocusRequest, root: HTMLElement | null): HTMLElement | null {
    const listHeading = () => {
        const heading = root?.querySelector<HTMLElement>('h1') ?? null;
        heading?.setAttribute('tabindex', '-1');
        return heading;
    };
    // the row asked for, else the first row of the page, else the heading of the screen
    const firstRow = () => bySelRole(root, 'quarantine-open') ?? listHeading();
    // a failed reading of the detail replaces it with its banner: the focus asked for the detail goes to Try again
    const detailTryAgain = () => root?.querySelector<HTMLElement>(TRY_AGAIN.detail) ?? null;
    switch (wanted.kind) {
        case 'detailHeading':
            return document.getElementById('fmdbq-detail-title') ?? detailTryAgain();
        case 'listHeading':
            return listHeading();
        case 'reload':
            return bySelRole(root, 'quarantine-reload') ?? detailTryAgain();
        case 'tryAgain':
            return root?.querySelector<HTMLElement>(TRY_AGAIN[wanted.where]) ?? null;
        case 'refresh':
            return bySelRole(root, 'quarantine-refresh');
        case 'empty':
            return bySelRole(root, 'quarantine-no-match') ?? bySelRole(root, 'quarantine-empty') ?? firstRow();
        case 'row':
            return root?.querySelector<HTMLElement>(`[data-sel-role="quarantine-open"][data-id="${CSS.escape(wanted.id)}"]`) ?? firstRow();
        default:
            return bySelRole(root, `quarantine-action-${wanted.operation.toLowerCase()}`) ?? document.getElementById('fmdbq-detail-title')
                ?? detailTryAgain();
    }
}

/** How long after a dialog closed a move of the focus elsewhere is the Modal giving it back, not the user, in ms. */
const FOCUS_GRACE = 300;

/**
 * Moves the focus to the element a request names, as soon as it is rendered, and keeps it there until something else
 * takes it: the request stays after each render, because a later render can re-mount the element (the data of the
 * detail arriving), and a closing Modal gives the focus back to its opener after the render. A key or pointer press,
 * or the focus moving to another element, such as a screen reader moving it, spends the request. Only a request made
 * as a dialog closes lets a move pass during the grace time: the Modal giving the focus back to its opener. A request
 * renders the screen again, so that one made after a request to the server applies at once.
 */
function useFocusRequests(rootRef: React.RefObject<HTMLElement>) {
    const requestRef = useRef<FocusRequest | null>(null);
    const targetRef = useRef<HTMLElement | null>(null);
    const placedAtRef = useRef(0);
    const afterDialogRef = useRef(false);
    const [, setRequests] = useState(0);

    useEffect(() => {
        const spend = () => {
            requestRef.current = null;
        };
        const moved = (event: FocusEvent) => {
            const inGrace = afterDialogRef.current && Date.now() - placedAtRef.current <= FOCUS_GRACE;
            if (requestRef.current && event.target !== targetRef.current && !inGrace) {
                spend();
            }
        };
        document.addEventListener('keydown', spend, true);
        document.addEventListener('mousedown', spend, true);
        document.addEventListener('focusin', moved, true);
        return () => {
            document.removeEventListener('keydown', spend, true);
            document.removeEventListener('mousedown', spend, true);
            document.removeEventListener('focusin', moved, true);
        };
    }, []);

    useEffect(() => {
        const wanted = requestRef.current;
        const target = wanted ? targetOf(wanted, rootRef.current) : null;
        if (!target || document.activeElement === target) {
            return;
        }

        targetRef.current = target;
        placedAtRef.current = Date.now();
        target.focus();
        // again on the next frame, unless the user acted or the focus is there: it comes after the focus that a closing
        // Modal gives back to its opener
        requestAnimationFrame(() => {
            if (requestRef.current === wanted && document.activeElement !== target && target.isConnected) {
                target.focus();
            }
        });
    });
    return useCallback((wanted: FocusRequest, afterDialog = false) => {
        requestRef.current = wanted;
        targetRef.current = null;
        afterDialogRef.current = afterDialog;
        // a new request: its grace starts when it is made, and again when its element takes the focus
        placedAtRef.current = Date.now();
        setRequests(count => count + 1);
    }, []);
}

/**
 * Announces the list once per filter, page and order: an operation that refreshes the same page does not repeat it.
 * A user who does not manage the site hears the no-access text once.
 */
function useListAnnouncement(summary: SummaryData | undefined, page: SubmissionPage | undefined, filters: Filters,
                             announce: Announce, t: Translate) {
    const announcedRef = useRef<string | null>(null);
    const site = summary?.formidableQuarantine;
    useEffect(() => {
        if (summary && !site && announcedRef.current !== 'noAccess') {
            announcedRef.current = 'noAccess';
            announce(t('quarantine.noAccess'));
            return;
        }

        const key = JSON.stringify(filters);
        if (site && page && announcedRef.current !== key) {
            announcedRef.current = key;
            announce(listSentence(page, filters, site.forms, t));
        }
    }, [summary, site, page, filters, announce, t]);
}

/**
 * The manager screen of the quarantine of the site of the route. A switch to another site starts it again: the filters
 * and the open submission of a site mean nothing on another one.
 */
export const QuarantineApp = () => {
    const siteKey = currentSiteKey();
    return <QuarantineScreen key={siteKey ?? ''} siteKey={siteKey}/>;
};

const QuarantineScreen = ({siteKey}: {siteKey: string | undefined}) => {
    const {t} = useTranslation('formidable-quarantine');
    const language = uiContext().uilang || 'en';
    const {polite, assertive, announce} = useAnnouncer();

    const [filters, setFilters] = useState<Filters>(NO_FILTER);
    const [formFilterOpen, setFormFilterOpen] = useState(false);
    const [openId, setOpenId] = useState<string | null>(null);
    const [confirm, setConfirm] = useState<'RELEASE' | 'REJECT' | null>(null);
    const [confirmFailure, setConfirmFailure] = useState<string | null>(null);
    const [failure, setFailure] = useState<string | null>(null);
    // the operation that runs, with its submission: only the detail of that submission shows it as busy
    const [running, setRunning] = useState<{operation: QuarantineOperation; id: string} | null>(null);
    const runningHere = running && running.id === openId ? running.operation : null;
    const [changed, setChanged] = useState(false);
    const [reloading, setReloading] = useState(false);

    const rootRef = useRef<HTMLDivElement>(null);
    // the open submission as the last render left it, for an answer that arrives after the manager opened another one
    const openIdRef = useRef<string | null>(openId);
    openIdRef.current = openId;
    const focus = useFocusRequests(rootRef);

    // The three queries read formidableQuarantine(siteKey), which has no identifier: in the cache, the answer of one
    // would replace the fields of the others and make Apollo fetch them again. Each keeps its answer to itself.
    const summary = useQuery<SummaryData>(GET_QUARANTINE_SUMMARY, {
        variables: {siteKey},
        skip: !siteKey,
        fetchPolicy: 'no-cache',
        notifyOnNetworkStatusChange: true
    });
    const list = useQuery<ListData>(GET_QUARANTINE_LIST, {
        variables: {
            siteKey,
            formId: filters.formId,
            states: filters.state ? [filters.state] : null,
            offset: filters.offset,
            newestFirst: filters.sort === 'descending'
        },
        skip: !siteKey,
        fetchPolicy: 'no-cache',
        notifyOnNetworkStatusChange: true,
        onCompleted: data => {
            // an operation or another manager emptied the page: the screen asks the start of the last page of the list now
            const served = data?.formidableQuarantine?.submissions;
            const inside = served ? offsetInsideList(served.offset, served.items.length, served.totalCount) : null;
            if (inside !== null) {
                setFilters(current => (current.offset === inside ? current : {...current, offset: inside}));
            }
        }
    });
    // the previous answer stays on screen while the next one loads, so the focused control never unmounts. A failed
    // page or summary shows the failure of the list in place of its rows, never the answer of other filters nor the
    // earlier answer that Apollo keeps beside the error of a failed refetch; the filters keep the earlier counts and
    // forms. The failure holds until an answer arrives, so a Try again that runs keeps its banner and its button.
    const summaryData = summary.data ?? (summary.loading ? summary.previousData : undefined);
    const site = summaryData?.formidableQuarantine ?? null;
    const listFailedRef = useRef(false);
    if (!list.loading && !summary.loading) {
        listFailedRef.current = Boolean(list.error || summary.error);
    }

    const listFailed = listFailedRef.current;
    const page = listFailed ? undefined
        : (list.data ?? (list.loading ? list.previousData : undefined))?.formidableQuarantine?.submissions;
    // the Loader of LayoutContent replaces the whole screen: for the first answer alone, never for a retry
    const answeredRef = useRef(false);
    answeredRef.current = answeredRef.current || Boolean(summary.data || summary.error);

    const detail = useQuery<DetailData>(GET_QUARANTINE_SUBMISSION, {
        variables: {siteKey, id: openId},
        skip: !siteKey || !openId,
        fetchPolicy: 'no-cache'
    });
    // a failed reading of the detail shows its failure, never the earlier answer that Apollo keeps beside the error
    const detailFailed = Boolean(detail.error) && !detail.loading;
    const submission: HeldDetail | null = openId && !detailFailed ? detail.data?.formidableQuarantine?.submission ?? null : null;
    // the page or the detail answers null to a user who no longer manages the site, while the summary read on open
    // still names it: the screen then says that the user has no access
    const accessLost = list.data?.formidableQuarantine === null || (openId !== null && detail.data?.formidableQuarantine === null);
    const [operate] = useMutation<OperateData>(OPERATE_QUARANTINE_SUBMISSION, {fetchPolicy: 'no-cache'});

    useEffect(() => {
        // the first load: the sentence of the list replaces it once the page has arrived
        if (siteKey) {
            announce(t('quarantine.loading'));
        }
    }, [siteKey, announce, t]);
    useListAnnouncement(summaryData, list.data?.formidableQuarantine?.submissions, filters, announce, t);
    useEffect(() => {
        if (summary.error || list.error) {
            announce(t('quarantine.loadFailed'), true);
        }
    }, [summary.error, list.error, announce, t]);

    const changeFilters = useCallback((next: Partial<Filters>) => setFilters(current => ({...current, offset: 0, ...next})), []);
    const changePage = useCallback((offset: number) => setFilters(current => ({...current, offset})), []);
    const changeSort = useCallback((sort: SortDirection) => changeFilters({sort}), [changeFilters]);
    const closeFormFilter = useCallback(() => setFormFilterOpen(false), []);

    const open = useCallback((id: string) => {
        setOpenId(id);
        setChanged(false);
        setFailure(null);
        focus({kind: 'detailHeading'});
    }, [focus]);

    const close = () => {
        if (openId) {
            focus({kind: 'row', id: openId});
        }

        setOpenId(null);
        setChanged(false);
        setFailure(null);
    };

    const showChanged = () => {
        setConfirm(null);
        setChanged(true);
        focus({kind: 'reload'}, true);
    };

    /** The counts, the forms and the page again, after an operation or on Refresh. */
    const refetchList = () => Promise.all([summary.refetch(), list.refetch()]);

    /**
     * A refused or failed operation. A conflict shows in the alert container of the detail; another failure of Release or
     * Reject shows in the alert container of the confirm dialog, and one of Retry or Resume in the one of the detail.
     * Each container announces it. When the manager opened another submission meanwhile, the alert region of the screen
     * says it instead.
     */
    const operationFailed = (operation: QuarantineOperation, error: unknown, stillOpen: boolean) => {
        const changedUnderneath = isChangedUnderneath(error);
        if (!stillOpen) {
            announce(t(changedUnderneath ? 'quarantine.changed' : 'quarantine.failed'), true);
        } else if (changedUnderneath) {
            showChanged();
        } else if (CONFIRMED_OPERATIONS.has(operation)) {
            setConfirmFailure(t('quarantine.failed'));
        } else {
            setFailure(t('quarantine.failed'));
        }
    };

    /**
     * Where the focus goes after an operation that worked, and the list and the detail as the operation left them. The
     * detail and the focus follow the operation only while its submission is still the open one.
     */
    const afterOperation = async (operation: QuarantineOperation, after: HeldDetail, neighbour: string | null, stillOpen: boolean) => {
        setConfirm(null);
        const leaves = filters.state !== null && after.state !== filters.state;
        if (stillOpen && leaves) {
            // the submission leaves the filtered list: the detail closes, the focus goes to a neighbour row
            setOpenId(null);
            focus(neighbour ? {kind: 'row', id: neighbour} : {kind: 'empty'}, true);
        } else if (stillOpen) {
            focus({kind: 'detailHeading'}, true);
        }

        announce(t(`quarantine.done.${operation}`, {state: t(`quarantine.state.${after.state}`)}));
        try {
            await (stillOpen && !leaves ? Promise.all([refetchList(), detail.refetch()]) : refetchList());
        } catch {
            // the operation ran: only the reading of the list or of the detail failed
            announce(t('quarantine.loadFailed'), true);
        }
    };

    const run = async (operation: QuarantineOperation) => {
        const id = openId;
        if (!id) {
            return;
        }

        const rows = page?.items ?? [];
        const index = rows.findIndex(row => row.id === id);
        const neighbour = rows[index + 1]?.id ?? rows[index - 1]?.id ?? null;
        // the rows stay open to a click while the request runs: the answer reads which submission is open then
        const stillOpen = () => openIdRef.current === id;
        setRunning({operation, id});
        setConfirmFailure(null);
        setFailure(null);
        try {
            let after: HeldDetail | null;
            try {
                const result = await operate({variables: {siteKey, id, operation}});
                after = result.data?.formidableQuarantine?.operate ?? null;
            } catch (error) {
                operationFailed(operation, error, stillOpen());
                return;
            }

            if (after) {
                await afterOperation(operation, after, neighbour, stillOpen());
            } else if (stillOpen()) {
                // the user no longer manages the site: what the screen shows is out of date
                showChanged();
            } else {
                announce(t('quarantine.changed'), true);
            }
        } finally {
            // an operation started meanwhile on another submission keeps its own busy state
            setRunning(current => (current?.id === id ? null : current));
        }
    };

    const reload = async () => {
        setReloading(true);
        // the Reload button is busy while the request runs, and takes the focus back if it fails; when the detail
        // could not be read, its banner replaces Reload, and the focus goes to its Try again
        focus({kind: 'reload'});
        try {
            const [, again] = await Promise.all([refetchList(), detail.refetch()]);
            setChanged(false);
            const now = again.data?.formidableQuarantine?.submission;
            if (now) {
                announce(t('quarantine.reloaded', {state: t(`quarantine.state.${now.state}`)}));
                focus({kind: 'detailHeading'});
            } else {
                // the submission left the quarantine of the site: the detail closes on its row, or on the list
                announce(t('quarantine.detail.gone'));
                close();
            }
        } catch {
            announce(t('quarantine.loadFailed'), true);
        } finally {
            setReloading(false);
        }
    };

    const refresh = async () => {
        // the Refresh button is busy while the request runs: the focus comes back to it after
        focus({kind: 'refresh'});
        try {
            const [[, again]] = await Promise.all([refetchList(), openId ? detail.refetch() : Promise.resolve(null)]);
            const served = again.data?.formidableQuarantine?.submissions;
            if (site && served) {
                announce(listSentence(served, filters, site.forms, t));
            }
        } catch {
            announce(t('quarantine.loadFailed'), true);
        }
    };

    /**
     * Try again after a failed load: the focus goes to the heading of what came back, or back to Try again, whose
     * banner stays, when the load fails again.
     */
    const retry = async (load: () => Promise<unknown>, after: FocusRequest, where: 'list' | 'detail') => {
        try {
            await load();
            focus(after);
        } catch {
            focus({kind: 'tryAgain', where});
            announce(t('quarantine.loadFailed'), true);
        }
    };

    const banner = (role: string, variant: 'info' | 'warning' | 'danger', title: string, text: string, action?: React.ReactNode) => (
        <Banner role="note" variant={variant} title={title} data-sel-role={role}>
            <Typography>{text}</Typography>
            {action}
        </Banner>
    );

    const tryAgain = (onClick: () => void) => (
        <Button label={t('quarantine.tryAgain')} variant="outlined" className="fmdbq-block" data-sel-role="quarantine-try-again" onClick={onClick}/>
    );

    function body(): React.ReactNode {
        if (!siteKey) {
            return banner('quarantine-no-site', 'info', t('quarantine.noSiteTitle'), t('quarantine.noSite'));
        }

        if (!summaryData) {
            // before the first answer the Loader stands in; after a failure the banner stays while Try again runs
            return answeredRef.current ? banner('quarantine-load-failed', 'danger', t('quarantine.loadFailedTitle'), t('quarantine.loadFailed'),
                tryAgain(() => retry(refetchList, {kind: 'listHeading'}, 'list'))) : null;
        }

        if (!site || accessLost) {
            return banner('quarantine-no-access', 'warning', t('quarantine.noAccessTitle'), t('quarantine.noAccess'));
        }

        return listBody(site);
    }

    function listContent(): React.ReactNode {
        if (!page) {
            // Try again reads the counts and the forms with the page
            return listFailed
                ? banner('quarantine-list-failed', 'danger', t('quarantine.loadFailedTitle'), t('quarantine.loadFailed'),
                    tryAgain(() => retry(refetchList, {kind: 'listHeading'}, 'list')))
                : <Typography className="fmdbq-secondary">{t('quarantine.loading')}</Typography>;
        }

        const filtered = filters.state !== null || filters.formId !== null;
        if (page.items.length === 0 && !filtered) {
            // focusable from the code alone, for the focus after an operation that emptied the list
            return (
                <div tabIndex={-1} data-sel-role="quarantine-empty">
                    <EmptyData message={t('quarantine.empty')}/>
                </div>
            );
        }

        if (page.items.length === 0) {
            return (
                <div tabIndex={-1} className="fmdbq-no-match" data-sel-role="quarantine-no-match">
                    <EmptyData message={t('quarantine.filter.noMatch')}/>
                    <Button
                        label={t('quarantine.filter.clear')}
                        variant="outlined"
                        onClick={() => {
                            // the order is no filter: it stays
                            changeFilters({state: null, formId: null});
                            focus({kind: 'listHeading'});
                        }}
                    />
                </div>
            );
        }

        return (
            <SubmissionsTable
                rows={page.items}
                totalCount={page.totalCount}
                offset={page.offset}
                sortDirection={filters.sort}
                openId={openId}
                busy={list.loading}
                language={language}
                onOpen={open}
                onPage={changePage}
                onSort={changeSort}
            />
        );
    }

    function listBody(quarantine: QuarantineSummary): React.ReactNode {
        const words = filterWords(filters, quarantine.forms, t);
        const order = t(`quarantine.table.order.${filters.sort}`);
        return (
            <>
                {/* One card, as Form Results and module-management-community: the toolbar on top, the table under it,
                    the detail beside the table. */}
                <Paper hasPadding={false} className="fmdbq-card" data-sel-role="quarantine-card">
                    <div className="fmdbq-toolbar" data-sel-role="quarantine-toolbar">
                        <StateFilter counts={quarantine.counts} selected={filters.state} onSelect={state => changeFilters({state})}/>
                        <FormFilter
                            forms={quarantine.forms}
                            selected={filters.formId}
                            isOpen={formFilterOpen}
                            onOpen={() => setFormFilterOpen(true)}
                            onClose={closeFormFilter}
                            onSelect={formId => changeFilters({formId})}
                        />
                        <div className="fmdbq-toolbar-end">
                            <Button
                                variant="ghost"
                                label={t('quarantine.refresh')}
                                icon={<Reload aria-hidden="true"/>}
                                isLoading={list.loading || summary.loading}
                                data-sel-role="quarantine-refresh"
                                onClick={refresh}
                            />
                        </div>
                    </div>
                    <div className="fmdbq-card-body">
                        {/* the admin shell holds the main landmark: the list is a named region inside it */}
                        <section className="fmdbq-list" aria-labelledby={LIST_HEADING_ID}>
                            <h2 id={LIST_HEADING_ID} className="fmdbq-sr-only">
                                {words ? t('quarantine.table.headingFiltered', {order, filters: words}) : t('quarantine.table.heading', {order})}
                            </h2>
                            {listContent()}
                        </section>
                        {openId && (
                            <DetailFrame hasHeading={Boolean(submission)} onClose={close}>
                                {renderDetail()}
                            </DetailFrame>
                        )}
                    </div>
                </Paper>
                {confirm && submission && (
                    <ConfirmDialog
                        operation={confirm}
                        subject={rowLabel(submission, language, t)}
                        busy={runningHere === confirm}
                        failure={confirmFailure}
                        onConfirm={() => run(confirm)}
                        onCancel={() => {
                            setConfirm(null);
                            setConfirmFailure(null);
                            focus({kind: 'action', operation: confirm}, true);
                        }}
                    />
                )}
            </>
        );
    }

    function renderDetail(): React.ReactNode {
        if (!submission && detail.loading) {
            return <Typography className="fmdbq-secondary">{t('quarantine.detail.loading')}</Typography>;
        }

        if (detailFailed) {
            return banner('quarantine-detail-failed', 'danger', t('quarantine.loadFailedTitle'), t('quarantine.detail.loadFailed'),
                tryAgain(() => retry(() => detail.refetch(), {kind: 'detailHeading'}, 'detail')));
        }

        if (!submission) {
            return banner('quarantine-gone', 'info', t('quarantine.goneTitle'), t('quarantine.detail.gone'));
        }

        return (
            <SubmissionDetail
                submission={submission}
                language={language}
                changed={changed}
                failure={failure}
                reloading={reloading}
                actions={(
                    <ActionButtons
                        allowedOperations={submission.allowedOperations}
                        running={runningHere}
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

    return (
        // isolation keeps the z-indexes of the sticky head and pager of the DataTable inside the page, so a Modal,
        // portalled to the body with an overlay of no z-index, covers them (the note of module-management-community)
        <div ref={rootRef} className={ROOT_CLASS} data-sel-role="quarantine-app">
            <style>{QUARANTINE_CSS}</style>
            <LayoutContent
                hasPadding
                className="fmdbq-layout"
                isLoading={Boolean(siteKey) && !answeredRef.current}
                header={<Header title={t('quarantine.title')}/>}
            >
                {body()}
            </LayoutContent>
            {/* outside LayoutContent, which swaps its children for a Loader: the regions exist from the first render */}
            <LiveRegions polite={polite} assertive={assertive}/>
        </div>
    );
};
