import { readFileSync, writeFileSync } from 'fs';
import { resolve } from 'path';

// Maven version passed as first argument (e.g. "1.2.3" or "1.2.3-SNAPSHOT")
const mavenVersion = process.argv[2];
if (!mavenVersion) {
    console.error('Usage: node sync-version.js <maven-version>');
    process.exit(1);
}

// Strip "-SNAPSHOT" suffix to produce a valid semver
const semver = mavenVersion.replace(/-SNAPSHOT$/, '');

// The exec-maven-plugin runs with the calling module as working directory, so
// resolving from the CWD (not from this script's own location) lets a module
// invoke any copy of this script — the tsx test modules share the root's.
const pkgPath = resolve(process.cwd(), 'package.json');
const pkg = JSON.parse(readFileSync(pkgPath, 'utf8'));
pkg.version = semver;
writeFileSync(pkgPath, JSON.stringify(pkg, null, '\t') + '\n');

console.log(`package.json version set to ${semver}`);
