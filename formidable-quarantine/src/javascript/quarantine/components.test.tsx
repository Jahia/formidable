// @vitest-environment jsdom
import React, {act} from 'react';
import {renderToStaticMarkup} from 'react-dom/server';
import {createRoot, type Root} from 'react-dom/client';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';
import type {DetailData, HeldDetail, ListData, SubmissionRow, SummaryData} from './graphql';
import {ActionButtons, DetailFrame, SubmissionDetail} from './components/SubmissionDetail';
import {rowLabel, SubmissionsTable} from './components/SubmissionsTable';
import type {QuarantineOperation} from './quarantine.utils';
import {QuarantineApp} from './QuarantineApp';
import en from '../../main/resources/javascript/locales/en.json';
import fr from '../../main/resources/javascript/locales/fr.json';
import de from '../../main/resources/javascript/locales/de.json';
import es from '../../main/resources/javascript/locales/es.json';

interface Answer<T> {
    data?: T;
    previousData?: T;
    loading: boolean;
    error?: Error;
}

// The answer of each query, by its operation name, that a test sets; the screen reads them through useQuery, and each
// call is recorded with its variables. react-i18next with no instance answers the key: the tests read the keys.
const apollo = vi.hoisted(() => ({
    summary: {loading: true} as Answer<SummaryData>,
    list: {loading: true} as Answer<ListData>,
    detail: {loading: false} as Answer<DetailData>,
    calls: [] as Array<{name: string; variables: Record<string, unknown>; fetchPolicy?: string}>,
    mutationPolicies: [] as Array<string | undefined>,
    operate: vi.fn(),
    // one refetch per query, so a test tells the counts and the forms from the page and the detail
    refetch: {summary: vi.fn(), list: vi.fn(), detail: vi.fn()}
}));

const allRefetches = () => Object.values(apollo.refetch);

vi.mock('@apollo/client', async importOriginal => ({
    ...await importOriginal<typeof import('@apollo/client')>(),
    useQuery: (query: {definitions: Array<{kind: string; name?: {value: string}}>},
               options: {variables: Record<string, unknown>; fetchPolicy?: string; onCompleted?: (data: unknown) => void}) => {
        // the operation, not the fragment that a query may define before it
        const name = query.definitions.find(definition => definition.kind === 'OperationDefinition')?.name?.value ?? '';
        apollo.calls.push({name, variables: options.variables, fetchPolicy: options.fetchPolicy});
        const kind = name.endsWith('Summary') ? 'summary' : name.endsWith('List') ? 'list' : 'detail';
        const answer = apollo[kind];
        if (answer.data && options.onCompleted) {
            // Apollo calls it once the answer arrived, after the render
            const {onCompleted} = options;
            const {data} = answer;
            queueMicrotask(() => onCompleted(data));
        }

        return {...answer, refetch: apollo.refetch[kind]};
    },
    useMutation: (_mutation: unknown, options?: {fetchPolicy?: string}) => {
        apollo.mutationPolicies.push(options?.fetchPolicy);
        return [apollo.operate];
    }
}));

const markupOf = (element: React.ReactElement) => renderToStaticMarkup(element);

/** The data-sel-role attributes of an action button, in the order of the markup. */
const actionsOf = (markup: string) => [...markup.matchAll(/data-sel-role="quarantine-action-([a-z]+)"/g)].map(match => match[1]);

const actions = (allowed: string[], running: QuarantineOperation | null = null) => markupOf(
    <ActionButtons allowedOperations={allowed} running={running} onOperation={vi.fn()}/>
);

describe('ActionButtons', () => {
    it('shows a button for each allowed operation alone, in the fixed order', () => {
        expect(actionsOf(actions(['REJECT', 'RELEASE', 'RETRY']))).toEqual(['retry', 'release', 'reject']);
        expect(actionsOf(actions(['RESUME']))).toEqual(['resume']);
    });

    it('shows a sentence, not a button, when no operation is allowed', () => {
        const markup = actions([]);
        expect(actionsOf(markup)).toEqual([]);
        expect(markup).toContain('quarantine-no-actions');
    });

    it('marks every button aria-disabled while one runs, and names the running one', () => {
        const markup = actions(['RETRY', 'REJECT'], 'REJECT');
        expect(markup.match(/aria-disabled="true"/g)).toHaveLength(2);
        expect(markup).toContain('quarantine.action.busy.REJECT');
    });

    it('puts the Reject button on the dark danger style', () => {
        expect(actions(['REJECT'])).toMatch(/fmdbq-reject/);
    });
});

