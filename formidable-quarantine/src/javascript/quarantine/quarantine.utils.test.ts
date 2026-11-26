import {describe, expect, it} from 'vitest';
import {
    buttonsFor,
    isChangedUnderneath,
    nextOffset,
    PAGE_SIZE,
    pageRange,
    refusalCodeOf,
    siteKeyFromRoute,
    stateOfStored,
    visibleFileNames
} from './quarantine.utils';

describe('buttonsFor', () => {
    it('gives a button to each allowed operation alone, in the fixed order, Reject last', () => {
        expect(buttonsFor(['REJECT', 'RELEASE', 'RETRY'])).toEqual(['RETRY', 'RELEASE', 'REJECT']);
        expect(buttonsFor(['RESUME', 'RETRY', 'REJECT'])).toEqual(['RETRY', 'RESUME', 'REJECT']);
    });

    it('gives no button when the API allows nothing, and none to an operation it does not know', () => {
        expect(buttonsFor([])).toEqual([]);
        expect(buttonsFor(null)).toEqual([]);
        expect(buttonsFor(['DOWNLOAD', 'RELEASE'])).toEqual(['RELEASE']);
    });
});

describe('pageRange and nextOffset', () => {
    it('reads the first, the middle and the last page of 63 submissions', () => {
        expect(pageRange(0, 25, 63)).toEqual({from: 1, to: 25, total: 63, hasPrevious: false, hasNext: true});
        expect(pageRange(25, 25, 63)).toEqual({from: 26, to: 50, total: 63, hasPrevious: true, hasNext: true});
        expect(pageRange(50, 13, 63)).toEqual({from: 51, to: 63, total: 63, hasPrevious: true, hasNext: false});
    });

    it('reads an empty list as 0 to 0, with no next page', () => {
        expect(pageRange(0, 0, 0)).toEqual({from: 0, to: 0, total: 0, hasPrevious: false, hasNext: false});
    });

    it('pages by 25 and stays inside the list', () => {
        expect(PAGE_SIZE).toBe(25);
        expect(nextOffset(0, 'next', 63)).toBe(25);
        expect(nextOffset(50, 'next', 63)).toBe(50);
        expect(nextOffset(25, 'previous', 63)).toBe(0);
        expect(nextOffset(0, 'previous', 63)).toBe(0);
    });
});

describe('refusalCodeOf', () => {
    const refused = (code: unknown) => ({graphQLErrors: [{message: 'any text', extensions: {code}}]});

    it('reads the code of the extensions, never the message', () => {
        expect(refusalCodeOf(refused('CONFLICT'))).toBe('CONFLICT');
        expect(refusalCodeOf(refused('NOT_FOUND'))).toBe('NOT_FOUND');
        expect(refusalCodeOf(refused('FORBIDDEN'))).toBe('FORBIDDEN');
        expect(refusalCodeOf({graphQLErrors: [{message: 'The operation release is not allowed in state completed'}]}))
            .toBeUndefined();
    });

    it('reads a conflict or a submission gone as changed underneath, and a failure with no code as not', () => {
        expect(isChangedUnderneath(refused('CONFLICT'))).toBe(true);
        expect(isChangedUnderneath(refused('NOT_FOUND'))).toBe(true);
        expect(isChangedUnderneath(refused('FORBIDDEN'))).toBe(false);
        expect(isChangedUnderneath(new Error('Network error'))).toBe(false);
        expect(isChangedUnderneath(undefined)).toBe(false);
    });
});

describe('siteKeyFromRoute', () => {
    it('reads the site of a jContent app route, under a context path too', () => {
        expect(siteKeyFromRoute('/jahia/jcontent/digitall/en/apps/formidableQuarantine')).toBe('digitall');
        expect(siteKeyFromRoute('/dx/jahia/jcontent/digitall/en/apps/formidableQuarantine', '/dx')).toBe('digitall');
        expect(siteKeyFromRoute('/jahia/administration')).toBeUndefined();
    });
});

describe('the small readers', () => {
    it('maps the stored names of the audit to the states of the API', () => {
        expect(stateOfStored('needsReview')).toBe('NEEDS_REVIEW');
        expect(stateOfStored('unknown')).toBeUndefined();
    });

    it('shows two file names and counts the others', () => {
        expect(visibleFileNames(['a.pdf', 'b.png', 'c.txt', 'd.doc'])).toEqual({names: ['a.pdf', 'b.png'], more: 2});
        expect(visibleFileNames([])).toEqual({names: [], more: 0});
    });
});
