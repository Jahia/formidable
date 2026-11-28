/**
 * The pure rules of the manager screen of the quarantine (docs/architecture/quarantine.md, "The GraphQL API"): which
 * buttons a submission gets, the range of a page, the code of a refused operation, and the site of the jContent route.
 */

/** The states of a held submission, as the API names them, in the order of the state filter. */
export const STATES = ['HELD', 'CHECKING', 'RELEASING', 'NEEDS_REVIEW', 'COMPLETED', 'REJECTED'] as const;
export type QuarantineState = typeof STATES[number];

/** The operations of a manager, in the fixed order of the buttons: Reject comes last, set apart. */
export const OPERATIONS = ['RETRY', 'RESUME', 'RELEASE', 'REJECT'] as const;
export type QuarantineOperation = typeof OPERATIONS[number];

/** The operations that open a confirm dialog before they run. */
export const CONFIRMED_OPERATIONS: ReadonlySet<QuarantineOperation> = new Set(['RELEASE', 'REJECT']);

/** The size of a page of the list. */
export const PAGE_SIZE = 25;

export type ChipColor = 'default' | 'accent' | 'success' | 'warning' | 'danger';

/** The colour of the chip of each state: two states share accent, so the label carries the meaning. */
export const STATE_COLORS: Record<QuarantineState, ChipColor> = {
    HELD: 'default',
    CHECKING: 'accent',
    RELEASING: 'accent',
    NEEDS_REVIEW: 'warning',
    COMPLETED: 'success',
    REJECTED: 'danger'
};

/** The verdicts of a check, as the API answers them. FAILED is a check that could not answer, not a FAIL. */
export const VERDICTS = ['PASS', 'FAIL', 'INCONCLUSIVE', 'PENDING', 'FAILED'] as const;
export type Verdict = typeof VERDICTS[number];

export const VERDICT_COLORS: Record<Verdict, ChipColor> = {
    PASS: 'success',
    FAIL: 'danger',
    INCONCLUSIVE: 'warning',
    PENDING: 'default',
    FAILED: 'warning'
};

/** The reasons that the screen has a phrase for; any other shows as its code alone. */
export const KNOWN_REASONS: ReadonlySet<string> = new Set([
    'CHECK_FAILED', 'INCONCLUSIVE', 'UNAVAILABLE', 'ATTEMPTS_EXHAUSTED', 'IN_PROCESS_FIRST', 'FILE_WITHHELD',
    'NO_CHECK', 'NO_FILE', 'INTEGRITY_FAILED', 'ACTION_FAILED', 'FORM_GONE'
]);

/** The stored names of the states, as the audit records them, by the name the API gives them. */
const STORED_STATES: Record<string, QuarantineState> = {
    held: 'HELD',
    checking: 'CHECKING',
    releasing: 'RELEASING',
    needsReview: 'NEEDS_REVIEW',
    completed: 'COMPLETED',
    rejected: 'REJECTED'
};

/** The state of a stored name of the audit, or undefined for a name the screen does not know. */
export function stateOfStored(stored: string): QuarantineState | undefined {
    return STORED_STATES[stored];
}

export function isVerdict(value: string): value is Verdict {
    return (VERDICTS as readonly string[]).includes(value);
}

export function isState(value: string): value is QuarantineState {
    return (STATES as readonly string[]).includes(value);
}

/**
 * The buttons of a submission: the operations that the API allows, and no other, in the fixed order of
 * {@link OPERATIONS}. An operation the screen does not know gets no button.
 */
export function buttonsFor(allowedOperations: readonly string[] | null | undefined): QuarantineOperation[] {
    const allowed = new Set(allowedOperations ?? []);
    return OPERATIONS.filter(operation => allowed.has(operation));
}

/** The range of a page: the first and the last item shown, from 1, and the total; 0 to 0 when the list is empty. */
export interface PageRange {
    from: number;
    to: number;
    total: number;
}

