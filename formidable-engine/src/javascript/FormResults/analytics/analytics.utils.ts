import {isUserProperty} from '../FormResults.utils';
import {buildFormLogic, parseLogicRule, type LogicElement, type LogicRule} from './analytics.logic';

/**
 * The analytics of a form's results, computed in the browser from the submissions the reader may already
 * read on the Results page: nothing here asks the server for more than the table does. Everything in this
 * file is a pure function of the submissions and of the form's fields, so that it is tested without a page.
 */

/** How the answers of a field are summarised: a choice and a yes/no get a breakdown, a number its scale. */
export type AnalyticsFieldKind = 'choice' | 'boolean' | 'number' | 'file' | 'other';

export interface AnalyticsOption {
    value: string;
    label: string;
}

export interface AnalyticsField {
    name: string;
    label: string;
    kind: AnalyticsFieldKind;
    /** The options the form declares, in its order; empty for a field whose options come from a source. */
    options: AnalyticsOption[];
}

export interface AnalyticsFields {
    /** The fields that submit a value or a file, in the order the form displays them. */
    fields: AnalyticsField[];
    /** The label of every element of the form, for a stored value whose field type carries no marker. */
    labels: Map<string, string>;
    /** The elements that can carry conditional logic (fields, fieldsets, steps), with their rules. */
    logic: LogicElement[];
}

export const EMPTY_ANALYTICS_FIELDS: AnalyticsFields = {fields: [], labels: new Map(), logic: []};

export interface AnalyticsSubmission {
    created: string;
    locale: string | null;
    referer: string | null;
    values: Map<string, string[]>;
    /** The fields that received at least one file. */
    fileFields: Set<string>;
}

type GqlAnalyticsFieldNode = {
    name?: string;
    displayName?: string;
    nonSubmittable?: boolean;
    isSubmittable?: boolean;
    isSensitive?: boolean;
    isChoice?: boolean;
    isBoolean?: boolean;
    isNumber?: boolean;
    isFile?: boolean;
    options?: {values?: unknown} | null;
};

type GqlLogicElementNode = {
    name?: string;
    path?: string;
    fieldKey?: {value?: string} | null;
    logics?: {values?: unknown} | null;
};

type GqlAnalyticsFieldsResponse = {
    jcr?: {nodeById?: {fields?: {nodes?: Array<{
        descendants?: {nodes?: GqlAnalyticsFieldNode[]};
        logicElements?: {nodes?: GqlLogicElementNode[]};
    }>}}};
};

function fieldKind(node: GqlAnalyticsFieldNode): AnalyticsFieldKind {
    if (node.isFile) {
        return 'file';
    }

    if (node.isChoice) {
        return 'choice';
    }

    if (node.isBoolean) {
        return 'boolean';
    }

    return node.isNumber ? 'number' : 'other';
}

/** A manual option is stored as a JSON string `{"value","label","selected"}`; anything else is skipped. */
function parseOptions(values: unknown): AnalyticsOption[] {
    if (!Array.isArray(values)) {
        return [];
    }

    const options: AnalyticsOption[] = [];
    for (const raw of values) {
        try {
            const option = JSON.parse(String(raw)) as {value?: unknown; label?: unknown};
            if (typeof option.value === 'string' && option.value !== '') {
                options.push({value: option.value, label: typeof option.label === 'string' && option.label ? option.label : option.value});
            }
        } catch {
            // Not an option of the manual format: the stored values still show, under their own name.
        }
    }

    return options;
}

/**
 * Reads GET_FORM_FIELD_LABELS for the analytics: the fields that submit something (a value or a file), with
 * what tells how to summarise them. A password is left out: its value is not a statistic.
 */
