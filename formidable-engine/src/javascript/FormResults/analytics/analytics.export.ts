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
    return `"${neutralised.replace(/"/g, '""')}"`;
};

/** The byte order mark that makes a spreadsheet read the file as UTF-8. */
const BOM = String.fromCharCode(0xFEFF);

const percent = (share: number): string => (share * 100).toFixed(1);

/**
 * The report as one row per figure: section, field, item, count, share. UTF-8 with a BOM and CRLF line ends,
 * every cell quoted, so that a spreadsheet opens it as is.
 */
export function buildAnalyticsCsv(analytics: FormAnalytics, meta: AnalyticsExportMeta, t: Translate): string {
    const unknown = t('formResults.analytics.unknown');
    const rows: Array<Array<string | number>> = [[
        t('formResults.analytics.export.section'),
        t('formResults.analytics.export.field'),
        t('formResults.analytics.export.item'),
        t('formResults.analytics.export.count'),
        t('formResults.analytics.export.share')
    ]];

    rows.push([t('formResults.analytics.kpi.submissions'), '', meta.period, analytics.total, '']);
    for (const bucket of analytics.timeline.buckets) {
        const date = bucket.start;
        const day = `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}${analytics.timeline.unit === 'day' ? `-${String(date.getDate()).padStart(2, '0')}` : ''}`;
        rows.push([t('formResults.analytics.timeline.title'), '', day, bucket.count, '']);
    }

    for (const page of analytics.pages) {
        rows.push([t('formResults.analytics.pages.title'), '', page.key ?? unknown, page.count, percent(page.share)]);
    }

    for (const language of analytics.languages) {
        rows.push([t('formResults.analytics.languages.title'), '', language.key ?? unknown, language.count, percent(language.share)]);
    }

    for (const field of analytics.completion) {
        rows.push([t('formResults.analytics.completion.title'), field.name, t('formResults.analytics.completion.shown'), field.shown, analytics.total === 0 ? '' : percent(field.shown / analytics.total)]);
        rows.push([t('formResults.analytics.completion.title'), field.name, t('formResults.analytics.completion.empty'), field.empty, percent(field.emptyShare)]);
    }

    for (const distribution of analytics.distributions) {
        for (const entry of distribution.entries) {
            const item = distribution.kind === 'boolean' ? t(`formResults.analytics.answers.${entry.value === 'true' ? 'yes' : 'no'}`) : entry.label;
            rows.push([t('formResults.analytics.answers.title'), distribution.name, item, entry.count, percent(entry.share)]);
        }
    }

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
