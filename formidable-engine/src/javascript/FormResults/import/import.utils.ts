import {uiContext} from '../FormResults.utils';

/** The states of an import job, as the engine stores them (ImportJobs.State). */
export type ImportJobState = 'analysing' | 'review' | 'importing' | 'done' | 'failed';

export interface ImportReportField {
    name: string;
    type: string;
    /** The titles of the source field by language: what the mapping matches the fields of an existing form on. */
    titles?: Record<string, string>;
    notes: string[];
}

/**
 * The results of a source form attached to an existing form of the site, with the field of that form each
 * source field lands under (docs/architecture/forms-import.md, "The mapping"); a field left out keeps its name.
 */
export interface AttachChoice {
    choice: 'attach';
    /** The identifier of the form, empty until one is picked. */
    form: string;
    mapping: Record<string, string>;
}

/** What receives the results of a source form: an entry alone (the default), a form created for them, or an existing form. */
export type ImportChoice = 'resultsOnly' | 'create' | AttachChoice;

export type ImportChoiceKind = 'resultsOnly' | 'create' | 'attach';

export const kindOf = (choice: ImportChoice | undefined): ImportChoiceKind => {
    if (choice === undefined) {
        return 'resultsOnly';
    }
    return typeof choice === 'string' ? choice : choice.choice;
};

export interface ImportReportForm {
    sourceName: string;
    titles: Record<string, string>;
    targetName: string | null;
    targetPath: string | null;
    outcome: 'created' | 'found' | 'resultsOnly' | 'attached' | null;
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
        body: JSON.stringify({choices: requestChoices(choices)})
    });

/**
 * The choices as the endpoint reads them: an attachment carries the names that change alone, a field sent to
 * the field of the same name keeps its name as if it were left out.
 */
export function requestChoices(choices: Record<string, ImportChoice>): Record<string, ImportChoice> {
    return Object.fromEntries(Object.entries(choices).map(([sourceName, choice]) => {
        if (typeof choice === 'string') {
            return [sourceName, choice];
        }
        const mapping = Object.fromEntries(Object.entries(choice.mapping).filter(([source, target]) => target && target !== source));
        return [sourceName, {choice: 'attach', form: choice.form, mapping}];
    }));
}

export const closeJob = (siteKey: string, jobId: string): Promise<unknown> =>
    call(endpoint(siteKey, `/jobs/${encodeURIComponent(jobId)}`), {method: 'DELETE'});

/** The title of a form in the user's language, else in the first language it has, else its source name. */
export function formTitle(form: ImportReportForm, language: string): string {
    const titles = form.titles ?? {};
    return titles[language] ?? Object.values(titles)[0] ?? form.sourceName;
}