export function parseAnalyticsFields(data: GqlAnalyticsFieldsResponse | undefined): AnalyticsFields {
    const nodes = data?.jcr?.nodeById?.fields?.nodes?.[0]?.descendants?.nodes;
    if (!Array.isArray(nodes)) {
        return EMPTY_ANALYTICS_FIELDS;
    }

    const fields: AnalyticsField[] = [];
    const labels = new Map<string, string>();
    for (const node of nodes) {
        if (typeof node.name !== 'string' || !node.name) {
            continue;
        }

        const label = node.displayName || node.name;
        labels.set(node.name, label);
        if (node.nonSubmittable || node.isSensitive || !(node.isSubmittable || node.isFile)) {
            continue;
        }

        fields.push({name: node.name, label, kind: fieldKind(node), options: parseOptions(node.options?.values)});
    }

    const logic: LogicElement[] = [];
    for (const node of data?.jcr?.nodeById?.fields?.nodes?.[0]?.logicElements?.nodes ?? []) {
        if (node.name && node.path) {
            const values = Array.isArray(node.logics?.values) ? node.logics.values : [];
            logic.push({
                name: node.name,
                path: node.path,
                fieldKey: node.fieldKey?.value ?? '',
                rules: values.map(parseLogicRule).filter((rule): rule is LogicRule => rule !== null)
            });
        }
    }

    return {fields, labels, logic};
}

type GqlAnalyticsSubmissionNode = {
    created?: {value?: string} | null;
    locale?: {value?: string} | null;
    referer?: {value?: string} | null;
    data?: {nodes?: Array<{properties?: Array<{name?: string; value?: unknown; values?: unknown}>}>};
    files?: {nodes?: Array<{children?: {nodes?: Array<{name?: string}>}}>};
};

export function parseAnalyticsSubmission(node: GqlAnalyticsSubmissionNode): AnalyticsSubmission {
    const values = new Map<string, string[]>();
    for (const property of node.data?.nodes?.[0]?.properties ?? []) {
        if (typeof property.name !== 'string' || !isUserProperty(property.name)) {
            continue;
        }

        const raw = Array.isArray(property.values) && property.values.length > 0 ? property.values : [property.value];
        const filled = raw.filter(value => value != null && value !== '').map(String);
        if (filled.length > 0) {
            values.set(property.name, filled);
        }
    }

    const fileFields = new Set<string>();
    for (const folder of node.files?.nodes?.[0]?.children?.nodes ?? []) {
        if (folder.name) {
            fileFields.add(folder.name);
        }
    }

    return {
        created: node.created?.value ?? '',
        locale: node.locale?.value ?? null,
        referer: node.referer?.value ?? null,
        values,
        fileFields
    };
}

export interface PageContext {
    siteKey?: string;
    /** The servlet context path, empty at the root. */
    contextPath?: string;
}

const RENDER_PREFIX = /^\/cms\/render\/[^/]+/;

/**
 * The page a submission was posted from, as one key per page: the path of the Referer header without the
 * host, the query, the context path, the render servlet, the language segment, the site's own prefix and
 * the `.html` suffix. The same page reached in two languages, with a tracking parameter or through the
 * site's domain then counts once. The language is removed only when it is the submission's own, so a
 * section named like a language code stays. Null when the header was not sent.
 */
export function pageOfReferer(referer: string | null, locale: string | null, context: PageContext = {}): string | null {
    if (!referer) {
        return null;
    }

    let path: string;
    try {
        path = new URL(referer, 'http://localhost').pathname;
    } catch {
        return referer;
    }

    try {
        path = decodeURIComponent(path);
    } catch {
        // A malformed escape: the path is shown as sent.
    }

    const stripPrefix = (prefix: string | undefined) => {
        if (prefix && (path === prefix || path.startsWith(`${prefix}/`))) {
            path = path.slice(prefix.length);
        }
    };

    stripPrefix(context.contextPath);
    path = path.replace(RENDER_PREFIX, '');
    stripPrefix(locale ? `/${locale}` : undefined);
    stripPrefix(context.siteKey ? `/sites/${context.siteKey}` : undefined);
    if (path.endsWith('.html')) {
        path = path.slice(0, -'.html'.length);
    }

    while (path.endsWith('/')) {
        path = path.slice(0, -1);
    }

    return path || '/';
}