const rowsOf = (count: number, from: number): SubmissionRow[] => Array.from({length: count}, (_, index) => ({
    id: `f/2026/11/26/held-${from + index}`, formId: 'f', formTitle: 'Contact', createdAt: '2026-11-26T10:00:00Z',
    state: 'NEEDS_REVIEW', attempts: 1, reason: null, files: [{item: 'item0', name: 'cv.pdf'}]
}));

const table = (offset: number, count: number, total: number, openId: string | null = null, sort: 'ascending' | 'descending' = 'ascending') => markupOf(
    <SubmissionsTable rows={rowsOf(count, offset)} totalCount={total} offset={offset} sortDirection={sort}
                      openId={openId} busy={false} language="en" onOpen={vi.fn()} onPage={vi.fn()} onSort={vi.fn()}/>
);

// A mounted screen, for the tests that press a control: React runs its effects inside act.
(globalThis as typeof globalThis & {IS_REACT_ACT_ENVIRONMENT?: boolean}).IS_REACT_ACT_ENVIRONMENT = true;
let mounted: {container: HTMLElement; root: Root} | null = null;

const mount = async (element: React.ReactElement) => {
    const container = document.createElement('div');
    document.body.appendChild(container);
    const root = createRoot(container);
    await act(async () => root.render(element));
    mounted = {container, root};
    return mounted;
};

const press = async (element: Element | null | undefined) => {
    expect(element, 'the control to press').toBeTruthy();
    await act(async () => (element as HTMLElement).click());
};

afterEach(() => {
    if (mounted) {
        const {container, root} = mounted;
        act(() => root.unmount());
        container.remove();
        mounted = null;
    }
});

describe('SubmissionsTable', () => {
    it('shows in each row the form, the date, the state, the attempts, the reason and the file names', () => {
        const rows = rowsOf(1, 0).map(row => ({...row, attempts: 4, reason: 'CHECK_FAILED',
            files: [{item: 'item0', name: 'cv.pdf'}, {item: 'item1', name: 'id.png'}, {item: 'item2', name: null}]}));
        const markup = markupOf(
            <SubmissionsTable rows={rows} totalCount={1} offset={0} sortDirection="ascending"
                              openId={null} busy={false} language="en" onOpen={vi.fn()} onPage={vi.fn()} onSort={vi.fn()}/>
        );
        const row = markup.slice(markup.indexOf('data-sel-role="quarantine-row"'));
        expect(row).toContain('quarantine.table.open');
        expect(row).toContain('Nov 26, 2026');
        expect(row).toContain('quarantine.state.NEEDS_REVIEW');
        expect(row).toMatch(/style="width:100px">4<\/td>/);
        expect(row).toContain('quarantine.reason.CHECK_FAILED <code>CHECK_FAILED</code>');
        expect(row).toMatch(/<li>cv\.pdf<\/li><li>id\.png<\/li><li>quarantine\.table\.moreFiles<\/li>/);
    });

    it('names the button of a row by its form and its date, and by the identifier of a form with no title', () => {
        const echo = (key: string, options?: Record<string, unknown>) => `${key} ${JSON.stringify(options)}`;
        const row = rowsOf(1, 0)[0];
        expect(rowLabel(row, 'en', echo)).toBe(`quarantine.table.open {"form":"Contact","date":"Nov 26, 2026, ${new Intl.DateTimeFormat('en',
            {timeStyle: 'short'}).format(new Date(row.createdAt))}"}`);
        expect(rowLabel({...row, formTitle: null}, 'en', echo)).toContain('"form":"f"');
    });

    it('pages by 25 on the server: the pager reads the range of the page, of the total', () => {
        expect(table(25, 25, 63)).toContain('26-50 quarantine.paging.of 63');
        expect(table(50, 13, 63)).toContain('51-63 quarantine.paging.of 63');
    });

    it('disables the first and previous buttons on the first page, and next and last on the last', () => {
        expect(table(0, 25, 63)).toMatch(/disabled=""[^>]*data-testid="pagination-button-previous-page"/);
        expect(table(50, 13, 63)).toMatch(/disabled=""[^>]*data-testid="pagination-button-next-page"/);
    });

    it('asks for the offset of the next and the previous page', async () => {
        const onPage = vi.fn();
        const {container} = await mount(
            <SubmissionsTable rows={rowsOf(25, 25)} totalCount={63} offset={25} sortDirection="ascending"
                              openId={null} busy={false} language="en" onOpen={vi.fn()} onPage={onPage} onSort={vi.fn()}/>
        );
        await press(container.querySelector('[data-testid="pagination-button-next-page"]'));
        await press(container.querySelector('[data-testid="pagination-button-previous-page"]'));
        expect(onPage.mock.calls).toEqual([[50], [0]]);
    });

    it('opens a row through one named button, marks that row alone expanded, and takes the tab stop away from the row', () => {
        const markup = table(0, 2, 2, 'f/2026/11/26/held-0');
        expect(markup.match(/data-sel-role="quarantine-open"/g)).toHaveLength(2);
        expect(markup.match(/aria-expanded="true"/g)).toHaveLength(1);
        expect(markup).toMatch(/aria-expanded="true"[^>]*data-id="f\/2026\/11\/26\/held-0"/);
        expect(markup).toMatch(/aria-expanded="false"[^>]*data-id="f\/2026\/11\/26\/held-1"/);
        expect(markup).not.toMatch(/<tr[^>]*data-sel-role="quarantine-row"[^>]*tabindex="0"/);
        expect(markup).not.toMatch(/<tr[^>]*tabindex="0"[^>]*data-sel-role="quarantine-row"/);
    });

    it('sorts by Received through a named button, and the header cell carries aria-sort in both orders', () => {
        const ascending = table(0, 2, 2);
        expect(ascending).toMatch(/aria-sort="ascending"/);
        expect(ascending).toMatch(/<button[^>]*aria-label="quarantine.table.sort.ascending"[^>]*data-sel-role="quarantine-sort"/);
        expect(ascending).toContain('aria-labelledby="fmdbq-list-heading"');

        const descending = table(0, 2, 2, null, 'descending');
        expect(descending).toMatch(/aria-sort="descending"/);
        expect(descending).not.toMatch(/aria-sort="ascending"/);
        expect(descending).toMatch(/<button[^>]*aria-label="quarantine.table.sort.descending"[^>]*data-sel-role="quarantine-sort"/);
    });
});

