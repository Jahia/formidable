// Every resource bundle of a Java module holds ASCII only. Usage: node check-properties-ascii.mjs [repoRoot]
//
// Java reads a `*.properties` file as ISO-8859-1 — PropertyResourceBundle and Properties.load alike — so a
// non-ASCII byte written raw (an accent, a dash) shows on screen as mojibake (`â€”`, `Ã‰`); a character outside
// ASCII is written as `\uXXXX`. In scope: every `*.properties` under the `src/main/resources/` of a Java module,
// in Jahia's `resources/` folder or at a package path alike. Out of scope: the bundles of the JavaScript modules
// (`settings/resources/`), read as UTF-8 by their loader — there the accents are written raw, on purpose.
import {readdirSync, readFileSync, statSync} from 'node:fs';
import {join, relative, resolve, sep} from 'node:path';

const root = resolve(process.argv[2] ?? '.');
const SKIPPED_DIRS = new Set(['node_modules', 'target', 'dist', '.git']);
// Bounded by separators on both sides: a sibling `resourcesX/` is not a Java resources folder.
const JAVA_RESOURCES = `${sep}src${sep}main${sep}resources${sep}`;

const bundles = dir => readdirSync(dir).flatMap(name => {
    if (SKIPPED_DIRS.has(name)) return [];
    const path = join(dir, name);
    if (statSync(path).isDirectory()) return bundles(path);
    return name.endsWith('.properties') && path.includes(JAVA_RESOURCES) ? [path] : [];
});

const errors = [];
let checked = 0;
for (const file of bundles(root)) {
    checked++;
    const bytes = readFileSync(file);
    const lines = bytes.toString('latin1').split('\n');
    lines.forEach((line, index) => {
        const stray = [...line].filter(character => character.charCodeAt(0) > 127);
        if (stray.length > 0) {
            errors.push(`${relative(root, file)}:${index + 1}: ${stray.length} non-ASCII byte(s) — write the character as \\uXXXX`);
        }
    });
}

if (errors.length > 0) {
    console.error(errors.join('\n'));
    process.exit(1);
}
console.log(`ok: ${checked} Java resource bundles hold ASCII only`);
