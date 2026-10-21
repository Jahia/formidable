import {uiContext} from './page';

/** The states of an import job, as the engine stores them (ImportJobs.State). */
export type ImportJobState = 'analysing' | 'review' | 'importing' | 'done' | 'failed';

/**
 * A line of the report about a form or a field (ImportNote): a code the dialog translates under
 * formResults.import.note, its parameters, and whether it asks for something to be done by hand.
 */
export interface ImportNote {
    code: string;
    level: 'info' | 'action';
    params: Record<string, string>;
}

export interface ImportReportField {
    name: string;
    type: string;
    /** The titles of the source field by language: what the mapping matches the fields of an existing form on. */
    titles?: Record<string, string>;
    /** The distinct values the submissions hold for the field, up to a cap: matched against the options of a choice field. */
    values?: string[];
    /** Whether the submissions hold more distinct values than listed. */
    moreValues?: boolean;
    notes: ImportNote[];
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

/**
 * What receives the results of a source form: an entry alone, a form created for them, an existing form, or
 * nothing, the form left out of the run — the default, until the administrator chooses.
 */
export type ImportChoice = 'resultsOnly' | 'create' | 'skip' | AttachChoice;

export type ImportChoiceKind = 'resultsOnly' | 'create' | 'attach' | 'skip';

export const kindOf = (choice: ImportChoice | undefined): ImportChoiceKind => {
    if (choice === undefined) {
        return 'skip';
    }
    return typeof choice === 'string' ? choice : choice.choice;
};

/**
 * A choice made for every form of the review at once: import the results (alone for a new form, added to its
 * entry for a form imported before), create the forms, or import nothing. An attachment names its form, so it
 * stays a choice per form.
 */
export type ChoiceForAll = 'import' | 'create' | 'skip';

/** Whether the review lets the administrator choose for the form: a form imported before with nothing new has nothing to choose. */
export const isChoosable = (form: ImportReportForm): boolean => !(form.outcome === 'found' && form.submissions.toImport === 0);

/** The choices of every choosable form of the report for a choice made for all: a found form takes or leaves its new submissions. */
export function choicesForAll(report: ImportReport, value: ChoiceForAll): Record<string, ImportChoice> {
    return Object.fromEntries(report.forms.filter(isChoosable).map(form => {
        if (value === 'skip') {
            return [form.sourceName, 'skip'];
        }
        return [form.sourceName, value === 'create' && form.outcome !== 'found' ? 'create' : 'resultsOnly'];
    }));
}

/**
 * The choice for all the rows of the review agree on, null when they differ: what the menu for all shows. A
 * found form that takes its new submissions agrees with importing and with creating alike.
 */
export function choiceForAllOf(report: ImportReport, choices: Record<string, ImportChoice>): ChoiceForAll | null {
    const agreeing: ChoiceForAll[][] = report.forms.filter(isChoosable).map(form => {
        const kind = kindOf(choices[form.sourceName]);
        return kind !== 'skip' && form.outcome === 'found' ? ['import', 'create'] : FOR_ALL_OF_KIND[kind];
    });
    if (agreeing.length === 0) {
        return null;
    }
    const common = agreeing.reduce((kept, values) => kept.filter(value => values.includes(value)), FOR_ALL_VALUES);
    return common.includes('import') ? 'import' : common[0] ?? null;
}

const FOR_ALL_VALUES: ChoiceForAll[] = ['import', 'create', 'skip'];

/** The choices for all a choice of one form agrees with; an attachment agrees with none. */
const FOR_ALL_OF_KIND: Record<ImportChoiceKind, ChoiceForAll[]> = {resultsOnly: ['import'], create: ['create'], attach: [], skip: ['skip']};

/**
 * Something a submission lost or kept as it was, or why it was not written (ImportReport, incidents): a note
 * with the submission it is about, by its identity in the source and its date. Listed by an import only.
 */
export interface ImportIncident extends ImportNote {
    submission: string;
    created: string | null;
}

export interface ImportReportForm {
    sourceName: string;
    titles: Record<string, string>;
    targetName: string | null;
    targetPath: string | null;
    outcome: 'created' | 'found' | 'resultsOnly' | 'attached' | 'skipped' | null;
    fields: ImportReportField[];
    notes: ImportNote[];
    submissions: {found: number; imported: number; alreadyImported: number; failed?: number; toImport: number};
    values: {converted: number; dropped: number; notConverted: number};
    files: {count: number; bytes: number; missing: number};
    incidents?: ImportIncident[];
    /** The incidents past the cap the report lists: only counted. */
    incidentsOmitted?: number;
}

export interface ImportReport {
    dryRun: boolean;
    importedFormsFolder: string | null;
    forms: ImportReportForm[];
    totals: {forms: number; submissionsFound: number; submissionsToImport: number; submissionsImported: number; submissionsFailed?: number};
    nothingToImport: boolean;
    /** Why the import stopped before its end; null when it ran to its end. */
    stopped?: string | null;
    /** The incidents of no form of the export: submissions of a results entry it holds no form for. */
    incidents?: ImportIncident[];
    incidentsOmitted?: number;
}

export interface ImportJob {
    id: string;
    state: ImportJobState;
    report: ImportReport | null;
    message: string | null;
    updated: string;
}

export interface ImportSettings {
    /** The module is enabled on the site: the one switch of the import. */
    enabled: boolean;
    /** Enabled on the site, and the user administers it: the button shows. */
    allowed: boolean;
    maxFileSizeMb: number;
    job?: ImportJob | null;
}

/** The dialog polls a running job at this pace. */
export const POLL_INTERVAL_MS = 1000;

const endpoint = (siteKey: string, path: string): string =>
    `${uiContext().contextPath ?? ''}/modules/formidable-forms-import/import${path}?site=${encodeURIComponent(siteKey)}`;

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

/**
 * Starts the import with the choices of the review; a form the choices do not name is left out. A form found with
 * nothing new, which the review offers no choice for, is sent as taking its results, which adds nothing to it, so
 * that the import reports it found, as the review did, and not left out.
 */
export const startImport = (siteKey: string, jobId: string, choices: Record<string, ImportChoice> = {}, report?: ImportReport): Promise<ImportJob> =>
    call(endpoint(siteKey, `/jobs/${encodeURIComponent(jobId)}/import`), {
        method: 'POST',
        headers: {'Content-Type': 'application/json'},
        body: JSON.stringify({choices: requestChoices(withFoundForms(choices, report))})
    });

/** The choices with every found form that has nothing to choose taking its results: what keeps it found. */
export function withFoundForms(choices: Record<string, ImportChoice>, report?: ImportReport): Record<string, ImportChoice> {
    const found = (report?.forms ?? []).filter(form => form.outcome === 'found' && !isChoosable(form));
    return {...Object.fromEntries(found.map(form => [form.sourceName, 'resultsOnly' as const])), ...choices};
}

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

/**
 * The notes of a form and of its fields, one line per distinct note with the fields it concerns, split into what
 * asks for something to be done by hand and what was carried over and is only worth knowing.
 */
export function groupedNotes(form: ImportReportForm, language: string): {action: ReportLine[]; info: ReportLine[]} {
    const lines = new Map<string, ReportLine>();
    const add = (note: ImportNote, field: string | null) => {
        const key = JSON.stringify([note.code, note.params]);
        const line = lines.get(key) ?? {note, fields: []};
        if (field && !line.fields.includes(field)) {
            line.fields.push(field);
        }
        lines.set(key, line);
    };
    form.notes.forEach(note => add(note, null));
    form.fields.forEach(field => field.notes.forEach(note => add(note, fieldTitle(field, language) || field.name)));
    const all = [...lines.values()];
    return {action: all.filter(line => line.note.level === 'action'), info: all.filter(line => line.note.level === 'info')};
}

/** A note of the report with the fields it concerns, by their label; none for a note about the form itself. */
export interface ReportLine {
    note: ImportNote;
    fields: string[];
}

/**
 * What the import will do with the choices of the review: the forms it writes for and the submissions it imports.
 * A form left out counts for neither; a form found from an earlier run counts when it has new submissions.
 */
export function plannedImport(report: ImportReport, choices: Record<string, ImportChoice>): {forms: number; submissions: number} {
    const kept = report.forms.filter(form => kindOf(choices[form.sourceName]) !== 'skip');
    return {
        forms: kept.filter(form => form.outcome !== 'found' || form.submissions.toImport > 0).length,
        submissions: kept.reduce((sum, form) => sum + form.submissions.toImport, 0)
    };
}

/** The local name of a field type, the key of its label under formResults.import.types: fmdb:inputText gives inputText. */
export const typeKey = (type: string | undefined): string => (type ?? '').slice((type ?? '').indexOf(':') + 1);

// --- attaching to an existing form ---

/** A form of the site the results can attach to. */
export interface SiteForm {
    uuid: string;
    name: string;
    path: string;
    title: string;
}

/** A field of the form the results attach to, as the mapping offers it: its node name, its label in the user's language, and its options. */
export interface TargetField {
    name: string;
    label: string;
    /** The values of the options of a choice field with manual options; null for any other field. */
    options: string[] | null;
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

/** The variables of GET_SITE_FORMS: the forms of the site in the language of the user, and the site node itself. */
export const siteFormsVariables = (siteKey: string, language: string) =>
    ({formsQuery: buildSiteFormsQuery(siteKey), language, sitePath: `/sites/${siteKey}`});

type GqlSiteFormsResponse = {
    jcr?: {
        nodesByQuery?: {nodes?: Array<{uuid: string; name: string; path: string; displayName?: string}>};
        site?: {defaultLanguage?: {value?: string | null} | null} | null;
    };
};

/**
 * The default language of the site, from GET_SITE_FORMS: the options of a choice field are read there, where
 * the editor always writes them, whatever language the user works in; null when the site does not say.
 */
export function parseSiteDefaultLanguage(data: GqlSiteFormsResponse | undefined): string | null {
    const language = data?.jcr?.site?.defaultLanguage?.value;
    return language || null;
}

/** Reads GET_SITE_FORMS: the forms by title, then by path for two forms of one title. */
export function parseSiteForms(data: GqlSiteFormsResponse | undefined): SiteForm[] {
    const nodes = data?.jcr?.nodesByQuery?.nodes ?? [];
    return nodes
        .map(node => ({uuid: node.uuid, name: node.name, path: node.path, title: node.displayName || node.name}))
        .sort((a, b) => a.title.localeCompare(b.title) || a.path.localeCompare(b.path));
}

type GqlMappableFieldsResponse = {
    jcr?: {nodeById?: {fields?: {nodes?: Array<{descendants?: {nodes?: Array<{
        name: string;
        displayName?: string;
        nonSubmittable?: boolean;
        options?: {values?: string[]} | null;
    }>}}>}}};
};

/** The values of the manual options of a field, stored as one JSON per option; null when the field holds none. */
function optionValuesOf(stored: string[] | undefined | null): string[] | null {
    if (!stored || stored.length === 0) {
        return null;
    }
    const values: string[] = [];
    stored.forEach(option => {
        try {
            const parsed = JSON.parse(option) as {value?: unknown};
            if (typeof parsed.value === 'string') {
                values.push(parsed.value);
            }
        } catch {
            // not an option as the editor stores them: ignored
        }
    });
    return values;
}

/**
 * Reads GET_FORM_FIELD_LABELS for the mapping: the fields a submission holds a value for, in the order of the
 * form, without the fieldsets and buttons that carry a title but no value.
 */
export function parseTargetFields(data: GqlMappableFieldsResponse | undefined): TargetField[] {
    const nodes = data?.jcr?.nodeById?.fields?.nodes?.[0]?.descendants?.nodes ?? [];
    return nodes
        .filter(node => typeof node.name === 'string' && node.name && !node.nonSubmittable)
        .map(node => ({name: node.name, label: node.displayName || node.name, options: optionValuesOf(node.options?.values)}));
}

/** The values of a source field that no option of the field it lands under carries; none for a field without options. */
export function unmatchedValues(field: ImportReportField, target: TargetField | undefined): string[] {
    if (!target?.options) {
        return [];
    }
    const options = new Set(target.options);
    return (field.values ?? []).filter(value => !options.has(value));
}

/** The names of the other fields of the source, which a submission may hold alongside this one. */
const othersOf = (field: ImportReportField, sourceFields: ImportReportField[]): string[] =>
    sourceFields.filter(other => other.name !== field.name).map(other => other.name);

/** The renames of a mapping: a field sent under its own name, as the proposal writes it, is no rename. */
const renamesOf = (mapping: Record<string, string>): Array<[string, string]> =>
    Object.entries(mapping).filter(([source, target]) => Boolean(target) && target !== source);

/**
 * The fields of the form a row may pick: not one picked by another row, nor the name of another field of
 * the source, so that no two fields land together and no rename chains on another (the row keeps its own pick).
 */
export function availableTargets(field: ImportReportField, sourceFields: ImportReportField[], mapping: Record<string, string>, targets: TargetField[]): TargetField[] {
    const taken = new Set<string>(othersOf(field, sourceFields));
    renamesOf(mapping).filter(([source]) => source !== field.name).forEach(([source, target]) => {
        taken.add(source);
        taken.add(target);
    });
    const own = mapping[field.name];
    return targets.filter(target => target.name === own || !taken.has(target.name));
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

/**
 * The names the mapping cannot be applied with: a target that is itself sent elsewhere, which a rename cannot
 * reach in one pass, and a name two fields of the source would both land under, where a submission holding
 * both would lose a value.
 */
export function mappingConflicts(sourceFields: ImportReportField[], mapping: Record<string, string>): string[] {
    const conflicts = new Set<string>();
    const renames = renamesOf(mapping);
    const renamed = new Set(renames.map(([source]) => source));
    const targets = new Set<string>();
    renames.forEach(([, target]) => {
        // two fields sent to one, or a target that is sent elsewhere itself: refused on the mapping alone
        if (targets.has(target) || renamed.has(target)) {
            conflicts.add(target);
        }
        targets.add(target);
    });
    sourceFields.forEach(field => {
        const landing = landingName(field.name, mapping);
        othersOf(field, sourceFields).forEach(other => {
            if (landingName(other, mapping) === landing) {
                conflicts.add(landing);
            }
        });
    });
    return [...conflicts];
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
    const complete = report.forms.every(form => {
        const choice = choices[form.sourceName];
        return choice === undefined || typeof choice === 'string' || attachProblem(choice, form.fields) === null;
    });
    // every form left out leaves nothing to import
    return complete && plannedImport(report, choices).forms > 0;
}
