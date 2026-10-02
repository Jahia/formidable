// Every setting of a reorganised editor is placed, once. Usage: node check-editor-forms.mjs [repoRoot]
//
// A Content Editor form override (jahia-content-editor-forms/forms/*.json) that declares the Field settings
// section takes over the layout of its node type: the editor moves a property wherever the override lists it,
// and a property the override forgets stays where the editor generated it — in Content, or in the hidden
// storage fieldset of a mixin — in silence. So an override declaring `fieldSettings` lists every property its
// node type declares in the CND, exactly once, the hidden ones included (listed in the hidden fieldset, which
// is how "hidden on purpose" is told from "forgotten"), and nothing the CND does not declare
// (docs/architecture/content-editor-layout.md).
//
// Every *.json under a jahia-content-editor-forms/forms directory and every *.cnd of the repository are read
// (node_modules, target and dist excluded). A property marked `hidden` in the CND is not an editor field and is
// not expected; a child node definition (`+`) is not a field. A property redefined by the type's supertypes is
// not the type's own and is not expected either: jcr:title and ce:systemName stay where the editor puts them.
import {readdirSync, readFileSync, statSync} from 'node:fs';
import {basename, dirname, join, relative, resolve} from 'node:path';

const root = resolve(process.argv[2] ?? '.');
const SKIPPED_DIRS = new Set(['node_modules', 'target', 'dist', '.git']);
const LAYOUT_SECTION = 'fieldSettings';

const walk = (dir, keep) => readdirSync(dir).flatMap(name => {
    if (SKIPPED_DIRS.has(name)) return [];
    const path = join(dir, name);
    if (statSync(path).isDirectory()) return walk(path, keep);
    return keep(path) ? [path] : [];
});

// Own properties per node type, read from the CND declarations: `- name (type…) attributes…`.
const propertiesByType = new Map();
for (const file of walk(root, path => path.endsWith('.cnd'))) {
    let current = null;
    for (const raw of readFileSync(file, 'utf8').split('\n')) {
        const line = raw.replace(/\/\/.*$/, '');
        const header = line.match(/^\[([\w]+:[\w]+)]/);
        if (header) {
            current = header[1];
            if (!propertiesByType.has(current)) propertiesByType.set(current, new Set());
            continue;
        }
        const property = current && line.match(/^\s*-\s*([\w:]+)\s*\((.*)$/);
        if (!property) continue;
        const attributes = property[2].slice(property[2].indexOf(')') + 1).replace(/'[^']*'/g, '');
        if (/\bhidden\b/.test(attributes)) continue;
        propertiesByType.get(current).add(property[1]);
    }
}

const errors = [];
let overrides = 0;
const formFiles = walk(root, path => path.endsWith('.json') && basename(dirname(path)) === 'forms'
    && basename(dirname(dirname(path))) === 'jahia-content-editor-forms');
for (const file of formFiles) {
    const where = relative(root, file);
    const form = JSON.parse(readFileSync(file, 'utf8'));
    const sections = form.sections ?? [];
    if (!sections.some(section => section.name === LAYOUT_SECTION)) continue;
    overrides++;
    const expected = propertiesByType.get(form.nodeType);
    if (!expected) {
        errors.push(`${where}: ${form.nodeType} is declared by no CND of the repository`);
        continue;
    }
    const listed = new Map();
    for (const section of sections) {
        for (const fieldSet of section.fieldSets ?? []) {
            for (const field of fieldSet.fields ?? []) {
                listed.set(field.name, (listed.get(field.name) ?? 0) + 1);
            }
        }
    }
    for (const name of expected) {
        if (!listed.has(name)) errors.push(`${where}: ${form.nodeType} declares ${name} and the override places it nowhere — list it in a fieldset, the hidden one if it must not show`);
    }
    for (const [name, count] of listed) {
        if (!expected.has(name)) errors.push(`${where}: ${name} is not a property ${form.nodeType} declares — a typo, or a field of another type that its own override must place`);
        else if (count > 1) errors.push(`${where}: ${name} is placed ${count} times`);
    }
}

if (errors.length > 0) {
    console.error(errors.join('\n'));
    process.exit(1);
}
console.log(`ok: ${overrides} form overrides declare the ${LAYOUT_SECTION} section, each placing every property of its node type once`);
