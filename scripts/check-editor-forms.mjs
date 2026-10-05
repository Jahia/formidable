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
//
// Second check: a section or fieldset label key two modules both carry says the same in each. The editor keeps the
// label of the override merged last, and overrides of one priority are ordered by a tie-break, not by a choice
// (jcontent Section.mergeWith, DefinitionRegistryItemComparator): two wordings of one key would show one label or the
// other depending on the field's type. So every labelKey and descriptionKey a form override gives a section or a
// fieldset is looked up in every resource bundle of the repository (resources/*.properties, the Java modules' escaped
// \uXXXX and the JavaScript modules' raw UTF-8 alike), and the bundles carrying it hold the same text in the same
// languages.
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

// --- Shared section and fieldset labels: one wording per key and language across the bundles carrying it.
const layoutKeys = new Set();
for (const file of formFiles) {
    for (const section of JSON.parse(readFileSync(file, 'utf8')).sections ?? []) {
        for (const item of [section, ...(section.fieldSets ?? [])]) {
            for (const key of [item.labelKey, item.descriptionKey]) {
                if (key) layoutKeys.add(key);
            }
        }
    }
}
const LANGUAGE_SUFFIX = /_([a-z]{2}(?:_[A-Z]{2})?)$/;
const unescapeValue = value => value
    .replace(/\\u([0-9a-fA-F]{4})/g, (_, hex) => String.fromCharCode(Number.parseInt(hex, 16)))
    .replace(/\\(.)/g, '$1');
// key → language → bundle → text
const wordings = new Map();
for (const file of walk(root, path => path.endsWith('.properties') && basename(dirname(path)) === 'resources')) {
    const name = basename(file, '.properties');
    const language = name.match(LANGUAGE_SUFFIX)?.[1] ?? 'default';
    const bundle = relative(root, join(dirname(file), name.replace(LANGUAGE_SUFFIX, '')));
    // a line ending with a backslash goes on with the next one
    const lines = readFileSync(file, 'utf8').replace(/\\\r?\n[ \t]*/g, '').split(/\r?\n/);
    for (const line of lines) {
        const entry = line.match(/^\s*([^#!\s=:][^=:]*?)\s*[=:]\s*(.*)$/);
        if (!entry || !layoutKeys.has(entry[1])) continue;
        if (!wordings.has(entry[1])) wordings.set(entry[1], new Map());
        const byLanguage = wordings.get(entry[1]);
        if (!byLanguage.has(language)) byLanguage.set(language, new Map());
        byLanguage.get(language).set(bundle, unescapeValue(entry[2]));
    }
}
let shared = 0;
for (const [key, byLanguage] of wordings) {
    const bundles = new Set([...byLanguage.values()].flatMap(perBundle => [...perBundle.keys()]));
    if (bundles.size < 2) continue;
    shared++;
    for (const [language, perBundle] of byLanguage) {
        for (const bundle of bundles) {
            if (!perBundle.has(bundle)) {
                errors.push(`${bundle}: ${key} has no ${language} wording, which ${[...perBundle.keys()].join(', ')} has — the label shown would depend on the field's type`);
            }
        }
        if (new Set(perBundle.values()).size > 1) {
            const versions = [...perBundle].map(([bundle, text]) => `${bundle} "${text}"`).join(' vs ');
            errors.push(`${key} (${language}) is worded differently: ${versions} — the label shown would depend on the field's type`);
        }
    }
}

if (errors.length > 0) {
    console.error(errors.join('\n'));
    process.exit(1);
}
console.log(`ok: ${overrides} form overrides declare the ${LAYOUT_SECTION} section, each placing every property of its node type once; `
    + `${shared} section or fieldset label key(s) carried by several bundles, worded alike in each language`);
