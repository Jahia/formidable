// @vitest-environment jsdom
import React from 'react';
import {renderToStaticMarkup} from 'react-dom/server';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';
import type {HeldDetail, ListData} from './graphql';
import {ActionButtons, SubmissionDetail} from './components/SubmissionDetail';
import {Paging} from './components/SubmissionsTable';
import {pageRange, type QuarantineOperation} from './quarantine.utils';
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
    <ActionButtons allowedOperations={allowed} running={running} actionButtonsRef={{current: new Map()}} onOperation={vi.fn()}/>
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

describe('Paging', () => {
    it('marks Previous aria-disabled on the first page, and keeps it a button', () => {
        const markup = markupOf(<Paging range={pageRange(0, 25, 63)} onPage={vi.fn()}/>);
        expect(markup).toMatch(/aria-disabled="true"[^>]*data-sel-role="quarantine-page-previous"/);
        expect(markup).toMatch(/aria-disabled="false"[^>]*data-sel-role="quarantine-page-next"/);
        expect(markup).toContain('role="group"');
    });

    it('marks Next aria-disabled on the last page', () => {
        const markup = markupOf(<Paging range={pageRange(50, 13, 63)} onPage={vi.fn()}/>);
        expect(markup).toMatch(/aria-disabled="false"[^>]*data-sel-role="quarantine-page-previous"/);
        expect(markup).toMatch(/aria-disabled="true"[^>]*data-sel-role="quarantine-page-next"/);
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
        reloadRef={{current: null}}
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
        expect(markup).toMatch(/<label for="[^"]+">quarantine\.filter\.form<\/label><select id=/);
        expect(markup).toMatch(/<th scope="row"[^>]*><button[^>]*aria-controls="fmdbq-detail" aria-expanded="false"/);
        expect(markup).toContain('aria-sort="ascending"');
    });
});
