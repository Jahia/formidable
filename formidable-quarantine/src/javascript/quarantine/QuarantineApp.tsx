import React, {useCallback, useEffect, useRef, useState} from 'react';
import {useMutation, useQuery} from '@apollo/client';
import {Banner, Button, EmptyData, Header, LayoutContent, Paper, Reload, Typography} from '@jahia/moonstone';
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
    | {kind: 'row'; id: string}
    | {kind: 'action'; operation: QuarantineOperation};

type Translate = (key: string, options?: Record<string, unknown>) => string;

interface Filters {
    state: QuarantineState | null;
    formId: string | null;
    offset: number;
    sort: SortDirection;
}

const NO_FILTER: Filters = {state: null, formId: null, offset: 0, sort: 'ascending'};

/** The words that name the filters, for the heading of the table and the announcement of a page. */
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

function targetOf(wanted: FocusRequest, root: HTMLElement | null): HTMLElement | null {
    const listHeading = () => {
        const heading = root?.querySelector<HTMLElement>('h1') ?? null;
        heading?.setAttribute('tabindex', '-1');
        return heading;
    };
    const detailHeading = () => document.getElementById('fmdbq-detail-title');
    switch (wanted.kind) {
        case 'detailHeading':
            return detailHeading();
        case 'listHeading':
            return listHeading();
        case 'reload':
            return root?.querySelector<HTMLElement>('[data-sel-role="quarantine-reload"]') ?? null;
        case 'row':
            return root?.querySelector<HTMLElement>(`[data-sel-role="quarantine-open"][data-id="${CSS.escape(wanted.id)}"]`) ?? listHeading();
        default:
            return root?.querySelector<HTMLElement>(`[data-sel-role="quarantine-action-${wanted.operation.toLowerCase()}"]`) ?? detailHeading();
    }
}

/** How long after a dialog closed a move of the focus elsewhere is the Modal giving it back, not the user, in ms. */
const FOCUS_GRACE = 300;

/**
 * Moves the focus to the element a request names, as soon as it is rendered, and keeps it there until something else
 * takes it: the request stays after each render, because a later render can re-mount the element (the data of the
 * detail arriving), and a closing Modal gives the focus back to its opener after the render. A key or pointer press,
 * or the focus moving to another element, such as a screen reader moving it, spends the request. Only a request made
 * as a dialog closes lets a move pass during the grace time: the Modal giving the focus back to its opener.
 */
function useFocusRequests(rootRef: React.RefObject<HTMLElement>) {
    const requestRef = useRef<FocusRequest | null>(null);
    const targetRef = useRef<HTMLElement | null>(null);
    const placedAtRef = useRef(0);
    const afterDialogRef = useRef(false);

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
    }, []);
}

/** Announces the list once per filter, page and order: an operation that refreshes the same page does not repeat it. */
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

