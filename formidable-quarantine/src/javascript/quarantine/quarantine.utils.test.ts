import {describe, expect, it} from 'vitest';
import {
    buttonsFor,
    formatSize,
    isChangedUnderneath,
    offsetInsideList,
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

describe('pageRange and offsetInsideList', () => {
    it('reads the first, the middle and the last page of 63 submissions', () => {
        expect(pageRange(0, 25, 63)).toEqual({from: 1, to: 25, total: 63});
        expect(pageRange(25, 25, 63)).toEqual({from: 26, to: 50, total: 63});
        expect(pageRange(50, 13, 63)).toEqual({from: 51, to: 63, total: 63});
    });

    it('reads an empty list as 0 to 0', () => {
        expect(pageRange(0, 0, 0)).toEqual({from: 0, to: 0, total: 0});
    });

    it('brings an empty page past the end back to the start of the last page', () => {
        expect(PAGE_SIZE).toBe(25);
        // the last row of page 2 left a filtered list of 25: page 1 holds them
        expect(offsetInsideList(25, 0, 25)).toBe(0);
        expect(offsetInsideList(50, 0, 26)).toBe(25);
        expect(offsetInsideList(75, 0, 63)).toBe(50);
    });

    it('leaves a page with rows, an empty list and the first page as they are', () => {
        expect(offsetInsideList(25, 3, 28)).toBeNull();
        expect(offsetInsideList(25, 0, 0)).toBeNull();
        expect(offsetInsideList(0, 0, 0)).toBeNull();
    });
});

describe('formatSize', () => {
    it('writes the size with the unit and the number of the language', () => {
        expect(formatSize(500, 'en')).toBe('500 byte');
        expect(formatSize(2048, 'en')).toBe('2 kB');
        expect(formatSize(1.5 * 1024 * 1024, 'en')).toBe('1.5 MB');
        // French writes a narrow no-break space before the unit
        expect(formatSize(1.5 * 1024 * 1024, 'fr')).toBe('1,5 Mo');
    });

    it('writes nothing for a size the API does not know', () => {
        expect(formatSize(null, 'en')).toBe('');
        expect(formatSize(-1, 'en')).toBe('');
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
