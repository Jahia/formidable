import {type ImportNote, type ReportLine, typeKey} from './import.utils';

type Translate = (key: string, options?: Record<string, unknown>) => string;

/** The parameters of a note that name a type or a setting, and the keys under formResults.import that word them. */
const TERM_KEYS: Record<string, string> = {
    type: 'types', preferred: 'types', after: 'after', rule: 'terms.rules', prefill: 'terms.prefills', action: 'terms.actions',
    kind: 'terms.kinds', source: 'terms.sources', setting: 'terms.settings'
};

/**
 * A note in words: its sentence, with every parameter that names a Formidable type, a Forms type or a setting
 * translated. A value the locales do not know reads as words drawn from its name, without its namespace.
 */
export function noteText(note: ImportNote, t: Translate): string {
    const params: Record<string, unknown> = {...note.params};
    Object.entries(TERM_KEYS).forEach(([key, keys]) => {
        const value = note.params[key];
        if (value) {
            params[key] = t(`formResults.import.${keys}.${typeKey(value)}`, {defaultValue: wordsOf(value)});
        }
    });
    return t(`formResults.import.note.${note.code}`, {...params, defaultValue: note.code});
}

/** fcnt:regexValidation gives "regex", matrixCheckBoxes "matrix check boxes": the name without its namespace, its kind, its case. */
export const wordsOf = (value: string): string => typeKey(value)
    .replace(/(Validation|Definition|Action|Prefill)$/, '')
    .replaceAll(/([a-z0-9])([A-Z])/g, '$1 $2')
    .toLowerCase();

/** A field of the form whose Forms prefill became a mapping to a visitor profile property, or failed to. */
export interface ProfileMapping {
    field: string;
    property: string;
    /** mapped; unchecked when jCustomer could not be asked; failed when the mapping was not written */
    status: 'mapped' | 'unchecked' | 'failed';
    prefilled: boolean;
    /** What follows the prefill: editable, readOnly or hidden; absent without prefill. */
    after?: string;
    /** For a failed mapping, the code of the note that says why. */
    reason?: string;
}

const MAPPED_CODES = new Set(['field.profilePrefilled', 'field.profileMapped']);
/** The notes of a prefill from the visitor profile that did not become a mapping, shown in the mapping section. */
const FAILED_CODES = new Set(['field.prefillUnknownProperty', 'field.prefillIncompatible', 'field.prefillNoIntegration', 'field.prefillNotMappable']);
const UNCHECKED_CODE = 'field.profileUnchecked';

/**
 * The profile mappings of a form, one per field — the ones written, and the ones that failed, which the notes to
 * review hold — apart from the other lines; and what follows the prefill when every prefilled field shares it,
 * so that it is said once.
 */
export function profileMappings(info: ReportLine[], action: ReportLine[] = []): {
    mappings: ProfileMapping[]; others: ReportLine[]; toReview: ReportLine[]; commonAfter: string | null;
} {
    const mappings: ProfileMapping[] = [];
    const add = (line: ReportLine, status: ProfileMapping['status']) => line.fields.forEach(field => mappings.push({
        field,
        property: line.note.params.property ?? '',
        status,
        prefilled: line.note.code === 'field.profilePrefilled',
        after: line.note.params.after,
        reason: status === 'failed' ? line.note.code : undefined
    }));
    const others = info.filter(line => {
        if (MAPPED_CODES.has(line.note.code)) {
            add(line, 'mapped');
            return false;
        }
        return true;
    });
    const toReview = action.filter(line => {
        if (FAILED_CODES.has(line.note.code)) {
            add(line, 'failed');
            return false;
        }
        // shown on the line of its field, in the mapping section
        return line.note.code !== UNCHECKED_CODE;
    });
    // a mapping written while jCustomer could not be asked: the same field, flagged
    action.filter(line => line.note.code === UNCHECKED_CODE).forEach(line => line.fields.forEach(field => {
        mappings.filter(mapping => mapping.field === field).forEach(mapping => {
            mapping.status = 'unchecked';
        });
    }));
    const afters = new Set(mappings.filter(mapping => mapping.status !== 'failed' && mapping.prefilled).map(mapping => mapping.after));
    return {mappings, others, toReview, commonAfter: afters.size === 1 ? [...afters][0] ?? null : null};
}
