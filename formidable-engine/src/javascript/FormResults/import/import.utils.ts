import {uiContext} from '../FormResults.utils';

/** The states of an import job, as the engine stores them (ImportJobs.State). */
export type ImportJobState = 'analysing' | 'review' | 'importing' | 'done' | 'failed';

export interface ImportReportField {
    name: string;
    type: string;
    notes: string[];
}

/** What receives the results of a source form: an entry alone (the default), or a form created for them. */
export type ImportChoice = 'resultsOnly' | 'create';

export interface ImportReportForm {
    sourceName: string;
    titles: Record<string, string>;
    targetName: string | null;
    targetPath: string | null;
    outcome: 'created' | 'found' | 'resultsOnly' | null;
    fields: ImportReportField[];
    notes: string[];
    submissions: {found: number; imported: number; alreadyImported: number; toImport: number};
    values: {converted: number; dropped: number; notConverted: number};
    files: {count: number; bytes: number; missing: number};
}

export interface ImportReport {
    dryRun: boolean;
    importedFormsFolder: string | null;
    forms: ImportReportForm[];
    totals: {forms: number; submissionsFound: number; submissionsToImport: number; submissionsImported: number};
    nothingToImport: boolean;
}

export interface ImportJob {
    id: string;
    state: ImportJobState;
    report: ImportReport | null;
    message: string | null;
    updated: string;
}

export interface ImportSettings {
    enabled: boolean;
    allowed: boolean;
    maxFileSizeMb: number;
    job?: ImportJob | null;
}

/** The dialog polls a running job at this pace. */
export const POLL_INTERVAL_MS = 1000;

const endpoint = (siteKey: string, path: string): string =>
    `${uiContext().contextPath ?? ''}/modules/formidable-engine/import${path}?site=${encodeURIComponent(siteKey)}`;

async function call<T>(url: string, init: RequestInit = {}): Promise<T> {
    const response = await fetch(url, {credentials: 'same-origin', ...init});
    const body = await response.json().catch(() => ({})) as T & {error?: string};
    if (!response.ok) {
        throw new Error(body.error ?? `${response.status} ${response.statusText}`);
    }
    return body;
}

export const fetchSettings = (siteKey: string): Promise<ImportSettings> =>
    call(endpoint(siteKey, '/settings'));

export const uploadExport = (siteKey: string, file: File): Promise<ImportJob> => {
    const form = new FormData();
    form.append('file', file, file.name);
    return call(endpoint(siteKey, '/jobs'), {method: 'POST', body: form});
};

export const fetchJob = (siteKey: string, jobId: string): Promise<ImportJob> =>
    call(endpoint(siteKey, `/jobs/${encodeURIComponent(jobId)}`));

/** Starts the import with the choices of the review; a form the choices do not name takes the results alone. */
export const startImport = (siteKey: string, jobId: string, choices: Record<string, ImportChoice> = {}): Promise<ImportJob> =>
    call(endpoint(siteKey, `/jobs/${encodeURIComponent(jobId)}/import`), {
        method: 'POST',
        headers: {'Content-Type': 'application/json'},
        body: JSON.stringify({choices})
    });

export const closeJob = (siteKey: string, jobId: string): Promise<unknown> =>
    call(endpoint(siteKey, `/jobs/${encodeURIComponent(jobId)}`), {method: 'DELETE'});

/** The title of a form in the user's language, else in the first language it has, else its source name. */
export function formTitle(form: ImportReportForm, language: string): string {
    const titles = form.titles ?? {};
    return titles[language] ?? Object.values(titles)[0] ?? form.sourceName;
}

/** Whether a dropped or chosen file can be an export: a zip, under the bound. Returns the reason key otherwise. */
export function refuseFile(file: File, maxFileSizeMb: number): 'notZip' | 'tooLarge' | null {
    const isZip = file.name.toLowerCase().endsWith('.zip') || file.type === 'application/zip' || file.type === 'application/x-zip-compressed';
    if (!isZip) {
        return 'notZip';
    }
    if (file.size > maxFileSizeMb * 1024 * 1024) {
        return 'tooLarge';
    }
    return null;
}

/** The notes of the fields, one line per distinct note with the fields it concerns: a prefill note repeats on every field. */
export function groupedFieldNotes(fields: ImportReportField[]): string[] {
    const fieldsByNote = new Map<string, string[]>();
    fields.forEach(field => field.notes.forEach(note => {
        fieldsByNote.set(note, [...(fieldsByNote.get(note) ?? []), field.name]);
    }));
    return [...fieldsByNote.entries()].map(([note, names]) => `${note} — ${names.join(', ')}`);
}
