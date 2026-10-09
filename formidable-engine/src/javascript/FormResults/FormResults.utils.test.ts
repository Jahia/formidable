import {afterEach, describe, expect, it, vi} from 'vitest';
import {
    buildFormsInEditQuery,
    formatFieldValue,
    formResultsLabel,
    formStatus,
    parseFormFields,
    siteKeyFromRoute,
    withEditForms,
    type FormResultsNode
} from './FormResults.utils';

/** The value as typed, in the reader's locale: the parts are formatted in UTC so no zone shifts them. */
const utcDate = (year: number, month: number, day: number, hours = 0, minutes = 0): Date => {
    const date = new Date(0);
    date.setUTCFullYear(year, month - 1, day);
    date.setUTCHours(hours, minutes, 0, 0);
    return date;
};
const asTypedDate = (year: number, month: number, day: number): string =>
    utcDate(year, month, day).toLocaleDateString(undefined, {timeZone: 'UTC'});
const asTypedDateTime = (year: number, month: number, day: number, hours: number, minutes: number): string =>
    utcDate(year, month, day, hours, minutes).toLocaleString(undefined, {dateStyle: 'short', timeStyle: 'short', timeZone: 'UTC'});

describe('formatFieldValue', () => {
    afterEach(() => {
        // Node re-reads TZ when the variable is assigned; the zone-specific cases stub it.
        vi.unstubAllEnvs();
    });

    it('shows a date field value as typed, not shifted by the reader\'s zone', () => {
        // "2026-09-09" parsed as a zoned instant would read as the 8th west of Greenwich.
        vi.stubEnv('TZ', 'America/Los_Angeles');
        expect(formatFieldValue('2026-09-09', 'date')).toEqual(asTypedDate(2026, 9, 9));
    });

    it('keeps a year below 100 as typed', () => {
        // Date.UTC() would read "0099" as 1999; the date input accepts any four-digit year.
        expect(formatFieldValue('0099-01-01', 'date')).toEqual(asTypedDate(99, 1, 1));
        expect(formatFieldValue('0099-01-01T08:00', 'datetime')).toEqual(asTypedDateTime(99, 1, 1, 8, 0));
        expect(formatFieldValue('0099-01-01', 'date')).not.toContain('1999');
    });

    it('shows a datetime field value as typed, in the reader\'s locale, without the ISO separator', () => {
        const expected = asTypedDateTime(2026, 9, 5, 12, 53);

        expect(formatFieldValue('2026-09-05T12:53', 'datetime')).toEqual(expected);
        expect(formatFieldValue('2026-09-05T12:53:00', 'datetime')).toEqual(expected);
        // The backend also accepts a fraction of a second.
        expect(formatFieldValue('2026-09-05T12:53:00.123', 'datetime')).toEqual(expected);
        expect(formatFieldValue('2026-09-05T12:53', 'datetime')).not.toContain('T');
    });

    it('keeps a wall-clock time that does not exist in the reader\'s zone (spring-forward gap)', () => {
        // 02:30 on 2026-03-08 is skipped in New York; the submitter typed it where it existed.
        vi.stubEnv('TZ', 'America/New_York');
        expect(formatFieldValue('2026-03-08T02:30', 'datetime')).toEqual(asTypedDateTime(2026, 3, 8, 2, 30));
    });

    it('leaves a value that does not parse as stored', () => {
        expect(formatFieldValue('yesterday', 'date')).toEqual('yesterday');
        expect(formatFieldValue('2026-09-05', 'datetime')).toEqual('2026-09-05');
        expect(formatFieldValue('2026-13-45', 'date')).toEqual('2026-13-45');
        expect(formatFieldValue('2026-09-05T25:61', 'datetime')).toEqual('2026-09-05T25:61');
    });

    it('leaves the value of any other field kind as stored', () => {
        expect(formatFieldValue('2026-09-09', undefined)).toEqual('2026-09-09');
    });

    it('follows a datetime value with the submitter\'s time zone when the submission recorded one', () => {
        const expected = asTypedDateTime(2026, 9, 5, 12, 53);
        expect(formatFieldValue('2026-09-05T12:53', 'datetime', 'Europe/Paris')).toEqual(`${expected} (Europe/Paris)`);
        // A generic zone is still where the submitter was: shown as recorded.
        expect(formatFieldValue('2026-09-05T12:53', 'datetime', 'UTC')).toEqual(`${expected} (UTC)`);
    });

    it('shows a datetime value alone when the submission recorded no zone', () => {
        const expected = asTypedDateTime(2026, 9, 5, 12, 53);
        expect(formatFieldValue('2026-09-05T12:53', 'datetime', null)).toEqual(expected);
        expect(formatFieldValue('2026-09-05T12:53', 'datetime', '')).toEqual(expected);
    });

    it('never adds a zone to a date value or to a value that does not parse', () => {
        expect(formatFieldValue('2026-09-09', 'date', 'Europe/Paris')).toEqual(asTypedDate(2026, 9, 9));
        expect(formatFieldValue('yesterday', 'datetime', 'Europe/Paris')).toEqual('yesterday');
    });
});

