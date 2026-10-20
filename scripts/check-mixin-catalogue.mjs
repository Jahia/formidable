// Every mixin the modules declare has its row in the catalogue, and no row names a mixin that is gone.
// Usage: node check-mixin-catalogue.mjs [repoRoot]
//
// docs/architecture/mixin-catalogue.md lists, one table row each, every fmdbmix: and fmdbsamplemix: mixin
// of the repository: what it says, who reads it, which types carry it. Nothing but this script keeps the
// page and the CND files in step: a mixin declared without its row, or a row kept after its declaration
// went, is a page that lies in silence. The names are compared both ways.
//
// Every *.cnd of the repository is read (node_modules, target and dist excluded). A declaration is read on
// its own line, as every CND here writes it, and counts when its header, or the lines that continue it
// before the first property or child line, carries the word mixin. A row of the page counts for every
// mixin of the two namespaces its first cell names in backticks — the date bounds share a row by pair.
import {readdirSync, readFileSync, statSync} from 'node:fs';
import {join, relative, resolve} from 'node:path';

const root = resolve(process.argv[2] ?? '.');
const CATALOGUE = 'docs/architecture/mixin-catalogue.md';
const NAMESPACES = ['fmdbmix', 'fmdbsamplemix'];
const SKIPPED_DIRS = new Set(['node_modules', 'target', 'dist', '.git']);

const cndFiles = dir => readdirSync(dir).flatMap(name => {
    if (SKIPPED_DIRS.has(name)) return [];
    const path = join(dir, name);
    if (statSync(path).isDirectory()) return cndFiles(path);
    return name.endsWith('.cnd') ? [path] : [];
});

const declared = new Map();
for (const file of cndFiles(root)) {
    const lines = readFileSync(file, 'utf8').split('\n').map(raw => raw.replace(/\/\/.*$/, ''));
    lines.forEach((line, index) => {
        const header = line.match(/^\[((?:fmdbmix|fmdbsamplemix):[\w]+)]/);
        if (!header) return;
        // the declaration continues until its first property, child or next declaration line
        let declaration = line;
        for (let next = index + 1; next < lines.length && !/^\s*[-+[]/.test(lines[next]); next++) {
            declaration += ` ${lines[next]}`;
        }
        if (/\bmixin\b/.test(declaration)) {
            declared.set(header[1], `${relative(root, file)}:${index + 1}`);
        }
    });
}

const listed = new Map();
readFileSync(join(root, CATALOGUE), 'utf8').split('\n').forEach((line, index) => {
    const firstCell = line.match(/^\|([^|]*)\|/);
    if (!firstCell) return;
    for (const name of firstCell[1].matchAll(/`((?:fmdbmix|fmdbsamplemix):[\w]+)`/g)) {
        listed.set(name[1], `${CATALOGUE}:${index + 1}`);
    }
});

const errors = [];
for (const [name, where] of declared) {
    if (!listed.has(name)) errors.push(`${where}: ${name} is declared but has no row in ${CATALOGUE}`);
}
for (const [name, where] of listed) {
    if (!declared.has(name)) errors.push(`${where}: ${name} has a row but no CND of the repository declares it as a mixin`);
}

if (errors.length > 0) {
    console.error(errors.join('\n'));
    process.exit(1);
}
console.log(`ok: ${declared.size} ${NAMESPACES.join(': and ')}: mixins declared, each with its row in ${CATALOGUE}, and no row without its mixin`);