export type AnalyticsPeriod = '7d' | '30d' | '90d' | '12m' | 'all';

export const ANALYTICS_PERIODS: AnalyticsPeriod[] = ['7d', '30d', '90d', '12m', 'all'];

const PERIOD_DAYS: Record<Exclude<AnalyticsPeriod, 'all'>, number> = {'7d': 7, '30d': 30, '90d': 90, '12m': 365};

const startOfDay = (date: Date): Date => new Date(date.getFullYear(), date.getMonth(), date.getDate());

/** The first day of a period that ends today, in the reader's zone; null for "all". */
export function periodStart(period: AnalyticsPeriod, now: Date): Date | null {
    if (period === 'all') {
        return null;
    }

    const start = startOfDay(now);
    start.setDate(start.getDate() - (PERIOD_DAYS[period] - 1));
    return start;
}

/** A local day as the `yyyy-MM-dd` value the submissions query takes. */
export function toDateValue(date: Date): string {
    const pad = (value: number) => String(value).padStart(2, '0');
    return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
}

export interface TimelineBucket {
    start: Date;
    count: number;
}

export interface Timeline {
    unit: 'day' | 'month';
    buckets: TimelineBucket[];
}

/** Past this many days a column per day is too thin to read: the timeline counts per month. */
const MAX_DAILY_BUCKETS = 92;

const DAY_MS = 24 * 60 * 60 * 1000;

const daysBetween = (from: Date, to: Date): number => Math.round((startOfDay(to).getTime() - startOfDay(from).getTime()) / DAY_MS) + 1;

/**
 * The submissions counted per day, or per month over a long span, from `start` to `end` in the reader's zone
 * with the empty days kept: a gap in the submissions must show as a gap.
 */
export function buildTimeline(dates: Date[], start: Date, end: Date): Timeline {
    const unit = daysBetween(start, end) > MAX_DAILY_BUCKETS ? 'month' : 'day';
    const bucketStart = (date: Date): Date => (unit === 'day' ? startOfDay(date) : new Date(date.getFullYear(), date.getMonth(), 1));
    const counts = new Map<number, number>();
    for (const date of dates) {
        const key = bucketStart(date).getTime();
        counts.set(key, (counts.get(key) ?? 0) + 1);
    }

    const buckets: TimelineBucket[] = [];
    const last = bucketStart(end).getTime();
    for (let cursor = bucketStart(start); cursor.getTime() <= last;) {
        buckets.push({start: cursor, count: counts.get(cursor.getTime()) ?? 0});
        cursor = unit === 'day'
            ? new Date(cursor.getFullYear(), cursor.getMonth(), cursor.getDate() + 1)
            : new Date(cursor.getFullYear(), cursor.getMonth() + 1, 1);
    }

    return {unit, buckets};
}

export interface CountEntry {
    /** Null is "unknown": a submission without a Referer, or without a language. */
    key: string | null;
    count: number;
    /** Share of the submissions, 0 to 1. */
    share: number;
}

function countBy(keys: Array<string | null>): CountEntry[] {
    const counts = new Map<string | null, number>();
    for (const key of keys) {
        counts.set(key, (counts.get(key) ?? 0) + 1);
    }

    return Array.from(counts.entries())
        .map(([key, count]) => ({key, count, share: count / keys.length}))
        .sort((a, b) => b.count - a.count || String(a.key).localeCompare(String(b.key)));
}

