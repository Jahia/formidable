// @vitest-environment jsdom
import React from 'react';
import {renderToStaticMarkup} from 'react-dom/server';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';
import type {HeldDetail, ListData} from './graphql';
import {ActionButtons, SubmissionDetail} from './components/SubmissionDetail';
import {SubmissionsTable} from './components/SubmissionsTable';
import type {SubmissionRow} from './graphql';
import type {QuarantineOperation} from './quarantine.utils';
import {QuarantineApp} from './QuarantineApp';

// The answer of the list query that each test sets; the screen reads it through useQuery. react-i18next with no
// instance answers the key: the tests read the keys.
const apollo = vi.hoisted(() => ({list: {loading: true} as {data?: ListData; loading: boolean; error?: Error}}));

vi.mock('@apollo/client', async importOriginal => ({
    ...await importOriginal<typeof import('@apollo/client')>(),
    useQuery: () => ({...apollo.list, refetch: vi.fn()}),
    useMutation: () => [vi.fn()]
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

const table = (offset: number, count: number, total: number, openId: string | null = null) => markupOf(
    <SubmissionsTable rows={rowsOf(count, offset)} totalCount={total} offset={offset} sortDirection="ascending"
                      openId={openId} busy={false} language="en" onOpen={vi.fn()} onPage={vi.fn()} onSort={vi.fn()}/>
);

describe('SubmissionsTable', () => {
    it('pages by 25 on the server: the pager reads the range of the page, of the total', () => {
        expect(table(25, 25, 63)).toContain('26-50 quarantine.paging.of 63');
        expect(table(50, 13, 63)).toContain('51-63 quarantine.paging.of 63');
    });

    it('disables the first and previous buttons on the first page, and next and last on the last', () => {
        expect(table(0, 25, 63)).toMatch(/disabled=""[^>]*data-testid="pagination-button-previous-page"/);
        expect(table(50, 13, 63)).toMatch(/disabled=""[^>]*data-testid="pagination-button-next-page"/);
    });

    it('opens a row through one named button, and takes the tab stop away from the row', () => {
        const markup = table(0, 2, 2, 'f/2026/11/26/held-0');
        expect(markup.match(/data-sel-role="quarantine-open"/g)).toHaveLength(2);
        expect(markup).toMatch(/aria-controls="fmdbq-detail" aria-expanded="true"/);
        expect(markup).not.toMatch(/<tr[^>]*data-sel-role="quarantine-row"[^>]*tabindex="0"/);
        expect(markup).not.toMatch(/<tr[^>]*tabindex="0"[^>]*data-sel-role="quarantine-row"/);
    });

    it('sorts by Received through a named button, and the header cell carries aria-sort', () => {
        const markup = table(0, 2, 2);
        expect(markup).toMatch(/aria-sort="ascending"/);
        expect(markup).toMatch(/<button[^>]*aria-label="quarantine.table.sort.ascending"[^>]*data-sel-role="quarantine-sort"/);
        expect(markup).toContain('aria-labelledby="fmdbq-list-heading"');
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
    audit: [{operation: 'run', user: null, from: 'held', to: 'needsReview', at: '2026-11-26T10:01:00Z'}],
    publicValues: null,
    sensitiveFields: ['iban'],
    allowedOperations: ['RETRY', 'RELEASE', 'REJECT']
};

const detail = (changed: boolean) => markupOf(
    <SubmissionDetail
        submission={SUBMISSION}
        language="en"
        headingRef={{current: null}}
        actions={null}
        changed={changed}
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

    it('shows the names of the sensitive fields and no value section when the form hides its values', () => {
        const markup = detail(false);
        expect(markup).toContain('<li>iban</li>');
        expect(markup).not.toContain('quarantine-values');
    });

    it('reads FAILED as a check error, not a fail', () => {
        expect(detail(false)).toContain('data-verdict="FAILED"');
        expect(detail(false)).toContain('quarantine.verdict.FAILED');
    });
});

describe('QuarantineApp', () => {
    beforeEach(() => {
        vi.stubGlobal('location', {pathname: '/jahia/jcontent/digitall/en/apps/formidableQuarantine'});
        vi.stubGlobal('contextJsParameters', {contextPath: '', uilang: 'en'});
    });

    afterEach(() => {
        vi.unstubAllGlobals();
    });

    it('shows the plain no-access text, never an error, when the API answers null', () => {
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
        apollo.list = {loading: false, error: new Error('Network error')};
        const markup = markupOf(<QuarantineApp/>);
        expect(markup).toContain('quarantine-load-failed');
        expect(markup).not.toContain('Network error');
    });

    it('shows the empty text when no submission is held', () => {
        apollo.list = {loading: false, data: {formidableQuarantine: {siteKey: 'digitall', forms: [],
            counts: [{state: 'HELD', count: 0}], submissions: {totalCount: 0, offset: 0, items: []}}}};
        expect(markupOf(<QuarantineApp/>)).toContain('quarantine-empty');
    });

    it('shows the h1, the filters and a table whose rows open through a named button', () => {
        apollo.list = {loading: false, data: {formidableQuarantine: {siteKey: 'digitall',
            forms: [{formId: 'f', formTitle: 'Contact', count: 1}],
            counts: [{state: 'NEEDS_REVIEW', count: 1}],
            submissions: {totalCount: 1, offset: 0, items: [{id: SUBMISSION.id, formId: 'f', formTitle: 'Contact',
                createdAt: SUBMISSION.createdAt, state: 'NEEDS_REVIEW', attempts: 3, reason: 'CHECK_FAILED',
                files: [{item: 'item0', name: 'cv.pdf'}]}]}}}};
        const markup = markupOf(<QuarantineApp/>);
        expect(markup).toMatch(/<h1[^>]*>quarantine\.title<\/h1>/);
        expect(markup.match(/<button[^>]*aria-pressed="false"/g)).toHaveLength(6);
        // the form filter: a chip that opens a dialog, named by its visible text
        expect(markup).toMatch(/<button[^>]*aria-haspopup="dialog" aria-expanded="false"[^>]*data-sel-role="quarantine-form-filter"/);
        // the list is a named region, and the DataTable is named by the same heading
        expect(markup).toMatch(/<section[^>]*aria-labelledby="fmdbq-list-heading"/);
        expect(markup).toMatch(/<h2 id="fmdbq-list-heading" class="fmdbq-sr-only">quarantine\.table\.heading<\/h2>/);
        expect(markup).toMatch(/<button[^>]*aria-controls="fmdbq-detail" aria-expanded="false"/);
        // the live regions sit outside LayoutContent, which swaps its children for a Loader
        expect(markup).toMatch(/<\/div><div role="status"/);
        expect(markup).toContain('moonstone-header');
    });
});
