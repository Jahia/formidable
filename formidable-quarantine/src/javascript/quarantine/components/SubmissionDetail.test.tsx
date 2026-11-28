// @vitest-environment jsdom
import React from 'react';
import {renderToStaticMarkup} from 'react-dom/server';
import {describe, expect, it, vi} from 'vitest';
import type {HeldDetail} from '../graphql';
import {SubmissionDetail} from './SubmissionDetail';

// a t that shows the options it gets, so a test reads what a label is built from
vi.mock('react-i18next', () => ({
    useTranslation: () => ({t: (key: string, options?: Record<string, unknown>) => (options ? `${key} ${JSON.stringify(options)}` : key)})
}));

const SUBMISSION: HeldDetail = {
    id: 'f/2026/11/26/held-1', formId: 'f', formTitle: 'Contact', createdAt: '2026-11-26T10:00:00Z', state: 'NEEDS_REVIEW',
    attempts: 1, reason: null,
    files: [
        {item: 'item0', name: 'cv.pdf', mimeType: 'application/pdf', size: 10, bytesPresent: true,
            checks: [{name: 'remoteCheck', verdict: 'PASS', reasonCode: null}]},
        {item: 'item1', name: null, mimeType: null, size: null, bytesPresent: true,
            checks: [{name: 'remoteCheck', verdict: 'PASS', reasonCode: null}]}
    ],
    decision: null, audit: [], publicValues: null, sensitiveFields: [], allowedOperations: []
};

describe('SubmissionDetail', () => {
    it('names the table of the checks of each file by the name of the file, else by its item', () => {
        const markup = renderToStaticMarkup(
            <SubmissionDetail submission={SUBMISSION} language="en" actions={null} changed={false} reloading={false} onReload={vi.fn()}/>
        );
        expect(markup).toContain('aria-label="quarantine.detail.checksTable {&quot;name&quot;:&quot;cv.pdf&quot;}"');
        expect(markup).toContain('aria-label="quarantine.detail.checksTable {&quot;name&quot;:&quot;item1&quot;}"');
    });
});
