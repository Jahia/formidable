// Every field type says what it submits, once. Usage: node check-field-markers.mjs [repoRoot]
//
// A node type extending fmdbmix:element or fmdbmix:formElement declares exactly one of the engine's three
// markers: fmdbmix:submittableField (a value — the field-actions switch and whatever else attaches to that
// marker with `extends`), fmdbmix:nonSubmittable (nothing: a button, a fieldset, a container of the
// kind), fmdbmix:fileField (a file, read from its multipart part). The pipeline keeps working without the
// positive marker — it tests formElement && !nonSubmittable — so a field type that forgets it is still
// submitted and nothing fails: it silently lacks the switch. This is what fails instead, at build time,
// for the next built-in field type (#345; docs/architecture/cnd-module-ownership.md).
//
// Second check: a type carrying fmdbmix:sensitiveField (a password), declared or inherited, carries none of
// fmdbmix:profileMappableField, fmdbmix:textField and fmdbmix:fileField. The first would offer the secret to a
// jCustomer profile property, the second to conditional logic as an operand, the third would make it an upload,
// which the sensitive split does not cover (decision D4). A CND cannot forbid a pair of supertypes, so the
// engine and the jExperience module already ignore these markers on a sensitive type at runtime ("sensitive
// wins", #447; docs/architecture/form-submission-flow.md) — that guard is silent: the mapping section, the
// logic source and the uploads vanish and only a WARN says why. This makes the mistake fail at build time instead, for the types
// of this repository; a third-party module's CND never comes here, which is why the runtime guard is the authority.
// A marker counts whichever type of the repository declares it: a subtype of fmdbext:password adding
// fmdbmix:textField fails, and so does a type reaching both through its supertypes.
//
// Every *.cnd of the repository is read (node_modules, target and dist excluded); a mixin declaration is
// not a field type and is skipped by the first check, not by the second. A declaration's supertypes are read
// up to its options or its first property, child node or declaration, so a list split over several lines
// counts whole. The first check reads the supertypes a type declares, the second the ones it inherits too.
import {readdirSync, readFileSync, statSync} from 'node:fs';
import {join, relative, resolve} from 'node:path';

const root = resolve(process.argv[2] ?? '.');
const ELEMENT_MARKERS = new Set(['fmdbmix:element', 'fmdbmix:formElement']);
const KIND_MARKERS = ['fmdbmix:submittableField', 'fmdbmix:nonSubmittable', 'fmdbmix:fileField'];
const SENSITIVE_MARKER = 'fmdbmix:sensitiveField';
// Each marker a sensitive type never carries, with what it would do to the secret.
const NOT_WITH_SENSITIVE = {
    'fmdbmix:profileMappableField': 'mapped to a profile',
    'fmdbmix:textField': 'read by conditional logic',
    'fmdbmix:fileField': 'uploaded as a file',
};
const SKIPPED_DIRS = new Set(['node_modules', 'target', 'dist', '.git']);
const NAME = String.raw`[\w]+:[\w]+`;

const cndFiles = dir => readdirSync(dir).flatMap(name => {
    if (SKIPPED_DIRS.has(name)) return [];
    const path = join(dir, name);
    if (statSync(path).isDirectory()) return cndFiles(path);
    return name.endsWith('.cnd') ? [path] : [];
});

// Each declaration of a file: its name, where it starts, the supertypes it declares and whether it is a mixin.
// Its header runs from `[name]` to its first property (-), child node (+), namespace (<) or the next declaration.
const declarationsOf = file => {
    const headers = [];
    let header = null;
    readFileSync(file, 'utf8').split('\n').forEach((raw, index) => {
        const line = raw.replace(/\/\/.*$/, '');
        const opening = line.match(new RegExp(String.raw`^\s*\[(${NAME})]\s*(.*)$`));
        if (opening) {
            header = {name: opening[1], where: `${relative(root, file)}:${index + 1}`, text: opening[2]};
            headers.push(header);
        } else if (/^\s*[-+<]/.test(line)) {
            header = null;
        } else if (header) {
            header.text += ` ${line}`;
        }
    });
    return headers.map(({name, where, text}) => {
        const list = text.match(new RegExp(String.raw`^\s*>\s*(${NAME}(?:\s*,\s*${NAME})*)`));
        const options = list ? text.slice(list[0].length) : text;
        return {
            name,
            where,
            supertypes: list ? list[1].split(',').map(supertype => supertype.trim()) : [],
            mixin: /\bmixin\b/.test(options),
        };
    });
};

const declarations = cndFiles(root).flatMap(declarationsOf);

// The supertypes each type of the repository declares, every declaration of a name merged.
const declared = new Map();
for (const {name, supertypes} of declarations) {
    declared.set(name, new Set([...(declared.get(name) ?? []), ...supertypes]));
}
// Every supertype of a type, inherited ones included; a type the repository does not declare adds none.
const inherited = (name, found = new Set()) => {
    for (const supertype of declared.get(name) ?? []) {
        if (found.has(supertype)) continue;
        found.add(supertype);
        inherited(supertype, found);
    }
    return found;
};

const errors = [];
let fieldTypes = 0;
let sensitiveTypes = 0;
for (const {name, where, supertypes, mixin} of declarations) {
    const carried = inherited(name);
    if (carried.has(SENSITIVE_MARKER)) {
        sensitiveTypes++;
        const conflicts = Object.keys(NOT_WITH_SENSITIVE).filter(marker => carried.has(marker));
        if (conflicts.length > 0) {
            const effects = conflicts.map(marker => NOT_WITH_SENSITIVE[marker]).join(' nor ');
            // the supertype an inherited marker comes through: the one to change
            const origins = conflicts.map(marker => supertypes.includes(marker)
                ? marker
                : `${marker} (through ${supertypes.find(supertype => inherited(supertype).has(marker))})`);
            errors.push(`${where}: ${name} carries ${SENSITIVE_MARKER} with ${origins.join(' and ')} — a sensitive value is never ${effects}: drop ${conflicts.join(' and ')}`);
        }
    }
    if (mixin || !supertypes.some(supertype => ELEMENT_MARKERS.has(supertype))) continue;
    fieldTypes++;
    const kinds = KIND_MARKERS.filter(marker => supertypes.includes(marker));
    if (kinds.length !== 1) {
        errors.push(kinds.length === 0
            ? `${where}: ${name} extends a field marker but declares none of ${KIND_MARKERS.join(', ')} — what does it submit?`
            : `${where}: ${name} declares ${kinds.join(' and ')} — one of them only`);
    }
}

if (errors.length > 0) {
    console.error(errors.join('\n'));
    process.exit(1);
}
console.log(`ok: ${fieldTypes} field types extend fmdbmix:element or fmdbmix:formElement, each declaring exactly one of ${KIND_MARKERS.join(', ')}`);
console.log(`ok: ${sensitiveTypes} types carry ${SENSITIVE_MARKER}, none with ${Object.keys(NOT_WITH_SENSITIVE).join(' or ')}`);