describe('parseFormFields', () => {
    it('records which fields carry a date or a datetime', () => {
        const fields = parseFormFields({
            jcr: {nodeById: {fields: {nodes: [{descendants: {nodes: [
                {name: 'birthday', displayName: 'Birthday', isDate: true, isDatetime: false},
                {name: 'appointment', displayName: 'Appointment', isDate: false, isDatetime: true},
                {name: 'comment', displayName: 'Comment', isDate: false, isDatetime: false}
            ]}}]}}}
        });

        expect(fields.order).toEqual(['birthday', 'appointment', 'comment']);
        expect(fields.kinds.get('birthday')).toEqual('date');
        expect(fields.kinds.get('appointment')).toEqual('datetime');
        expect(fields.kinds.has('comment')).toEqual(false);
    });
});

describe('form results entries', () => {
    const liveForm = {uuid: 'f', path: '/sites/s/contents/contact', displayName: 'Contact us'};
    const entry = (parentForm: FormResultsNode['parentForm'], editForm?: FormResultsNode['editForm']): FormResultsNode => ({
        uuid: 'u', path: '/sites/s/formidable-results/contact', name: 'contact', displayName: 'contact', parentForm, editForm
    });

    it('names an entry after its form, from live or from edit, or after itself once the form is gone', () => {
        expect(formResultsLabel(entry({value: 'f', refNode: liveForm}))).toEqual('Contact us');
        expect(formResultsLabel(entry({value: 'f', refNode: null}, {uuid: 'f', displayName: 'Contact us (draft)'}))).toEqual('Contact us (draft)');
        expect(formResultsLabel(entry({value: 'f', refNode: null}, null))).toEqual('contact');
        expect(formResultsLabel(entry(null))).toEqual('contact');
    });

    it('tells a published form from an unpublished and a deleted one, once the edit lookup answered', () => {
        expect(formStatus(entry({value: 'f', refNode: liveForm}))).toEqual('published');
        expect(formStatus(entry({value: 'f', refNode: null}))).toEqual('unknown');
        expect(formStatus(entry({value: 'f', refNode: null}, {uuid: 'f', displayName: 'Contact us'}))).toEqual('unpublished');
        expect(formStatus(entry({value: 'f', refNode: null}, null))).toEqual('deleted');
        // an entry imported without a form has no reference at all: imported, not deleted
        expect(formStatus({...entry(null), imported: true})).toEqual('imported');
        expect(formStatus({...entry(null, null), imported: false})).toEqual('deleted');
    });

    it('attaches the edit lookup to the entries missing in live only', () => {
        const published = entry({value: 'p', refNode: liveForm});
        const unpublished = entry({value: 'f', refNode: null});
        const deleted = entry({value: 'g', refNode: null});
        const [a, b, c] = withEditForms([published, unpublished, deleted], [{uuid: 'f', displayName: 'Contact us'}]);
        expect(a.editForm).toBeUndefined();
        expect(b.editForm).toEqual({uuid: 'f', displayName: 'Contact us'});
        expect(c.editForm).toBeNull();
        // Not answered yet: nothing is judged.
        expect(withEditForms([unpublished], undefined)[0].editForm).toBeUndefined();
    });

    it('looks the missing forms up by well-formed UUIDs only', () => {
        expect(buildFormsInEditQuery(['31eaa06e-4647-4d0b-bfdb-98d59d36e5b9', 'not a uuid', '']))
            .toEqual("SELECT * FROM [fmdbmix:formRoot] AS f WHERE f.[jcr:uuid] = '31eaa06e-4647-4d0b-bfdb-98d59d36e5b9'");
        expect(buildFormsInEditQuery(['x'])).toBeNull();
        expect(buildFormsInEditQuery([])).toBeNull();
    });
});

describe('siteKeyFromRoute', () => {
    it('reads the site of a jContent app route', () => {
        expect(siteKeyFromRoute('/jahia/jcontent/FormidableSite4Tests/en/apps/formidableResults')).toEqual('FormidableSite4Tests');
        expect(siteKeyFromRoute('/jahia/jcontent/my-site/fr/apps/formidableResults/extra')).toEqual('my-site');
    });

    it('reads it behind the servlet context path, and only there', () => {
        expect(siteKeyFromRoute('/dx/jahia/jcontent/my-site/en/apps/formidableResults', '/dx')).toEqual('my-site');
        expect(siteKeyFromRoute('/dx/jahia/jcontent/my-site/en/apps/formidableResults')).toBeUndefined();
        expect(siteKeyFromRoute('/jahia/jcontent/my-site/en/apps/formidableResults', '/dx')).toBeUndefined();
    });

    it('knows nothing outside a jContent app route', () => {
        expect(siteKeyFromRoute('/jahia/jcontent/my-site/en/pages/home')).toBeUndefined();
        expect(siteKeyFromRoute('/jahia/category-manager')).toBeUndefined();
        expect(siteKeyFromRoute('/')).toBeUndefined();
    });
});