export const QuarantineApp = () => {
    const {t} = useTranslation('formidable-quarantine');
    const siteKey = currentSiteKey();
    const language = uiContext().uilang || 'en';
    const {polite, assertive, announce} = useAnnouncer();

    const [filters, setFilters] = useState<Filters>(NO_FILTER);
    const [formFilterOpen, setFormFilterOpen] = useState(false);
    const [openId, setOpenId] = useState<string | null>(null);
    const [confirm, setConfirm] = useState<'RELEASE' | 'REJECT' | null>(null);
    const [confirmFailure, setConfirmFailure] = useState<string | null>(null);
    const [running, setRunning] = useState<QuarantineOperation | null>(null);
    const [changed, setChanged] = useState(false);
    const [reloading, setReloading] = useState(false);

    const rootRef = useRef<HTMLDivElement>(null);
    const detailHeadingRef = useRef<HTMLHeadingElement>(null);
    const focus = useFocusRequests(rootRef);

    const list = useQuery<ListData>(GET_QUARANTINE_LIST, {
        variables: {
            siteKey,
            formId: filters.formId,
            states: filters.state ? [filters.state] : null,
            offset: filters.offset,
            newestFirst: filters.sort === 'descending'
        },
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
    const closeFormFilter = useCallback(() => setFormFilterOpen(false), []);

    const open = useCallback((id: string) => {
        setOpenId(id);
        setChanged(false);
        focus({kind: 'detailHeading'});
    }, [focus]);

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
        focus({kind: 'reload'}, true);
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
            if (filters.state !== null && after.state !== filters.state) {
                // the submission leaves the filtered list: the detail closes, the focus goes to a neighbour row
                setOpenId(null);
                focus(neighbour ? {kind: 'row', id: neighbour} : {kind: 'listHeading'}, true);
                await list.refetch();
            } else {
                focus({kind: 'detailHeading'}, true);
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

    const refresh = async () => {
        const [again] = await Promise.all([list.refetch(), openId ? detail.refetch() : Promise.resolve(null)]);
        announce(listSentence(again.data?.formidableQuarantine ?? null, filters, t));
    };

    const banner = (role: string, variant: 'info' | 'warning' | 'danger', title: string, text: string, action?: React.ReactNode) => (
        <Banner role="note" variant={variant} title={title} data-sel-role={role}>
            <Typography>{text}</Typography>
            {action}
        </Banner>
    );

    function body(): React.ReactNode {
        if (!siteKey) {
            return banner('quarantine-no-site', 'info', t('quarantine.noSiteTitle'), t('quarantine.noSite'));
        }

        if (!listData) {
            return banner('quarantine-load-failed', 'danger', t('quarantine.loadFailedTitle'), t('quarantine.loadFailed'), (
                <Button label={t('quarantine.tryAgain')} variant="outlined" className="fmdbq-block" onClick={() => list.refetch()}/>
            ));
        }

        if (!quarantine) {
            return banner('quarantine-no-access', 'warning', t('quarantine.noAccessTitle'), t('quarantine.noAccess'));
        }

        return listBody(quarantine);
    }

    function listBody(site: QuarantineList): React.ReactNode {
        const page = site.submissions;
        const words = filterWords(filters, site, t);
        const order = t(`quarantine.table.order.${filters.sort}`);
        const filtered = filters.state !== null || filters.formId !== null;
        return (
            <>
                {/* One card, as Form Results and module-management-community: the toolbar on top, the table under it,
                    the detail beside the table. */}
                <Paper hasPadding={false} className="fmdbq-card" data-sel-role="quarantine-card">
                    <div className="fmdbq-toolbar" data-sel-role="quarantine-toolbar">
                        <StateFilter counts={site.counts} selected={filters.state} onSelect={state => changeFilters({state})}/>
                        <FormFilter
                            forms={site.forms}
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
                                isLoading={list.loading}
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
                            {page.items.length === 0 && !filtered && (
                                <EmptyData message={t('quarantine.empty')} data-sel-role="quarantine-empty"/>
                            )}
                            {page.items.length === 0 && filtered && (
                                <div className="fmdbq-no-match" data-sel-role="quarantine-no-match">
                                    <EmptyData message={t('quarantine.filter.noMatch')}/>
                                    <Button
                                        label={t('quarantine.filter.clear')}
                                        variant="outlined"
                                        onClick={() => {
                                            setFilters(NO_FILTER);
                                            focus({kind: 'listHeading'});
                                        }}
                                    />
                                </div>
                            )}
                            {page.items.length > 0 && (
                                <SubmissionsTable
                                    rows={page.items}
                                    totalCount={page.totalCount}
                                    offset={page.offset}
                                    sortDirection={filters.sort}
                                    openId={openId}
                                    busy={list.loading}
                                    language={language}
                                    onOpen={open}
                                    onPage={offset => setFilters(current => ({...current, offset}))}
                                    onSort={sort => changeFilters({sort})}
                                />
                            )}
                        </section>
                        {openId && (
                            <DetailFrame onClose={close}>
                                {renderDetail()}
                            </DetailFrame>
                        )}
                    </div>
                </Paper>
                {confirm && submission && (
                    <ConfirmDialog
                        operation={confirm}
                        subject={rowLabel(submission, language, t)}
                        busy={running === confirm}
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

        if (detail.error && !detail.data) {
            return banner('quarantine-detail-failed', 'danger', t('quarantine.loadFailedTitle'), t('quarantine.detail.loadFailed'), (
                <Button label={t('quarantine.tryAgain')} variant="outlined" className="fmdbq-block" onClick={() => detail.refetch()}/>
            ));
        }

        if (!submission) {
            return banner('quarantine-gone', 'info', t('quarantine.goneTitle'), t('quarantine.detail.gone'));
        }

        return (
            <SubmissionDetail
                submission={submission}
                language={language}
                headingRef={detailHeadingRef}
                changed={changed}
                reloading={reloading}
                actions={(
                    <ActionButtons
                        allowedOperations={submission.allowedOperations}
                        running={running}
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
                isLoading={Boolean(siteKey) && !listData && list.loading}
                header={<Header title={t('quarantine.title')}/>}
            >
                {body()}
            </LayoutContent>
            {/* outside LayoutContent, which swaps its children for a Loader: the regions exist from the first render */}
            <LiveRegions polite={polite} assertive={assertive}/>
        </div>
    );
};