const SUBMISSION: HeldDetail = {
    id: 'f/2026/11/26/held-1',
    formId: 'f',
    formTitle: 'Contact',
    createdAt: '2026-11-26T10:00:00Z',
    state: 'NEEDS_REVIEW',
    attempts: 3,
    reason: 'CHECK_FAILED',
    files: [{item: 'item0', name: 'cv.pdf', mimeType: 'application/pdf', size: 1000, bytesPresent: true,
        checks: [{name: 'remoteCheck', verdict: 'FAILED', reasonCode: 'TIMEOUT'}]}],
    decision: {reason: 'CHECK_FAILED', item: 'item0', check: 'remoteCheck', at: '2026-11-26T10:01:00Z'},
    audit: [{operation: 'run', user: null, from: 'held', to: 'needsReview', at: '2026-11-26T10:01:00Z'},
        {operation: 'retry', user: 'quarManager', from: 'needsReview', to: 'held', at: '2026-11-26T10:02:00Z'}],
    publicValues: null,
    sensitiveFields: ['iban'],
    allowedOperations: ['RETRY', 'RELEASE', 'REJECT']
};

const detail = (changed: boolean, submission: HeldDetail = SUBMISSION, failure: string | null = null) => markupOf(
    <SubmissionDetail
        submission={submission}
        language="en"
        actions={null}
        changed={changed}
        failure={failure}
        reloading={false}
        onReload={vi.fn()}
    />
);

