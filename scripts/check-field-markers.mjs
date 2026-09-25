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
// Every *.cnd of the repository is read (node_modules, target and dist excluded); a mixin declaration is
// not a field type and is skipped. A declaration is read on its own line, as every CND here writes it.
import {readdirSync, readFileSync, statSync} from 'node:fs';
import {join, relative, resolve} from 'node:path';

const root = resolve(process.argv[2] ?? '.');
const ELEMENT_MARKERS = new Set(['fmdbmix:element', 'fmdbmix:formElement']);
const KIND_MARKERS = ['fmdbmix:submittableField', 'fmdbmix:nonSubmittable', 'fmdbmix:fileField'];
const SKIPPED_DIRS = new Set(['node_modules', 'target', 'dist', '.git']);

const cndFiles = dir => readdirSync(dir).flatMap(name => {
    if (SKIPPED_DIRS.has(name)) return [];
    const path = join(dir, name);
    if (statSync(path).isDirectory()) return cndFiles(path);
    return name.endsWith('.cnd') ? [path] : [];
});

const errors = [];
let fieldTypes = 0;
for (const file of cndFiles(root)) {
    const lines = readFileSync(file, 'utf8').split('\n');
    lines.forEach((raw, index) => {
        const line = raw.replace(/\/\/.*$/, '');
        const declaration = line.match(/^\[([\w]+:[\w]+)]\s*>\s*(.+?)\s*(\bmixin\b.*)?$/);
        if (!declaration || declaration[3]) return;
        const supertypes = declaration[2].split(',').map(name => name.trim()).filter(Boolean);
        if (!supertypes.some(name => ELEMENT_MARKERS.has(name))) return;
        fieldTypes++;
        const kinds = KIND_MARKERS.filter(marker => supertypes.includes(marker));
        if (kinds.length !== 1) {
            const where = `${relative(root, file)}:${index + 1}`;
            errors.push(kinds.length === 0
                ? `${where}: ${declaration[1]} extends a field marker but declares none of ${KIND_MARKERS.join(', ')} — what does it submit?`
                : `${where}: ${declaration[1]} declares ${kinds.join(' and ')} — one of them only`);
        }
    });
}

if (errors.length > 0) {
    console.error(errors.join('\n'));
    process.exit(1);
}
console.log(`ok: ${fieldTypes} field types extend fmdbmix:element or fmdbmix:formElement, each declaring exactly one of ${KIND_MARKERS.join(', ')}`);
