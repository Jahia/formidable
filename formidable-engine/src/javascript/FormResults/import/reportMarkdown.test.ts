import {describe, expect, it} from 'vitest';
import {type ImportReport, type ImportReportForm} from './import.utils';
import {reportFileName, reportMarkdown} from './reportMarkdown';

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

describe('reportMarkdown', () => {
    const contact = form('contact', {
        outcome: 'created',
        notes: [{code: 'form.passwordDropped', level: 'action', params: {field: 'password_0_1'}}],
        fields: [
            {name: 'first-name', type: 'fmdb:inputText', titles: {en: 'First name'}, notes: [{code: 'field.profileMapped', level: 'info', params: {property: 'firstName'}}]},
            {name: 'enquiry', type: 'fmdb:textarea', titles: {en: 'Enquiry'}, notes: [{code: 'field.prefillUnknownProperty', level: 'action', params: {property: 'sfdc__lead'}}]}
        ],
        values: {converted: 9, dropped: 2, notConverted: 0},
        files: {count: 1, bytes: 578, missing: 1}
    });
    const news = form('news', {notes: [{code: 'form.redirect', level: 'action', params: {target: '/home'}}]});
    const report = {dryRun: false, importedFormsFolder: null, forms: [contact, news], totals: {forms: 2, submissionsFound: 6, submissionsToImport: 0, submissionsImported: 6}, nothingToImport: false} as ImportReport;

    const markdown = reportMarkdown(report, {
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
    });

    it('lists what to redo by hand, the field it concerns first, then what was carried over', () => {
        const toDo = markdown.indexOf('### formResults.import.notes.action');
        const profile = markdown.indexOf('### formResults.import.profile.title');
        expect(toDo).toBeGreaterThan(0);
        expect(profile).toBeGreaterThan(toDo);
        expect(markdown).toContain('- formResults.import.note.form.passwordDropped(field=password\\_0\\_1)');
        expect(markdown).toContain('- **Enquiry**: formResults.import.note.field.prefillUnknownProperty(property=sfdc\\_\\_lead)');
        // a profile mapping goes to a table of its own, out of the carried-over list
        expect(markdown).toContain('### formResults.import.profile.title');
        expect(markdown).toContain('| First name | *firstName* |  |');
        expect(markdown).not.toContain('field.profileMapped');
    });

    it('leaves out the notes of a form that is not created', () => {
        expect(markdown).toContain('## NEWS');
        expect(markdown).not.toContain('form.redirect');
    });
});

describe('reportFileName', () => {
    it('names the file after the site and the minute, in UTC', () => {
        expect(reportFileName('acme', new Date('2026-10-13T14:51:30Z'))).toEqual('forms-import-acme-2026-10-13-1451.md');
    });
});