describe('SubmissionDetail', () => {
    it('shows the changed message and its Reload button inside the alert container', () => {
        const markup = detail(true);
        expect(markup).toMatch(/<div role="alert" data-sel-role="quarantine-changed">.*quarantine\.changed.*quarantine-reload/);
    });

    it('keeps the alert container mounted and empty while nothing changed', () => {
        expect(detail(false)).toContain('<div role="alert" data-sel-role="quarantine-changed"></div>');
    });

    it('shows the failure of a Retry or a Resume in the alert container', () => {
        expect(detail(false, SUBMISSION, 'quarantine.failed'))
            .toMatch(/<div role="alert" data-sel-role="quarantine-changed">.*data-sel-role="quarantine-failed".*quarantine\.failed/);
    });

    it('shows the names of the sensitive fields and no value section when the form hides its values', () => {
        const markup = detail(false);
        expect(markup).toContain('<li>iban</li>');
        expect(markup).not.toContain('quarantine-values');
        expect(markup).not.toContain('quarantine.detail.valuesDeleted');
    });

    it('shows the public values of a form that shows them, and says when they were deleted', () => {
        const shown = detail(false, {...SUBMISSION, publicValues: [{name: 'fullName', values: ['Ada']}, {name: 'topics', values: ['a', 'b']}]});
        expect(shown).toMatch(/data-sel-role="quarantine-values"><dt>fullName<\/dt><dd>Ada<\/dd><dt>topics<\/dt><dd>a, b<\/dd><\/dl>/);
        const deleted = detail(false, {...SUBMISSION, publicValues: []});
        expect(deleted).toContain('quarantine.detail.valuesDeleted');
        expect(deleted).not.toContain('quarantine-values');
    });

    it('shows the decision with its reason, its file and its check, and the audit in its order', () => {
        const markup = detail(false);
        expect(markup).toMatch(/data-sel-role="quarantine-decision">.*CHECK_FAILED.*<dd>cv\.pdf<\/dd>.*<dd>remoteCheck<\/dd>/);
        const audit = markup.slice(markup.indexOf('data-sel-role="quarantine-audit"'));
        expect(audit.indexOf('quarantine.audit.run')).toBeGreaterThan(-1);
        expect(audit.indexOf('quarantine.audit.run')).toBeLessThan(audit.indexOf('quarantine.audit.retry'));
        expect(audit).toContain('quarantine.audit.system');
        expect(audit).toContain('quarManager');
    });

    it('reads FAILED as a check error, not a fail, and names the table of the checks of each file', () => {
        expect(detail(false)).toContain('data-verdict="FAILED"');
        expect(detail(false)).toContain('quarantine.verdict.FAILED');
        expect(detail(false)).toContain('<code>TIMEOUT</code>');
        expect(detail(false)).toContain('aria-label="quarantine.detail.checksTable"');
    });
});

describe('DetailFrame', () => {
    it('is named by the heading of the detail, and by its own name while there is none', () => {
        expect(markupOf(<DetailFrame hasHeading onClose={vi.fn()}>x</DetailFrame>)).toContain('aria-labelledby="fmdbq-detail-title"');
        const bare = markupOf(<DetailFrame hasHeading={false} onClose={vi.fn()}>x</DetailFrame>);
        expect(bare).toContain('aria-label="quarantine.detail.region"');
        expect(bare).not.toContain('aria-labelledby');
    });
});

const SUMMARY: SummaryData = {formidableQuarantine: {siteKey: 'digitall',
    forms: [{formId: 'f', formTitle: 'Contact', count: 1}, {formId: 'g', formTitle: 'Claims', count: 1}],
    counts: [{state: 'NEEDS_REVIEW', count: 2}]}};

const listOf = (items: SubmissionRow[], offset = 0, totalCount = items.length): ListData =>
    ({formidableQuarantine: {siteKey: 'digitall', submissions: {totalCount, offset, items}}});

const ROW: SubmissionRow = {id: SUBMISSION.id, formId: 'f', formTitle: 'Contact', createdAt: SUBMISSION.createdAt,
    state: 'NEEDS_REVIEW', attempts: 3, reason: 'CHECK_FAILED', files: [{item: 'item0', name: 'cv.pdf'}]};

const listCalls = () => apollo.calls.filter(call => call.name.endsWith('List'));
const lastListVariables = () => listCalls().at(-1)?.variables;