export function pageRange(offset: number, itemCount: number, total: number): PageRange {
    if (total <= 0 || itemCount <= 0) {
        return {from: 0, to: 0, total: Math.max(total, 0)};
    }

    return {from: offset + 1, to: offset + itemCount, total};
}

/**
 * The offset to ask again when a page came back empty although the list still holds submissions, as after an
 * operation took the last row of the last page: the start of the last page. null when the page is right as it is.
 */
export function offsetInsideList(offset: number, itemCount: number, total: number): number | null {
    if (itemCount > 0 || total <= 0 || offset < total) {
        return null;
    }

    return Math.floor((total - 1) / PAGE_SIZE) * PAGE_SIZE;
}

interface ErrorWithGraphQLErrors {
    graphQLErrors?: ReadonlyArray<{extensions?: Record<string, unknown> | null}>;
}

/**
 * Whether a refused operation means that the submission changed under the manager, read from extensions.code of its
 * GraphQL errors, never from a message: its state no longer allows the operation (CONFLICT), or it is gone from the
 * quarantine of the site (NOT_FOUND). Either way, a reload shows what it is now.
 */
export function isChangedUnderneath(error: unknown): boolean {
    const errors = (error as ErrorWithGraphQLErrors | null | undefined)?.graphQLErrors ?? [];
    return errors.some(one => one.extensions?.code === 'CONFLICT' || one.extensions?.code === 'NOT_FOUND');
}

interface UiContext {
    siteKey?: string;
    contextPath?: string;
    uilang?: string;
}

export function uiContext(): UiContext {
    return (globalThis as typeof globalThis & {contextJsParameters?: UiContext}).contextJsParameters ?? {};
}

const JCONTENT_APP_ROUTE = /^\/jahia\/jcontent\/([^/]+)\/[^/]+\/apps\//;

/**
 * The site key of a jContent app route (`<contextPath>/jahia/jcontent/<siteKey>/<lang>/apps/...`), or undefined
 * elsewhere. The pathname carries the servlet context path, hence the prefix (the same rule as the Results page of
 * formidable-engine).
 */
export function siteKeyFromRoute(pathname: string, contextPath = ''): string | undefined {
    if (!pathname.startsWith(contextPath)) {
        return undefined;
    }

    return JCONTENT_APP_ROUTE.exec(pathname.slice(contextPath.length))?.[1];
}

/** The site of the route, or the site of the jContent context when the route names none. */
export function currentSiteKey(): string | undefined {
    const context = uiContext();
    return siteKeyFromRoute(globalThis.location.pathname, context.contextPath ?? '') ?? context.siteKey;
}

/** A date of the API (ISO-8601, UTC) for a reader, in the language of the interface; the raw value when unreadable. */
export function formatDate(iso: string | null | undefined, language?: string): string {
    if (!iso) {
        return '';
    }

    const date = new Date(iso);
    if (Number.isNaN(date.getTime())) {
        return iso;
    }

    return new Intl.DateTimeFormat(language || undefined, {dateStyle: 'medium', timeStyle: 'short'}).format(date);
}

/** A size in bytes for a reader, with the unit and the number of the language of the interface. */
export function formatSize(bytes: number | null | undefined, language?: string): string {
    if (bytes === null || bytes === undefined || bytes < 0) {
        return '';
    }

    const format = (value: number, unit: string, digits: number) => new Intl.NumberFormat(language || undefined,
        {style: 'unit', unit, unitDisplay: 'short', maximumFractionDigits: digits}).format(value);
    if (bytes < 1024) {
        return format(bytes, 'byte', 0);
    }

    if (bytes < 1024 * 1024) {
        return format(bytes / 1024, 'kilobyte', 0);
    }

    return format(bytes / (1024 * 1024), 'megabyte', 1);
}

/** The files a row names: the first two, and how many more. */
export function visibleFileNames<T>(files: readonly T[]): {names: T[]; more: number} {
    return {names: files.slice(0, 2), more: Math.max(0, files.length - 2)};
}
