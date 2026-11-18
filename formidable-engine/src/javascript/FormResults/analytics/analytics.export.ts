import type {FormAnalytics} from './analytics.utils';

type Translate = (key: string) => string;

export interface AnalyticsExportMeta {
    form: string;
    site: string;
    /** The period as the page names it, and its bounds as local days; `from` is null for all the submissions. */
    period: string;
    from: string | null;
    to: string;
    generatedAt: string;
}

// A leading =, +, -, @, tab or CR makes a spreadsheet evaluate the cell as a formula, and these cells carry
// answers typed by anonymous visitors: such a cell is neutralised with a leading quote.
const FORMULA_TRIGGERS = new Set(['=', '+', '-', '@', '\t', '\r']);

const csvCell = (value: string | number): string => {
    const text = String(value);
    const neutralised = FORMULA_TRIGGERS.has(text.charAt(0)) && typeof value === 'string' ? `'${text}` : text;
    return `"${neutralised.replaceAll('"', '""')}"`;
};

/** The byte order mark that makes a spreadsheet read the file as UTF-8. */
const BOM = String.fromCodePoint(0xFEFF);

const percent = (share: number): string => (share * 100).toFixed(1);

type Row = Array<string | number>;

const pad = (value: number): string => String(value).padStart(2, '0');

/** A bucket of the timeline as `yyyy-MM-dd`, or `yyyy-MM` when the timeline counts per month. */
function bucketDate(start: Date, unit: FormAnalytics['timeline']['unit']): string {
    const month = `${start.getFullYear()}-${pad(start.getMonth() + 1)}`;
    return unit === 'day' ? `${month}-${pad(start.getDate())}` : month;
}

function countRows(section: string, entries: FormAnalytics['pages'], unknown: string): Row[] {
    return entries.map(entry => [section, '', entry.key ?? unknown, entry.count, percent(entry.share)]);
}

function completionRows(analytics: FormAnalytics, t: Translate): Row[] {
    const section = t('formResults.analytics.completion.title');
    return analytics.completion.flatMap(field => {
        const shownShare = analytics.total === 0 ? '' : percent(field.shown / analytics.total);
        return [
            [section, field.name, t('formResults.analytics.completion.shown'), field.shown, shownShare],
            [section, field.name, t('formResults.analytics.completion.empty'), field.empty, percent(field.emptyShare)]
        ];
    });
}

function answerRows(analytics: FormAnalytics, t: Translate): Row[] {
    const section = t('formResults.analytics.answers.title');
    const yesNo = (value: string): string => t(value === 'true' ? 'formResults.analytics.answers.yes' : 'formResults.analytics.answers.no');
    return analytics.distributions.flatMap(distribution => distribution.entries.map(entry => {
        const item = distribution.kind === 'boolean' ? yesNo(entry.value) : entry.label;
        return [section, distribution.name, item, entry.count, percent(entry.share)];
    }));
}

/**
 * The report as one row per figure: section, field, item, count, share. UTF-8 with a BOM and CRLF line ends,
 * every cell quoted, so that a spreadsheet opens it as is.
 */
export function buildAnalyticsCsv(analytics: FormAnalytics, meta: AnalyticsExportMeta, t: Translate): string {
    const unknown = t('formResults.analytics.unknown');
    const timeline = t('formResults.analytics.timeline.title');
    const rows: Row[] = [
        [
            t('formResults.analytics.export.section'),
            t('formResults.analytics.export.field'),
            t('formResults.analytics.export.item'),
            t('formResults.analytics.export.count'),
            t('formResults.analytics.export.share')
        ],
        [t('formResults.analytics.kpi.submissions'), '', meta.period, analytics.total, ''],
        ...analytics.timeline.buckets.map(bucket => [timeline, '', bucketDate(bucket.start, analytics.timeline.unit), bucket.count, '']),
        ...countRows(t('formResults.analytics.pages.title'), analytics.pages, unknown),
        ...countRows(t('formResults.analytics.languages.title'), analytics.languages, unknown),
        ...completionRows(analytics, t),
        ...answerRows(analytics, t)
    ];

    return `${BOM}${rows.map(row => row.map(csvCell).join(',')).join('\r\n')}\r\n`;
}

/** The report as computed, with what it was computed on and when. */
export function buildAnalyticsJson(analytics: FormAnalytics, meta: AnalyticsExportMeta): string {
    return JSON.stringify({
        ...meta,
        ...analytics,
        timeline: {
            unit: analytics.timeline.unit,
            buckets: analytics.timeline.buckets.map(bucket => ({start: bucket.start.toISOString(), count: bucket.count}))
        }
    }, null, 2);
}
