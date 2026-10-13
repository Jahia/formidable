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

/** A field of the form mapped to a visitor profile property, as the report states it. */
export interface ProfileMapping {
    field: string;
    property: string;
    prefilled: boolean;
    /** What follows the prefill: editable, readOnly or hidden; absent without prefill. */
    after?: string;
}

const PROFILE_CODES = new Set(['field.profilePrefilled', 'field.profileMapped']);

/**
 * The profile mappings among the lines of what was carried over, one per field, apart from the other lines; and
 * what follows the prefill when every prefilled field shares it, so that it is said once.
 */
export function profileMappings(lines: ReportLine[]): {mappings: ProfileMapping[]; others: ReportLine[]; commonAfter: string | null} {
    const mappings: ProfileMapping[] = [];
    const others: ReportLine[] = [];
    lines.forEach(line => {
        if (PROFILE_CODES.has(line.note.code)) {
            line.fields.forEach(field => mappings.push({
                field,
                property: line.note.params.property ?? '',
                prefilled: line.note.code === 'field.profilePrefilled',
                after: line.note.params.after
            }));
        } else {
            others.push(line);
        }
    });
    const afters = new Set(mappings.filter(mapping => mapping.prefilled).map(mapping => mapping.after));
    return {mappings, others, commonAfter: afters.size === 1 ? [...afters][0] ?? null : null};
}
