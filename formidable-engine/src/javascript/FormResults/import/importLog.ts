import {formatFileSize} from '../FormResults.utils';
import {sanitizeFilenamePart} from '../export/export.utils';
import {formTitle, groupedNotes, type ImportIncident, type ImportReport, type ImportReportForm, type ReportLine} from './import.utils';
import {noteText, type ProfileMapping, profileMappings} from './notes';

type Translate = (key: string, options?: Record<string, unknown>) => string;

interface LogContext {
    siteKey: string;
    fileName: string;
    language: string;
    /** What each row of the report says received the results, in words, as the dialog shows it. */
    outcomeOf: (form: ImportReportForm) => string;
    /** Whether the notes of a form apply: they only matter when its form was created. */
    notesApply: (form: ImportReportForm) => boolean;
    date: Date;
    t: Translate;
}

const MARKDOWN_SYNTAX = /[\\`*_[\]#|<>]/g;

/** The text with the characters Markdown reads as syntax escaped. */
const escape = (text: string): string => text.replaceAll(MARKDOWN_SYNTAX, String.raw`\$&`);

/** A moment as the log gives it: yyyy-MM-dd HH:mm, in UTC. */
const utc = (date: Date): string => date.toISOString().slice(0, 16).replace('T', ' ');

const lineOf = (line: ReportLine, t: Translate): string => {
    const fields = line.fields.length > 0 ? `**${escape(line.fields.join(', '))}**: ` : '';
    return `- ${fields}${escape(noteText(line.note, t))}`;
};

/** The figures of a form, one item each: its submissions, its fields, its files, the values that did not come along. */
function figuresOf(form: ImportReportForm, t: Translate): string[] {
    const submissions = [t('formResults.import.report.imported', {count: form.submissions.imported})];
    if (form.submissions.alreadyImported > 0) {
        submissions.push(t('formResults.import.report.already', {count: form.submissions.alreadyImported}));
    }
    return [
        submissions.join(', '),
        (form.submissions.failed ?? 0) > 0 ? t('formResults.import.report.failed', {count: form.submissions.failed}) : '',
        t('formResults.import.report.fields', {count: form.fields.length}),
        form.files.count > 0 ? t('formResults.import.report.files', {count: form.files.count, size: formatFileSize(form.files.bytes)}) : '',
        form.files.missing > 0 ? t('formResults.import.report.filesMissing', {count: form.files.missing}) : '',
        form.values.dropped > 0 || form.values.notConverted > 0 ?
            t('formResults.import.report.values', {dropped: form.values.dropped, notConverted: form.values.notConverted}) : ''
    ].filter(Boolean).map(figure => `- ${figure}`);
}

/**
 * The incidents of a form or of the run, one item per sentence with the submissions it concerns under it, by
 * date: a field that lost its values in every submission reads once, not once per submission.
 */
function incidentsSection(title: string, incidents: ImportIncident[] = [], omitted = 0, t: Translate): string[] {
    if (incidents.length === 0 && omitted === 0) {
        return [];
    }
    const bySentence = new Map<string, ImportIncident[]>();
    incidents.forEach(incident => {
        const sentence = noteText(incident, t);
        bySentence.set(sentence, [...(bySentence.get(sentence) ?? []), incident]);
    });
    const items = [...bySentence].flatMap(([sentence, concerned]) => [
        `- ${escape(sentence)} (${concerned.length})`,
        ...concerned.map(incident => {
            const submission = t('formResults.import.markdown.submission', {id: incident.submission});
            const date = incident.created ? utc(new Date(incident.created)) + ' UTC · ' : '';
            return `  - ${date}${escape(submission)}`;
        })
    ]);
    const more = omitted > 0 ? [`- ${t('formResults.import.markdown.incidentsOmitted', {count: omitted})}`] : [];
    return ['', title, '', ...items, ...more];
}

/**
 * The fields mapped to the visitor profile, as the dialog shows them: the property, whether the prefill is on, and
 * whether the property exists and the mapping is written (✓), or not and why (✗), or could not be checked (⚠).
 */
function profileSection(profile: ReturnType<typeof profileMappings>, t: Translate): string[] {
    if (profile.mappings.length === 0) {
        return [];
    }
    const header = [t('formResults.import.profile.columns.field'), t('formResults.import.profile.columns.property'),
        t('formResults.import.profile.columns.prefilled'), t('formResults.import.profile.columns.status')];
    const statusOf = (mapping: ProfileMapping): string => {
        if (mapping.status === 'failed') {
            const why = t(`formResults.import.profile.failed.${mapping.reason}`);
            return `✗ ${why}`;
        }
        return mapping.status === 'unchecked' ? `⚠ ${t('formResults.import.profile.unchecked')}` : '✓';
    };
    const rows = profile.mappings.map(mapping => {
        const prefill = mapping.status !== 'failed' && mapping.prefilled ? '✓' : '';
        return `| ${escape(mapping.field)} | *${escape(mapping.property)}* | ${prefill} | ${escape(statusOf(mapping))} |`;
    });
    return ['', `### ${t('formResults.import.profile.title')}`, '', t('formResults.import.profile.hint'), '', `| ${header.join(' | ')} |`, '|---|---|---|---|', ...rows];
}

/**
 * The section of one form: what received its results, its figures, what its submissions lost, what to redo by
 * hand, its profile mappings, the rest.
 */
function formSection(form: ImportReportForm, context: LogContext): string[] {
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
        ...figuresOf(form, t),
        ...incidentsSection(`### ${t('formResults.import.markdown.incidents')}`, form.incidents, form.incidentsOmitted, t),
        ...toReview,
        ...profileSection(profile, t),
        ...carried,
        ''
    ];
}

/**
 * The log of an import as a Markdown file (docs/architecture/forms-import.md, "Running it"): how it ended, then
 * one section per form with what received its results, its figures, what its submissions lost or why they were
 * not written, what to redo or check by hand, its profile mappings and the rest of what was carried over; last,
 * the submissions of no form. In the language of the dialog, built in the browser from the report of the job.
 */
export function importLog(report: ImportReport, context: LogContext): string {
    const {t} = context;
    const forms = report.forms.filter(form => form.outcome !== 'skipped');
    return [
        `# ${t('formResults.import.markdown.title')}`,
        '',
        `- ${t('formResults.import.markdown.site')}: ${escape(context.siteKey)}`,
        `- ${t('formResults.import.markdown.file')}: ${escape(context.fileName)}`,
        `- ${t('formResults.import.markdown.date')}: ${utc(context.date)} UTC`,
        `- ${report.stopped ? escape(t('formResults.import.markdown.stopped', {reason: report.stopped})) : t('formResults.import.markdown.done')}`,
        `- ${t('formResults.import.markdown.totals', {imported: report.totals.submissionsImported, failed: report.totals.submissionsFailed ?? 0})}`,
        '',
        ...forms.flatMap(form => formSection(form, context)),
        ...incidentsSection(`## ${t('formResults.import.markdown.orphans')}`, report.incidents, report.incidentsOmitted, t)
    ].join('\n');
}

/** forms-import-log-<site>-<yyyy-MM-dd-HHmm>.md, in UTC. */
export function importLogFileName(siteKey: string, date: Date): string {
    const [day, time] = date.toISOString().slice(0, 16).split('T');
    return `forms-import-log-${sanitizeFilenamePart(siteKey)}-${day}-${time.replace(':', '')}.md`;
}