/** The title of a source field in the user's language, else in the first language it has; empty when it has none. */
export function fieldTitle(field: ImportReportField, language: string): string {
    const titles = field.titles ?? {};
    return titles[language] ?? Object.values(titles)[0] ?? '';
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

// --- attaching to an existing form ---

/** A form of the site the results can attach to. */
export interface SiteForm {
    uuid: string;
    name: string;
    path: string;
    title: string;
}

/** A field of the form the results attach to, as the mapping offers it: its node name and its label in the user's language. */
export interface TargetField {
    name: string;
    label: string;
}

/** How a field of the form came to be proposed for a source field. */
export type MappingRule = 'sameName' | 'sameLabel';

export interface MappingProposal {
    target: string;
    rule: MappingRule;
}

/** The JCR-SQL2 statement listing the forms of the site, in the edit workspace where an unpublished form stands too. */
export const buildSiteFormsQuery = (siteKey: string): string =>
    `SELECT * FROM [fmdb:form] AS form WHERE ISDESCENDANTNODE(form, '/sites/${siteKey}')`;

type GqlSiteFormsResponse = {
    jcr?: {nodesByQuery?: {nodes?: Array<{uuid: string; name: string; path: string; displayName?: string}>}};
};

/** Reads GET_SITE_FORMS: the forms by title, then by path for two forms of one title. */
export function parseSiteForms(data: GqlSiteFormsResponse | undefined): SiteForm[] {
    const nodes = data?.jcr?.nodesByQuery?.nodes ?? [];
    return nodes
        .map(node => ({uuid: node.uuid, name: node.name, path: node.path, title: node.displayName || node.name}))
        .sort((a, b) => a.title.localeCompare(b.title) || a.path.localeCompare(b.path));
}

type GqlMappableFieldsResponse = {
    jcr?: {nodeById?: {fields?: {nodes?: Array<{descendants?: {nodes?: Array<{name: string; displayName?: string; nonSubmittable?: boolean}>}}>}}};
};

/**
 * Reads GET_FORM_FIELD_LABELS for the mapping: the fields a submission holds a value for, in the order of the
 * form, without the fieldsets and buttons that carry a title but no value.
 */
export function parseTargetFields(data: GqlMappableFieldsResponse | undefined): TargetField[] {
    const nodes = data?.jcr?.nodeById?.fields?.nodes?.[0]?.descendants?.nodes ?? [];
    return nodes
        .filter(node => typeof node.name === 'string' && node.name && !node.nonSubmittable)
        .map(node => ({name: node.name, label: node.displayName || node.name}));
}

/**
 * The field of the form proposed for each source field (docs/architecture/forms-import.md, "The mapping"):
 * the field of the same node name, else the field whose label equals the title of the source field in the
 * language of the user. A field of the form is proposed once: a submission that held two names sent to the
 * same field would lose one of its values.
 */
export function proposeMapping(sourceFields: ImportReportField[], targetFields: TargetField[], language: string): Record<string, MappingProposal> {
    const proposals: Record<string, MappingProposal> = {};
    const taken = new Set<string>();
    const byName = new Map(targetFields.map(field => [field.name, field]));
    sourceFields.forEach(field => {
        if (byName.has(field.name) && !taken.has(field.name)) {
            proposals[field.name] = {target: field.name, rule: 'sameName'};
            taken.add(field.name);
        }
    });
    sourceFields.forEach(field => {
        if (proposals[field.name]) {
            return;
        }
        const title = (field.titles?.[language] ?? '').trim();
        const match = title && targetFields.find(target => !taken.has(target.name) && target.label.trim() === title);
        if (match) {
            proposals[field.name] = {target: match.name, rule: 'sameLabel'};
            taken.add(match.name);
        }
    });
    return proposals;
}

/** The mapping the proposals make, to start from. */
export const mappingOf = (proposals: Record<string, MappingProposal>): Record<string, string> =>
    Object.fromEntries(Object.entries(proposals).map(([source, proposal]) => [source, proposal.target]));

/** The name the values of a field land under: the field of the mapping, else the name kept. */
export const landingName = (field: string, mapping: Record<string, string>): string => mapping[field] || field;

/** The names two source fields would both land under, where a submission holding both would lose a value. */
export function mappingConflicts(sourceFields: ImportReportField[], mapping: Record<string, string>): string[] {
    const sourcesByName = new Map<string, number>();
    sourceFields.forEach(field => {
        const name = landingName(field.name, mapping);
        sourcesByName.set(name, (sourcesByName.get(name) ?? 0) + 1);
    });
    return [...sourcesByName.entries()].filter(([, count]) => count > 1).map(([name]) => name);
}

/** Why an attachment cannot start yet: no form picked, or a name two fields land under. */
export function attachProblem(choice: AttachChoice, sourceFields: ImportReportField[]): 'noForm' | 'conflict' | null {
    if (!choice.form) {
        return 'noForm';
    }
    return mappingConflicts(sourceFields, choice.mapping).length > 0 ? 'conflict' : null;
}

/** Whether every choice of the review can start the import: an attachment names its form and lands no two fields together. */
export function choicesReady(choices: Record<string, ImportChoice>, report: ImportReport): boolean {
    return report.forms.every(form => {
        const choice = choices[form.sourceName];
        return choice === undefined || typeof choice === 'string' || attachProblem(choice, form.fields) === null;
    });
}
