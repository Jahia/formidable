// The engine's CND and the constants it exports say the same thing, and nothing else in Java
// spells one of those names out. Usage: node check-nodetype-names.mjs [repoRoot]
//
// Two checks, both from one reading of formidable-engine/src/main/resources/META-INF/definitions.cnd:
//
//  1. Parity. Every node type and mixin the engine declares has a constant in FormidableNodeTypes
//     or FormidableMixins, and every constant there is declared in that file. A mixin is how
//     another module opts into engine behaviour, so all of them are contract — which is why this
//     direction is enforced too, and why a new one cannot be added without exporting its name.
//     Properties are checked one way only: a property is local to the type that declares it until
//     something outside reads it, so FormidableProperties holds the ones that crossed.
//  2. No second spelling. An engine-declared name appears in the MAIN sources only in those classes —
//     anywhere else the constant exists and the literal is a copy that no rename would follow. Test
//     sources are out of scope on purpose: a test that writes the name out is how a constant holding
//     the WRONG declared name gets caught, which parity alone cannot do. A migration
//     is exempt: it is a frozen script that must keep naming the vocabulary of the release it
//     heals, not follow the live one. A name another module declares (fmdb:form and the concrete
//     field types, from formidable-elements) has no constant to use, and is counted, not refused.
//
// See docs/architecture/cnd-module-ownership.md, "Naming these types from Java".
import {readFileSync, readdirSync, statSync} from 'node:fs';
import {join, relative, resolve} from 'node:path';

const root = resolve(process.argv[2] ?? '.');
const CND = 'formidable-engine/src/main/resources/META-INF/definitions.cnd';
const API = 'formidable-engine/src/main/java/org/jahia/modules/formidable/engine/api';
// Stamped by a 0.4 content migration to record that it has already healed a node. The declarations
// outlive the migrations (docs/administration/upgrade-notes.md) so that marked content stays valid,
// but the names are the engine talking to itself: engine-internal, in migration/MigrationMarkers.java.
const TRANSITIONAL = new Set(['fmdbmix:elementsReactivated', 'fmdbmix:migratedChoiceOptions']);

const cnd = readFileSync(join(root, CND), 'utf8');
const declared = {type: new Set(), mixin: new Set(), item: new Set()};
for (const line of cnd.split('\n')) {
    const type = line.match(/^\[([\w]+:[\w]+)]/);
    if (type) declared[/\bmixin\b/.test(line) ? 'mixin' : 'type'].add(type[1]);
    const item = line.match(/^\s*[-+]\s+([\w:]+)\s*\(/);
    if (item) declared.item.add(item[1]);
}

// A commented-out declaration declares nothing: read the code, as the compiler does.
const uncommented = text => text.replace(/\/\*[\s\S]*?\*\//g, '').replace(/\/\/.*$/gm, '');
const constantsOf = file => {
    const out = new Map();
    for (const m of uncommented(readFileSync(join(root, API, file), 'utf8')).matchAll(/public static final String (\w+) = "([^"]+)";/g)) {
        out.set(m[1], m[2]);
    }
    return out;
};
const exported = {
    type: constantsOf('FormidableNodeTypes.java'),
    mixin: constantsOf('FormidableMixins.java'),
    item: constantsOf('FormidableProperties.java'),
};

const errors = [];
for (const kind of ['type', 'mixin']) {
    for (const name of declared[kind]) {
        if (!TRANSITIONAL.has(name) && ![...exported[kind].values()].includes(name)) {
            errors.push(`${CND}: ${name} has no constant in Formidable${kind === 'type' ? 'NodeTypes' : 'Mixins'}`);
        }
    }
}
for (const [kind, klass] of [['type', 'FormidableNodeTypes'], ['mixin', 'FormidableMixins'], ['item', 'FormidableProperties']]) {
    for (const [constant, value] of exported[kind]) {
        if (!declared[kind].has(value)) errors.push(`${klass}.${constant}: ${value} is not declared in ${CND}`);
    }
}

const engineOwned = new Set([...declared.type, ...declared.mixin]);
const SKIP = new Set(['node_modules', 'target', 'dist', '.git', '.yarn', 'results']);
const walk = dir => readdirSync(dir).flatMap(name => {
    if (SKIP.has(name)) return [];
    const path = join(dir, name);
    return statSync(path).isDirectory() ? walk(path) : [path];
});
const sources = walk(root).filter(f => f.endsWith('.java') && f.includes('/src/main/java/'));
const allowed = f => f.startsWith(join(root, API)) || f.includes('/migration/');
let elsewhere = 0;
for (const file of sources) {
    // A name quoted in a comment documents; only code can spell a second one.
    const code = uncommented(readFileSync(file, 'utf8'));
    for (const m of code.matchAll(/"(fmdb\w*:\w+)"/g)) {
        if (!engineOwned.has(m[1])) elsewhere++;
        else if (!allowed(file)) errors.push(`${relative(root, file)}: ${m[1]} has a constant, use it`);
    }
}

if (errors.length > 0) {
    console.error(errors.join('\n'));
    process.exit(1);
}
console.log(`ok: ${declared.type.size} node types and ${declared.mixin.size} mixins declared and exported,`
    + ` ${sources.length} Java sources hold no second spelling (${elsewhere} literals name another module's types)`);