export interface FieldCompletion {
    name: string;
    label: string;
    filled: number;
    empty: number;
    /**
     * The submissions the field was shown in: all of them for a field without conditional logic; for a conditional
     * one, those that filled it or whose values prove it was displayed. A submission that cannot tell is left out.
     */
    shown: number;
    /** The field, or a container above it, carries conditional logic. */
    conditional: boolean;
    /** Share of the submissions the field was shown in that left it empty, 0 to 1. */
    emptyShare: number;
}

export interface DistributionEntry {
    value: string;
    label: string;
    count: number;
    /** Share of the submissions that answered the field (of all submissions for a yes/no), 0 to 1. */
    share: number;
}

export interface FieldDistribution {
    name: string;
    label: string;
    kind: 'choice' | 'boolean' | 'number';
    /** The submissions the shares are counted on. */
    answered: number;
    /** A submission may hold several answers: the shares then add up to more than 100%. */
    multiple: boolean;
    entries: DistributionEntry[];
    /** A number field only: the mean of the answers. */
    average?: number;
}

export interface FormAnalytics {
    total: number;
    /** The newest submission, an ISO date, or null without any. */
    lastCreated: string | null;
    /** Submissions per day over the span of the timeline. */
    dailyAverage: number;
    timeline: Timeline;
    pages: CountEntry[];
    languages: CountEntry[];
    completion: FieldCompletion[];
    distributions: FieldDistribution[];
}

/**
 * A number field is broken down answer by answer only when its answers read as a scale (a rating, a score):
 * whole numbers from 0 to this. An amount or a quantity gets its average alone.
 */
const MAX_SCALE_VALUE = 10;

const isChecked = (values: string[] | undefined): boolean => Boolean(values?.some(value => value !== 'false'));

function isFilled(submission: AnalyticsSubmission, field: AnalyticsField): boolean {
    if (field.kind === 'boolean') {
        return isChecked(submission.values.get(field.name));
    }

    return submission.values.has(field.name) || submission.fileFields.has(field.name);
}

/** The fields to report on: the form's, then those only the submissions know (a field since removed, a form since deleted). */
function reportedFields(submissions: AnalyticsSubmission[], form: AnalyticsFields): AnalyticsField[] {
    const known = new Set(form.fields.map(field => field.name));
    const extra: AnalyticsField[] = [];
    for (const submission of submissions) {
        for (const name of [...submission.values.keys(), ...submission.fileFields]) {
            if (!known.has(name)) {
                known.add(name);
                extra.push({name, label: form.labels.get(name) ?? name, kind: 'other', options: []});
            }
        }
    }

    return [...form.fields, ...extra];
}

function distributionOf(field: AnalyticsField, submissions: AnalyticsSubmission[]): FieldDistribution | null {
    if (field.kind === 'boolean') {
        const yes = submissions.filter(submission => isChecked(submission.values.get(field.name))).length;
        const share = (count: number) => (submissions.length === 0 ? 0 : count / submissions.length);
        return {
            name: field.name,
            label: field.label,
            kind: 'boolean',
            answered: submissions.length,
            multiple: false,
            entries: [
                {value: 'true', label: 'true', count: yes, share: share(yes)},
                {value: 'false', label: 'false', count: submissions.length - yes, share: share(submissions.length - yes)}
            ]
        };
    }

    if (field.kind !== 'choice' && field.kind !== 'number') {
        return null;
    }

    const counts = new Map<string, number>();
    let answered = 0;
    let multiple = false;
    for (const submission of submissions) {
        const values = submission.values.get(field.name);
        if (!values) {
            continue;
        }

        answered++;
        multiple ||= values.length > 1;
        for (const value of new Set(values)) {
            counts.set(value, (counts.get(value) ?? 0) + 1);
        }
    }

    const entry = (value: string, label: string): DistributionEntry =>
        ({value, label, count: counts.get(value) ?? 0, share: answered === 0 ? 0 : (counts.get(value) ?? 0) / answered});

    if (field.kind === 'number') {
        const numbers = Array.from(counts.entries()).map(([value, count]) => ({value, number: Number(value), count}))
            .filter(item => Number.isFinite(item.number));
        const sum = numbers.reduce((total, item) => total + (item.number * item.count), 0);
        const answers = numbers.reduce((total, item) => total + item.count, 0);
        numbers.sort((a, b) => a.number - b.number);
        return {
            name: field.name,
            label: field.label,
            kind: 'number',
            answered,
            multiple: false,
            entries: numbers.every(item => Number.isInteger(item.number) && item.number >= 0 && item.number <= MAX_SCALE_VALUE)
                ? numbers.map(item => entry(item.value, item.value))
                : [],
            average: answers === 0 ? undefined : sum / answers
        };
    }

    // The declared options in the form's order, the ones nobody chose included; then the answers the form
    // no longer offers (an option since removed, the options of a source), the most chosen first.
    const declared = new Set(field.options.map(option => option.value));
    const undeclared = Array.from(counts.keys()).filter(value => !declared.has(value))
        .sort((a, b) => (counts.get(b) ?? 0) - (counts.get(a) ?? 0) || a.localeCompare(b));
    return {
        name: field.name,
        label: field.label,
        kind: 'choice',
        answered,
        multiple,
        entries: [...field.options.map(option => entry(option.value, option.label)), ...undeclared.map(value => entry(value, value))]
    };
}

