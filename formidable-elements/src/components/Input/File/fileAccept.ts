import type {FileTypes} from '~/utils/fileTypes.server';

/**
 * What a file field accepts, read by its island: the types the engine resolved for the field (its own restricted to
 * the administrator's list, or the whole list), each with the extensions Apache Tika gives it. No table of types here.
 */

/** The administrator's "any file": among a field's types, it lifts every restriction. */
const ANY_FILE = '*/*';

/**
 * Whether the field restricts the files at all: not without the engine's answer — the server checks anyway — nor when
 * its types hold "any file".
 */
export const isRestricted = (fileTypes: FileTypes): boolean =>
	fileTypes.tokens !== undefined && !fileTypes.tokens.includes(ANY_FILE);

/** The types a restricting field checks: none when it restricts nothing. */
const restrictingTokens = (fileTypes: FileTypes): string[] => (isRestricted(fileTypes) ? fileTypes.tokens ?? [] : []);

/** What the visitor is told the field accepts: each type's shown extensions, or the type itself when Tika knows none. */
export const getDisplayFormats = (fileTypes: FileTypes): string[] =>
	Array.from(new Set(restrictingTokens(fileTypes).flatMap(token => {
		const shown = fileTypes.shown[token] ?? [];
		return shown.length > 0 ? shown : [token];
	})));

/**
 * Whether a file answers a type: by the type the browser gives, else by its name's extension against the type's
 * recognised extensions; a type Tika knows no extension of lets the file through — the server checks the real type
 * of every file anyway. Not a wildcard the browser's type contradicts: under "any file" a wildcard lists no
 * extension, and a PDF dropped on an image field must still be refused here, not at submission.
 */
export const matchesAcceptToken = (file: Pick<File, 'name' | 'type'>, token: string, fileTypes: FileTypes): boolean => {
	const loweredToken = token.toLowerCase();
	const loweredName = file.name.toLowerCase();
	const loweredType = file.type.toLowerCase();

	if (loweredType && (loweredToken.endsWith("/*") ? loweredType.startsWith(loweredToken.slice(0, -1)) : loweredType === loweredToken)) {
		return true;
	}
	if (loweredType && loweredToken.endsWith("/*")) {
		return false;
	}
	const recognised = fileTypes.recognised[token] ?? [];
	return recognised.length === 0 || recognised.some(extension => loweredName.endsWith(extension));
};

/** Whether the field takes a file: any file when unrestricted, none when no type is left, else one of its types. */
export const accepts = (file: Pick<File, 'name' | 'type'>, fileTypes: FileTypes): boolean =>
	!isRestricted(fileTypes) || restrictingTokens(fileTypes).some(token => matchesAcceptToken(file, token, fileTypes));

/**
 * The input's accept attribute: every type and every extension a file of it is recognised by, so that the picker
 * offers what the island's own check lets through (.mov for video/quicktime, not only Tika's preferred .qt); empty —
 * any file — when the field restricts nothing.
 */
export const buildAcceptAttr = (fileTypes: FileTypes): string => {
	const entries = new Set<string>();
	for (const token of restrictingTokens(fileTypes)) {
		entries.add(token);
		for (const extension of fileTypes.recognised[token] ?? []) {
			entries.add(extension);
		}
	}
	return Array.from(entries).join(",");
};