describe('QuarantineApp', () => {
    beforeEach(() => {
        vi.stubGlobal('location', {pathname: '/jahia/jcontent/digitall/en/apps/formidableQuarantine'});
        vi.stubGlobal('contextJsParameters', {contextPath: '', uilang: 'en'});
        // the Menu of the form filter measures itself; jsdom has no ResizeObserver
        vi.stubGlobal('ResizeObserver', class {
            observe() {
                // nothing to measure in jsdom
            }

            unobserve() {
                // nothing to measure in jsdom
            }

            disconnect() {
                // nothing to measure in jsdom
            }
        });
        apollo.calls = [];
        apollo.summary = {loading: false, data: SUMMARY};
        apollo.list = {loading: false, data: listOf([ROW])};
        apollo.detail = {loading: false};
        apollo.operate.mockReset();
        allRefetches().forEach(refetch => {
            refetch.mockReset();
            refetch.mockResolvedValue({data: undefined});
        });
    });

    afterEach(() => {
        vi.unstubAllGlobals();
    });

    it('shows the plain no-access text, never an error, when the API answers null', () => {
        apollo.summary = {data: {formidableQuarantine: null}, loading: false};
        apollo.list = {data: {formidableQuarantine: null}, loading: false};
        const markup = markupOf(<QuarantineApp/>);
        expect(markup).toContain('data-sel-role="quarantine-no-access"');
        expect(markup).toContain('quarantine.noAccess');
        expect(markup).not.toContain('quarantine-table');
        // the live regions are there from the first render
        expect(markup).toContain('role="status"');
        expect(markup).toContain('role="alert"');
    });

    it('shows the load failure with Try again when the request fails', () => {
        apollo.summary = {loading: false, error: new Error('Network error')};
        const markup = markupOf(<QuarantineApp/>);
        expect(markup).toMatch(/data-sel-role="quarantine-load-failed".*<button[^>]*>.*quarantine\.tryAgain/s);
        expect(markup).not.toContain('Network error');
    });

    it('keeps each query and the operation out of the cache, whose shared root field would mix their answers', () => {
        apollo.mutationPolicies = [];
        markupOf(<QuarantineApp/>);
        expect(new Set(apollo.calls.map(call => call.name))).toEqual(new Set([
            'GetFormidableQuarantineSummary', 'GetFormidableQuarantineList', 'GetFormidableQuarantineSubmission']));
        apollo.calls.forEach(call => expect(call.fetchPolicy, call.name).toBe('no-cache'));
        expect(apollo.mutationPolicies.length).toBeGreaterThan(0);
        apollo.mutationPolicies.forEach(policy => expect(policy).toBe('no-cache'));
    });

    it('shows the empty text when no submission is held', () => {
        apollo.list = {loading: false, data: listOf([])};
        expect(markupOf(<QuarantineApp/>)).toContain('quarantine-empty');
    });

    it('shows the h1, the filters and a table whose rows open through a named button', () => {
        const markup = markupOf(<QuarantineApp/>);
        expect(markup).toMatch(/<h1[^>]*>quarantine\.title<\/h1>/);
        expect(markup.match(/<button[^>]*aria-pressed="false"/g)).toHaveLength(6);
        // the form filter: a chip that opens a dialog, named by its visible text
        expect(markup).toMatch(/<button[^>]*aria-haspopup="dialog" aria-expanded="false"[^>]*data-sel-role="quarantine-form-filter"/);
        // the list is a named region, and the DataTable is named by the same heading
        expect(markup).toMatch(/<section[^>]*aria-labelledby="fmdbq-list-heading"/);
        expect(markup).toMatch(/<h2 id="fmdbq-list-heading" class="fmdbq-sr-only">quarantine\.table\.heading<\/h2>/);
        expect(markup).toMatch(/<button[^>]*aria-controls="fmdbq-detail" aria-expanded="false"/);
        // one card, as Form Results: the toolbar with the filters and Refresh on top, the table under it
        expect(markup).toMatch(/data-sel-role="quarantine-card".*data-sel-role="quarantine-toolbar".*quarantine-form-filter.*quarantine-refresh.*data-sel-role="quarantine-table"/s);
        // the live regions sit outside LayoutContent, which swaps its children for a Loader
        expect(markup).toMatch(/<\/div><div role="status"/);
        expect(markup).toContain('moonstone-header');
    });

    it('shows the failure of the page, never the rows of other filters, when the page request fails', () => {
        apollo.list = {loading: false, error: new Error('Network error'), previousData: listOf([ROW])};
        const markup = markupOf(<QuarantineApp/>);
        expect(markup).toContain('quarantine-list-failed');
        expect(markup).not.toContain('quarantine-row');
        // the filters stay, so the focused control does not unmount
        expect(markup).toContain('quarantine-state-filter');
    });

    it('sends the state, the form and the order of the filters with the page query, and only the page query', async () => {
        const {container} = await mount(<QuarantineApp/>);
        const summaries = apollo.calls.filter(call => call.name.endsWith('Summary')).length;

        await press(container.querySelector('button[data-state="NEEDS_REVIEW"]'));
        expect(lastListVariables()).toMatchObject({states: ['NEEDS_REVIEW'], formId: null, offset: 0, newestFirst: false});

        await press(container.querySelector('[data-sel-role="quarantine-form-filter"]'));
        const claims = Array.from(document.querySelectorAll<HTMLInputElement>('[data-sel-role="quarantine-form-editor"] input[type="radio"]'))
            .find(radio => radio.value === 'g');
        await press(claims);
        expect(lastListVariables()).toMatchObject({states: ['NEEDS_REVIEW'], formId: 'g'});

        await press(container.querySelector('[data-sel-role="quarantine-sort"]'));
        expect(lastListVariables()).toMatchObject({formId: 'g', newestFirst: true});

        // a change of filter or order asks the page again through its variables, never the counts and the forms with it
        expect(apollo.calls.filter(call => call.name.endsWith('Summary')).every(call => call.variables.siteKey === 'digitall')).toBe(true);
        expect(new Set(apollo.calls.filter(call => call.name.endsWith('Summary')).map(call => JSON.stringify(call.variables))).size).toBe(1);
        expect(summaries).toBeGreaterThan(0);
        allRefetches().forEach(refetch => expect(refetch).not.toHaveBeenCalled());
    });

    it('asks the start of the last page when an answer brought an empty page past the end', async () => {
        apollo.list = {loading: false, data: listOf(rowsOf(25, 0), 0, 50)};
        const {container, root} = await mount(<QuarantineApp/>);
        await press(container.querySelector('[data-testid="pagination-button-next-page"]'));
        expect(lastListVariables()).toMatchObject({offset: 25});

        // the last row of page 2 was released: 25 submissions are left, all on page 1
        apollo.list = {loading: false, data: listOf([], 25, 25)};
        await act(async () => root.render(<QuarantineApp/>));
        expect(lastListVariables()).toMatchObject({offset: 0});
    });

    it('clears the state and the form on Clear filters, and keeps the order', async () => {
        const {container, root} = await mount(<QuarantineApp/>);
        await press(container.querySelector('[data-sel-role="quarantine-sort"]'));
        await press(container.querySelector('button[data-state="REJECTED"]'));
        apollo.list = {loading: false, data: listOf([])};
        await act(async () => root.render(<QuarantineApp/>));
        const clear = container.querySelector('[data-sel-role="quarantine-no-match"] button');
        expect(clear?.textContent).toBe('quarantine.filter.clear');
        await press(clear);
        expect(lastListVariables()).toMatchObject({states: null, formId: null, offset: 0, newestFirst: true});
    });

    it('starts again, with no filter and no open submission, on a switch to another site', async () => {
        const {container, root} = await mount(<QuarantineApp/>);
        await press(container.querySelector('button[data-state="NEEDS_REVIEW"]'));
        expect(container.querySelector('button[data-state="NEEDS_REVIEW"]')?.getAttribute('aria-pressed')).toBe('true');

        vi.stubGlobal('location', {pathname: '/jahia/jcontent/othersite/en/apps/formidableQuarantine'});
        await act(async () => root.render(<QuarantineApp/>));
        expect(container.querySelector('button[data-state="NEEDS_REVIEW"]')?.getAttribute('aria-pressed')).toBe('false');
        expect(lastListVariables()).toMatchObject({siteKey: 'othersite', states: null});
    });

    describe('an operation', () => {
        const openAndRetry = async () => {
            apollo.detail = {loading: false, data: {formidableQuarantine: {siteKey: 'digitall', submission: SUBMISSION}}};
            const {container} = await mount(<QuarantineApp/>);
            await press(container.querySelector('[data-sel-role="quarantine-open"]'));
            await press(container.querySelector('[data-sel-role="quarantine-action-retry"]'));
            return container;
        };

        it('refused with the code CONFLICT shows the changed message with its Reload button', async () => {
            apollo.operate.mockRejectedValue({graphQLErrors: [{message: 'any text', extensions: {code: 'CONFLICT'}}]});
            const container = await openAndRetry();
            expect(apollo.operate).toHaveBeenCalledWith({variables: {siteKey: 'digitall', id: SUBMISSION.id, operation: 'RETRY'}});
            expect(container.querySelector('[data-sel-role="quarantine-changed"]')?.textContent).toContain('quarantine.changed');
            expect(container.querySelector('[data-sel-role="quarantine-reload"]')).not.toBeNull();
            expect(container.querySelector('[data-sel-role="quarantine-failed"]')).toBeNull();
        });

        it('answered null, as for a user who no longer manages the site, shows the changed message', async () => {
            apollo.operate.mockResolvedValue({data: {formidableQuarantine: null}});
            const container = await openAndRetry();
            expect(container.querySelector('[data-sel-role="quarantine-changed"]')?.textContent).toContain('quarantine.changed');
        });

        it('that failed with no code shows a visible failure, not the changed message', async () => {
            apollo.operate.mockRejectedValue(new Error('Network error'));
            const container = await openAndRetry();
            expect(container.querySelector('[data-sel-role="quarantine-failed"]')?.textContent).toContain('quarantine.failed');
            expect(container.querySelector('[data-sel-role="quarantine-changed"]')?.textContent).not.toContain('quarantine.changed');
            expect(container.textContent).not.toContain('Network error');
        });

        it('that ran reads the counts, the forms, the page and the detail again', async () => {
            apollo.operate.mockResolvedValue({data: {formidableQuarantine: {operate: {...SUBMISSION, state: 'HELD'}}}});
            await openAndRetry();
            expect(apollo.refetch.summary).toHaveBeenCalledTimes(1);
            expect(apollo.refetch.list).toHaveBeenCalledTimes(1);
            expect(apollo.refetch.detail).toHaveBeenCalledTimes(1);
        });

        it('that ran, followed by a failed reading of the list, is not reported as failed', async () => {
            apollo.operate.mockResolvedValue({data: {formidableQuarantine: {operate: {...SUBMISSION, state: 'HELD'}}}});
            allRefetches().forEach(refetch => refetch.mockRejectedValue(new Error('Network error')));
            const container = await openAndRetry();
            expect(apollo.refetch.summary).toHaveBeenCalled();
            expect(container.querySelector('[data-sel-role="quarantine-failed"]')).toBeNull();
            expect(container.querySelector('[data-sel-role="quarantine-changed"]')?.textContent).toBe('');
        });

        it('that failed is announced once, by the alert container of the detail', async () => {
            apollo.operate.mockRejectedValue(new Error('Network error'));
            const container = await openAndRetry();
            // the announcer sets its message on the next frame
            await act(async () => new Promise(resolve => setTimeout(resolve, 50)));
            expect(container.querySelector('[data-sel-role="quarantine-failed"]')?.textContent).toContain('quarantine.failed');
            expect(container.querySelector('[data-sel-role="quarantine-alert"]')?.textContent).not.toContain('quarantine.failed');
        });

        it('answered after the manager opened another submission leaves the detail of that one alone', async () => {
            const other: SubmissionRow = {...ROW, id: 'f/2026/11/26/held-2'};
            apollo.list = {loading: false, data: listOf([ROW, other])};
            apollo.detail = {loading: false, data: {formidableQuarantine: {siteKey: 'digitall', submission: SUBMISSION}}};
            let refuse: (reason: unknown) => void = () => undefined;
            apollo.operate.mockReturnValue(new Promise((_resolve, reject) => {
                refuse = reject;
            }));
            const {container} = await mount(<QuarantineApp/>);
            await press(container.querySelector(`[data-sel-role="quarantine-open"][data-id="${ROW.id}"]`));
            await press(container.querySelector('[data-sel-role="quarantine-action-retry"]'));
            // the manager opens the other row while the Retry of the first one runs
            await press(container.querySelector(`[data-sel-role="quarantine-open"][data-id="${other.id}"]`));
            await act(async () => refuse({graphQLErrors: [{message: 'any text', extensions: {code: 'CONFLICT'}}]}));
            await act(async () => new Promise(resolve => setTimeout(resolve, 50)));

            expect(container.querySelector('[data-sel-role="quarantine-detail"]')).not.toBeNull();
            expect(container.querySelector('[data-sel-role="quarantine-changed"]')?.textContent).toBe('');
            expect(container.querySelector('[data-sel-role="quarantine-alert"]')?.textContent).toContain('quarantine.changed');
        });

        /** Opens the first row, starts its Retry with an answer the test gives later, then opens the second row. */
        const retryThenOpenOther = async () => {
            const other: SubmissionRow = {...ROW, id: 'f/2026/11/26/held-2'};
            apollo.list = {loading: false, data: listOf([ROW, other])};
            apollo.detail = {loading: false, data: {formidableQuarantine: {siteKey: 'digitall', submission: SUBMISSION}}};
            const answer: {resolve: (value: unknown) => void; reject: (reason: unknown) => void} = {resolve: () => undefined, reject: () => undefined};
            apollo.operate.mockReturnValue(new Promise((resolve, reject) => {
                answer.resolve = resolve;
                answer.reject = reject;
            }));
            const {container} = await mount(<QuarantineApp/>);
            await press(container.querySelector(`[data-sel-role="quarantine-open"][data-id="${ROW.id}"]`));
            await press(container.querySelector('[data-sel-role="quarantine-action-retry"]'));
            await press(container.querySelector(`[data-sel-role="quarantine-open"][data-id="${other.id}"]`));
            return {container, answer};
        };

        const settle = async () => act(async () => new Promise(resolve => setTimeout(resolve, 50)));

        it('that runs shows no busy button on the submission opened meanwhile', async () => {
            const {container, answer} = await retryThenOpenOther();
            const retry = container.querySelector('[data-sel-role="quarantine-action-retry"]');
            expect(retry?.textContent).toBe('quarantine.action.RETRY');
            expect(retry?.getAttribute('aria-disabled')).toBe('false');
            await act(async () => answer.resolve({data: {formidableQuarantine: {operate: SUBMISSION}}}));
        });

        it('that failed with no code after another submission was opened says so in the alert region alone', async () => {
            const {container, answer} = await retryThenOpenOther();
            await act(async () => answer.reject(new Error('Network error')));
            await settle();
            expect(container.querySelector('[data-sel-role="quarantine-failed"]')).toBeNull();
            expect(container.querySelector('[data-sel-role="quarantine-changed"]')?.textContent).toBe('');
            expect(container.querySelector('[data-sel-role="quarantine-alert"]')?.textContent).toContain('quarantine.failed');
        });

        it('answered null after another submission was opened says so in the alert region alone', async () => {
            const {container, answer} = await retryThenOpenOther();
            await act(async () => answer.resolve({data: {formidableQuarantine: null}}));
            await settle();
            expect(container.querySelector('[data-sel-role="quarantine-changed"]')?.textContent).toBe('');
            expect(container.querySelector('[data-sel-role="quarantine-alert"]')?.textContent).toContain('quarantine.changed');
        });

        it('that took its submission out of the filter keeps open the submission opened meanwhile', async () => {
            const other: SubmissionRow = {...ROW, id: 'f/2026/11/26/held-2'};
            apollo.list = {loading: false, data: listOf([ROW, other])};
            apollo.detail = {loading: false, data: {formidableQuarantine: {siteKey: 'digitall', submission: SUBMISSION}}};
            let answer: (value: unknown) => void = () => undefined;
            apollo.operate.mockReturnValue(new Promise(resolve => {
                answer = resolve;
            }));
            const {container} = await mount(<QuarantineApp/>);
            await press(container.querySelector('button[data-state="NEEDS_REVIEW"]'));
            await press(container.querySelector(`[data-sel-role="quarantine-open"][data-id="${ROW.id}"]`));
            await press(container.querySelector('[data-sel-role="quarantine-action-retry"]'));
            await press(container.querySelector(`[data-sel-role="quarantine-open"][data-id="${other.id}"]`));
            // the first submission went back to Held: it leaves the filter on Needs review
            await act(async () => answer({data: {formidableQuarantine: {operate: {...SUBMISSION, state: 'HELD'}}}}));

            expect(container.querySelector('[data-sel-role="quarantine-detail"]')).not.toBeNull();
            expect(container.querySelector(`[data-sel-role="quarantine-open"][data-id="${other.id}"]`)?.getAttribute('aria-expanded')).toBe('true');
            expect(apollo.refetch.summary).toHaveBeenCalled();
        });
    });
});

describe('the locales', () => {
    const keysOf = (value: unknown, prefix = ''): string[] => (typeof value === 'object' && value !== null
        ? Object.entries(value).flatMap(([key, child]) => keysOf(child, prefix ? `${prefix}.${key}` : key))
        : [prefix]);

    it('hold the same keys in English, French, German and Spanish', () => {
        const english = keysOf(en).sort();
        expect(english.length).toBeGreaterThan(100);
        for (const other of [fr, de, es]) {
            expect(keysOf(other).sort()).toEqual(english);
        }
    });
});
