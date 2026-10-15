import {describe, expect, it} from 'vitest';
import {type ImportReport, type ImportReportForm} from './import.utils';
import {importLog, importLogFileName} from './importLog';

// a translation that shows the key and the values it was given, enough to check what reaches the sentence
const t = (key: string, options?: Record<string, unknown>): string => {
    const values = Object.entries(options ?? {}).filter(([name]) => name !== 'defaultValue').map(([name, value]) => `${name}=${String(value)}`);
    return values.length > 0 ? `${key}(${values.join(',')})` : key;
};

const form = (sourceName: string, overrides: Partial<ImportReportForm> = {}): ImportReportForm => ({
    sourceName,
    titles: {en: sourceName.toUpperCase()},
    targetName: sourceName,
    targetPath: null,
    outcome: 'resultsOnly',
    fields: [],
    notes: [],
    submissions: {found: 3, imported: 3, alreadyImported: 0, toImport: 3},
    values: {converted: 9, dropped: 0, notConverted: 0},
    files: {count: 0, bytes: 0, missing: 0},
    ...overrides
});

describe('importLog', () => {
    const contact = form('contact', {
        outcome: 'created',
        notes: [{code: 'form.passwordDropped', level: 'action', params: {field: 'password_0_1'}}],
        fields: [
            {name: 'first-name', type: 'fmdb:inputText', titles: {en: 'First name'}, notes: [{code: 'field.profileMapped', level: 'info', params: {property: 'firstName'}}]},
            {name: 'enquiry', type: 'fmdb:textarea', titles: {en: 'Enquiry'}, notes: [{code: 'field.prefillUnknownProperty', level: 'action', params: {property: 'sfdc__lead'}}]}
        ],
        values: {converted: 9, dropped: 2, notConverted: 0},
        files: {count: 1, bytes: 578, missing: 1},
        submissions: {found: 3, imported: 2, alreadyImported: 0, failed: 1, toImport: 3},
        incidents: [
            // read out of date order: the log lists them by date
            {code: 'submission.passwordDropped', level: 'action', params: {field: 'Password'}, submission: 's2', created: '2024-06-02T11:30:00Z'},
            {code: 'submission.passwordDropped', level: 'action', params: {field: 'Password'}, submission: 's1', created: '2024-06-01T10:00:00Z'},
            {code: 'submission.writeFailed', level: 'action', params: {reason: 'disk full'}, submission: 's3', created: null}
        ],
        incidentsOmitted: 4
    });
    const skipped = form('skipped', {outcome: 'skipped'});
    const news = form('news', {notes: [{code: 'form.redirect', level: 'action', params: {target: '/home'}}]});
    const report = {dryRun: false, importedFormsFolder: null, forms: [contact, news, skipped], totals: {forms: 3, submissionsFound: 9, submissionsToImport: 0, submissionsImported: 5, submissionsFailed: 1},
        nothingToImport: false, stopped: null,
        incidents: [{code: 'submission.noForm', level: 'action', params: {form: 'old-survey'}, submission: 's9', created: '2024-01-01T00:00:00Z'}]} as ImportReport;

    const markdown = importLog(report, {
        siteKey: 'acme',
        fileName: 'formFactory.zip',
        language: 'en',
        date: new Date('2026-10-13T14:51:00Z'),
        t,
        outcomeOf: f => `outcome of ${f.sourceName}`,
        notesApply: f => f.outcome === 'created'
    });

    it('gives a section per form with what received its results and its figures, losses included', () => {
        expect(markdown).toContain('# formResults.import.markdown.title');
        expect(markdown).toContain('- formResults.import.markdown.site: acme');
        expect(markdown).toContain('2026-10-13 14:51 UTC');
        expect(markdown).toContain('## CONTACT');
        expect(markdown).toContain('- formResults.import.columns.result: outcome of contact');
        expect(markdown).toContain('formResults.import.report.filesMissing(count=1)');
        expect(markdown).toContain('formResults.import.report.values(dropped=2,notConverted=0)');
        expect(markdown).toContain('- formResults.import.markdown.done');
        expect(markdown).toContain('formResults.import.markdown.totals(imported=5,failed=1)');
        expect(markdown).toContain('- formResults.import.report.failed(count=1)');
        // a form left out of the import has nothing to log
        expect(markdown).not.toContain('## SKIPPED');
    });

    it('gives what the submissions lost once per sentence, with the submissions it concerns under it', () => {
        const section = markdown.indexOf('### formResults.import.markdown.incidents');
        expect(section).toBeGreaterThan(markdown.indexOf('## CONTACT'));
        expect(markdown).toContain('- formResults.import.note.submission.passwordDropped(field=Password) (2)');
        expect(markdown).toContain('  - 2024-06-01 10:00 UTC · formResults.import.markdown.submission(id=s1)');
        expect(markdown).toContain('  - 2024-06-02 11:30 UTC · formResults.import.markdown.submission(id=s2)');
        expect(markdown.indexOf('(id=s1)')).toBeLessThan(markdown.indexOf('(id=s2)'));
        expect(markdown).toContain('- formResults.import.note.submission.writeFailed(reason=disk full) (1)');
        expect(markdown).toContain('  - formResults.import.markdown.submission(id=s3)');
        expect(markdown).toContain('- formResults.import.markdown.incidentsOmitted(count=4)');
        // the submissions of no form come last, in a section of their own
        expect(markdown.indexOf('## formResults.import.markdown.orphans')).toBeGreaterThan(markdown.indexOf('## NEWS'));
        expect(markdown).toContain(String.raw`- formResults.import.note.submission.noForm(form=old-survey) (1)`);
    });

    it('says why an import stopped before its end', () => {
        const stopped = importLog({...report, stopped: '100 submissions could not be written'}, {
            siteKey: 'acme', fileName: 'f.zip', language: 'en', date: new Date(), t, outcomeOf: () => '', notesApply: () => false
        });
        expect(stopped).toContain('- formResults.import.markdown.stopped(reason=100 submissions could not be written)');
        expect(stopped).not.toContain('formResults.import.markdown.done');
    });

    it('lists what to redo by hand, the field it concerns first, then what was carried over', () => {
        const toDo = markdown.indexOf('### formResults.import.notes.action');
        const profile = markdown.indexOf('### formResults.import.profile.title');
        expect(toDo).toBeGreaterThan(0);
        expect(profile).toBeGreaterThan(toDo);
        expect(markdown).toContain(String.raw`- formResults.import.note.form.passwordDropped(field=password\_0\_1)`);
        // a profile mapping goes to a table of its own, out of the carried-over list
        expect(markdown).toContain('### formResults.import.profile.title');
        expect(markdown).toContain('| First name | *firstName* |  | ✓ |');
        // a prefill that did not become a mapping is a failed row of the table, with why, not a line to review
        expect(markdown).toContain(String.raw`| Enquiry | *sfdc\_\_lead* |  | ✗ formResults.import.profile.failed.field.prefillUnknownProperty |`);
        expect(markdown).not.toContain('field.profileMapped');
    });

    it('leaves out the notes of a form that is not created', () => {
        expect(markdown).toContain('## NEWS');
        expect(markdown).not.toContain('form.redirect');
    });
});

describe('importLogFileName', () => {
    it('names the file after the site and the minute, in UTC', () => {
        expect(importLogFileName('acme', new Date('2026-10-13T14:51:30Z'))).toEqual('forms-import-log-acme-2026-10-13-1451.md');
    });
});