export interface AnalyticsContext extends PageContext {
    /** The first day of the period, or null to start at the oldest submission. */
    start: Date | null;
    now: Date;
}

export function buildAnalytics(submissions: AnalyticsSubmission[], form: AnalyticsFields, context: AnalyticsContext): FormAnalytics {
    const total = submissions.length;
    const dates = submissions.map(submission => new Date(submission.created)).filter(date => !Number.isNaN(date.getTime()));
    const oldest = dates.reduce<Date | null>((min, date) => (min === null || date < min ? date : min), null);
    const newest = dates.reduce<Date | null>((max, date) => (max === null || date > max ? date : max), null);
    const start = context.start ?? oldest ?? context.now;
    const timeline = buildTimeline(dates, start, context.now);
    const fields = reportedFields(submissions, form);

    // A field hidden by the form's conditional logic was not left empty: it is counted only where it was shown.
    const logic = buildFormLogic(form.logic);
    const shownIn = submissions.map(submission => logic.forSubmission(submission));
    const completion = fields.map(field => {
        let filled = 0;
        let empty = 0;
        submissions.forEach((submission, index) => {
            if (isFilled(submission, field)) {
                filled++;
            } else if (shownIn[index](field.name) === 'shown') {
                empty++;
            }
        });
        const shown = filled + empty;
        return {name: field.name, label: field.label, filled, empty, shown, conditional: logic.isConditional(field.name), emptyShare: shown === 0 ? 0 : empty / shown};
    });

    return {
        total,
        lastCreated: newest ? newest.toISOString() : null,
        dailyAverage: total / Math.max(1, daysBetween(start, context.now)),
        timeline,
        pages: countBy(submissions.map(submission => pageOfReferer(submission.referer, submission.locale, context))),
        languages: countBy(submissions.map(submission => submission.locale)),
        // The least filled first: that is the question the list answers. The form's order breaks a tie.
        completion: completion.map((item, index) => ({item, index}))
            .sort((a, b) => b.item.emptyShare - a.item.emptyShare || a.index - b.index).map(({item}) => item),
        distributions: fields.map(field => distributionOf(field, submissions)).filter((item): item is FieldDistribution => item !== null)
    };
}

/** A pie reads only with a few slices that add up to the whole: one answer per submission, six parts at most. */
export const MAX_PIE_SLICES = 6;

export function isPie(distribution: FieldDistribution): boolean {
    const chosen = distribution.entries.filter(entry => entry.count > 0).length;
    return distribution.kind === 'choice' && !distribution.multiple && chosen > 0 && chosen <= MAX_PIE_SLICES;
}
