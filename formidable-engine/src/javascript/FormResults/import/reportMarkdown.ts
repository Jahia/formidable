import {formatFileSize} from '../FormResults.utils';
import {sanitizeFilenamePart} from '../export/export.utils';
import {formTitle, groupedNotes, type ImportReport, type ImportReportForm, type ReportLine} from './import.utils';
import {noteText, type ProfileMapping, profileMappings} from './notes';

type Translate = (key: string, options?: Record<string, unknown>) => string;

interface MarkdownContext {
    siteKey: string;
    fileName: string;
    language: string;
    /** What each row of the report says receives the results, in words, as the dialog shows it. */
    outcomeOf: (form: ImportReportForm) => string;
    /** Whether the notes of a form apply: they only matter when its form is created. */
    notesApply: (form: ImportReportForm) => boolean;
    date: Date;
    t: Translate;
}

const MARKDOWN_SYNTAX = /[\\`*_[\]#|<>]/g;

/** The text with the characters Markdown reads as syntax escaped. */
const escape = (text: string): string => text.replaceAll(MARKDOWN_SYNTAX, String.raw`\$&`);

const lineOf = (line: ReportLine, t: Translate): string => {
    const fields = line.fields.length > 0 ? `**${escape(line.fields.join(', '))}**: ` : '';
    return `- ${fields}${escape(noteText(line.note, t))}`;
};

/** The figures of a form, one item each: its submissions, its fields, its files, the values that did not come along. */
function figuresOf(form: ImportReportForm, dryRun: boolean, t: Translate): string[] {
    const submissions = [t('formResults.import.report.submissions', {count: form.submissions.found})];
    if (!dryRun) {
        submissions.push(t('formResults.import.report.imported', {count: form.submissions.imported}));
    }
    if (form.submissions.alreadyImported > 0) {
        submissions.push(t('formResults.import.report.already', {count: form.submissions.alreadyImported}));
    }
    return [
        submissions.join(', '),
        t('formResults.import.report.fields', {count: form.fields.length}),
        form.files.count > 0 ? t('formResults.import.report.files', {count: form.files.count, size: formatFileSize(form.files.bytes)}) : '',
        form.files.missing > 0 ? t('formResults.import.report.filesMissing', {count: form.files.missing}) : '',
        form.values.dropped > 0 || form.values.notConverted > 0 ?
            t('formResults.import.report.values', {dropped: form.values.dropped, notConverted: form.values.notConverted}) : ''
    ].filter(Boolean).map(figure => `- ${figure}`);
}

/**
 * The fields mapped to the visitor profile, as a table: the property, whether it exists and the mapping is written
 * (✓), or not and why (✗), or could not be checked (⚠), and whether the prefill is on.
 */
function profileSection(profile: ReturnType<typeof profileMappings>, t: Translate): string[] {
    if (profile.mappings.length === 0) {
        return [];
    }
    const header = [t('formResults.import.markdown.field'), t('formResults.import.markdown.property'), t('formResults.import.markdown.status'),
        t('formResults.import.markdown.prefilled')];
    const statusOf = (mapping: ProfileMapping): string => {
        if (mapping.status === 'failed') {
            const why = t(`formResults.import.profile.failed.${mapping.reason}`);
            return `✗ ${why}`;
        }
        return mapping.status === 'unchecked' ? `⚠ ${t('formResults.import.profile.unchecked')}` : '✓';
    };
    const rows = profile.mappings.map(mapping => {
        const prefill = mapping.status !== 'failed' && mapping.prefilled ? '✓' : '';
        return `| ${escape(mapping.field)} | *${escape(mapping.property)}* | ${escape(statusOf(mapping))} | ${prefill} |`;
    });
    return ['', `### ${t('formResults.import.profile.title')}`, '', t('formResults.import.profile.hint'), '', `| ${header.join(' | ')} |`, '|---|---|---|---|', ...rows];
}

/** The section of one form: what receives its results, its figures, what to redo by hand, its profile mappings, the rest. */
function formSection(form: ImportReport['forms'][number], report: ImportReport, context: MarkdownContext): string[] {
    const {t, language} = context;
    const notes = context.notesApply(form) ? groupedNotes(form, language) : {action: [], info: []};
    const profile = profileMappings(notes.info, notes.action);
    const toReview = profile.toReview.length > 0 ?
        ['', `### ${t('formResults.import.notes.action')}`, '', ...profile.toReview.map(line => lineOf(line, t))] : [];
    const carried = profile.others.length > 0 ? ['', `### ${t('formResults.import.notes.info')}`, '', ...profile.others.map(line => lineOf(line, t))] : [];
    return [
        `## ${escape(formTitle(form, language))}`,
        '',
        `- ${t('formResults.import.columns.result')}: ${escape(context.outcomeOf(form))}`,
        ...figuresOf(form, report.dryRun, t),
        ...toReview,
        ...profileSection(profile, t),
        ...carried,
        ''
    ];
}

/**
 * The report of a dry run or of an import as a Markdown file (docs/architecture/forms-import.md, "Running it"): one
 * section per form with what receives its results, its figures, what to redo or check by hand, its profile
 * mappings and the rest of what was carried over, in the language of the dialog. Built in the browser from the
 * report the dialog shows: nothing is stored.
 */
export function reportMarkdown(report: ImportReport, context: MarkdownContext): string {
    const {t} = context;
    const date = context.date.toISOString().slice(0, 16).replace('T', ' ');
    return [
        `# ${t('formResults.import.markdown.title')}`,
        '',
        `- ${t('formResults.import.markdown.site')}: ${escape(context.siteKey)}`,
        `- ${t('formResults.import.markdown.file')}: ${escape(context.fileName)}`,
        `- ${t('formResults.import.markdown.date')}: ${date} UTC`,
        `- ${t(report.dryRun ? 'formResults.import.markdown.dryRun' : 'formResults.import.markdown.imported')}`,
        '',
        ...report.forms.flatMap(form => formSection(form, report, context))
    ].join('\n');
}

/** forms-import-<site>-<yyyy-MM-dd-HHmm>.md, in UTC. */
export function reportFileName(siteKey: string, date: Date): string {
    const [day, time] = date.toISOString().slice(0, 16).split('T');
    return `forms-import-${sanitizeFilenamePart(siteKey)}-${day}-${time.replace(':', '')}.md`;
}
