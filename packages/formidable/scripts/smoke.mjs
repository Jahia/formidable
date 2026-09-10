// Proves the tarball `yarn npm publish` would upload: packs it, unpacks the archive under a
// scratch node_modules and imports the package by name, the way a module outside the monorepo
// does — through the exports map publishConfig rewrites to dist/, under Node's ESM resolver,
// which needs the `.js` extensions tsc never adds. (`yarn pack --dry-run` only lists files and
// proves none of this.) Run after `yarn build`.
import {execFileSync} from "node:child_process";
import {existsSync, mkdirSync, rmSync, writeFileSync} from "node:fs";
import {dirname, join} from "node:path";
import {fileURLToPath} from "node:url";

const packageDir = dirname(dirname(fileURLToPath(import.meta.url)));
const scratch = join(packageDir, ".smoke");
const installed = join(scratch, "node_modules", "@jahia", "formidable");

const fail = (message) => {
	console.error(`smoke: ${message}`);
	process.exit(1);
};

rmSync(scratch, {recursive: true, force: true});
mkdirSync(installed, {recursive: true});

const tarball = join(scratch, "package.tgz");
execFileSync("yarn", ["pack", "--out", tarball], {cwd: packageDir, stdio: "inherit"});
execFileSync("tar", ["-xzf", tarball, "-C", installed, "--strip-components=1"]);

for (const file of ["package.json", "dist/index.js", "dist/index.d.ts", "README.md", "LICENSE"]) {
	if (!existsSync(join(installed, file))) fail(`${file} is missing from the tarball`);
}

// The scratch directory is a package of its own: without this manifest the probe would sit in the
// scope of packages/formidable/package.json, and Node's self-reference rule would resolve
// `@jahia/formidable` to the workspace sources instead of the unpacked tarball. `react` still
// resolves by walking up to the workspace's node_modules.
writeFileSync(join(scratch, "package.json"), JSON.stringify({name: "smoke", private: true, type: "module"}));
const probe = join(scratch, "probe.mjs");
writeFileSync(probe, `
import {HelpText, helpTextId, validationDataAttributes, maskToPattern, applyMask, useMask} from "@jahia/formidable";

const checks = {
	helpTextId: helpTextId("n1") === "help-n1",
	HelpText: typeof HelpText === "function",
	validationDataAttributes: validationDataAttributes({msgValueMissing: "x"})["data-fmdb-msg-value-missing"] === "x",
	maskToPattern: maskToPattern("AA-9999") === "^[A-Za-z][A-Za-z]-[0-9][0-9][0-9][0-9]$",
	applyMask: applyMask("ab1234", "AA-9999") === "AB-1234",
	useMask: typeof useMask === "function"
};
const failed = Object.entries(checks).filter(([, ok]) => !ok).map(([name]) => name);
if (failed.length > 0) {
	console.error("smoke: " + failed.join(", ") + " failed");
	process.exit(1);
}
console.log("smoke: @jahia/formidable imports from its packed tarball under Node ESM, " + Object.keys(checks).length + " checks pass");
`);
execFileSync(process.execPath, [probe], {stdio: "inherit"});

rmSync(scratch, {recursive: true, force: true});
