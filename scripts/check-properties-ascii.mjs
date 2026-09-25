// Every resource bundle of a Java module holds ASCII only. Usage: node check-properties-ascii.mjs [repoRoot]
//
// Java reads a `*.properties` bundle as ISO-8859-1 (PropertyResourceBundle): a non-ASCII byte written raw — an
// accent, a dash — shows on screen as mojibake (`â€”`, `Ã‰`). A character outside ASCII is written as `\uXXXX`.
// The bundles of the JavaScript modules (`settings/resources/`, read as UTF-8 by their loader) are out of scope:
// there the accents are written raw, on purpose. See CLAUDE.md, "Fichiers .properties".
import {readdirSync, readFileSync, statSync} from 'node:fs';
import {join, relative, resolve} from 'node:path';

const root = resolve(process.argv[2] ?? '.');
const SKIPPED_DIRS = new Set(['node_modules', 'target', 'dist', '.git']);

const bundles = dir => readdirSync(dir).flatMap(name => {
    if (SKIPPED_DIRS.has(name)) return [];
    const path = join(dir, name);
    if (statSync(path).isDirectory()) return bundles(path);
    return name.endsWith('.properties') && path.includes(`${join('src', 'main', 'resources', 'resources')}`) ? [path] : [];
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
