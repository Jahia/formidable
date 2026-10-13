import {formatFileSize} from '../FormResults.utils';
import {sanitizeFilenamePart} from '../export/export.utils';
import {formTitle, groupedNotes, type ImportReport, type ImportReportForm, type ReportLine} from './import.utils';
import {noteText, profileMappings} from './notes';

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

/** The fields mapped to the visitor profile, as a table, and what follows the prefill when every field shares it. */
function profileSection(lines: ReportLine[], t: Translate): string[] {
    const {mappings, commonAfter} = profileMappings(lines);
    if (mappings.length === 0) {
        return [];
    }
    const header = [t('formResults.import.columns.form'), t('formResults.import.markdown.property'), t('formResults.import.markdown.prefilled')];
    const rows = mappings.map(mapping => {
        const after = mapping.prefilled && mapping.after && commonAfter === null ? t(`formResults.import.after.${mapping.after}`) : '';
        const prefilled = mapping.prefilled ? `✓ ${after}`.trim() : '';
        return `| ${escape(mapping.field)} | *${escape(mapping.property)}* | ${escape(prefilled)} |`;
    });
    const afterLine = commonAfter ? ['', t('formResults.import.profile.after', {after: t(`formResults.import.after.${commonAfter}`)})] : [];
    return ['', `### ${t('formResults.import.profile.title')}`, ...afterLine, '', `| ${header.join(' | ')} |`, '|---|---|---|', ...rows];
}

/** The section of one form: what receives its results, its figures, what to redo by hand, its profile mappings, the rest. */
function formSection(form: ImportReport['forms'][number], report: ImportReport, context: MarkdownContext): string[] {
    const {t, language} = context;
    const notes = context.notesApply(form) ? groupedNotes(form, language) : {action: [], info: []};
    const {others} = profileMappings(notes.info);
    const toDo = notes.action.length > 0 ? ['', `### ${t('formResults.import.notes.action')}`, '', ...notes.action.map(line => lineOf(line, t))] : [];
    const carried = others.length > 0 ? ['', `### ${t('formResults.import.notes.info')}`, '', ...others.map(line => lineOf(line, t))] : [];
    return [
        `## ${escape(formTitle(form, language))}`,
        '',
        `- ${t('formResults.import.columns.result')}: ${escape(context.outcomeOf(form))}`,
        ...figuresOf(form, report.dryRun, t),
        ...toDo,
        ...profileSection(notes.info, t),
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
