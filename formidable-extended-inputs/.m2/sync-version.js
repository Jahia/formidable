import { readFileSync, writeFileSync } from 'fs';
import { resolve, dirname } from 'path';
import { fileURLToPath } from 'url';

const __dirname = dirname(fileURLToPath(import.meta.url));

// Maven version passed as first argument (e.g. "1.2.3" or "1.2.3-SNAPSHOT")
const mavenVersion = process.argv[2];
if (!mavenVersion) {
    console.error('Usage: node sync-version.js <maven-version>');
    process.exit(1);
}

// Strip "-SNAPSHOT" suffix to produce a valid semver
const semver = mavenVersion.replace(/-SNAPSHOT$/, '');

const pkgPath = resolve(__dirname, '../package.json');
const pkg = JSON.parse(readFileSync(pkgPath, 'utf8'));
pkg.version = semver;
writeFileSync(pkgPath, JSON.stringify(pkg, null, '\t') + '\n');

console.log(`package.json version set to ${semver}`);

