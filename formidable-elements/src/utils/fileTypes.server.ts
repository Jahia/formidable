import {server} from "@jahia/javascript-modules-library";

/**
 * What a file field accepts, from the engine's FileTypeService — the administrator's allowed types and Apache Tika's
 * registry of MIME types — so that the field offers the visitor exactly what the server lets through, and no table
 * of this module maps a type to its extensions.
 */
export interface FileTypes {
	/**
	 * The MIME types and wildcards the field accepts: its own restricted to the allowed ones, or all of them when it
	 * declares none. Empty: no file is accepted. Undefined: the engine could not tell, the field restricts nothing.
	 */
	tokens?: string[];
	/** Per type, the extensions a visitor is shown: a type's own one, a wildcard's allowed types'. */
	shown: Record<string, string[]>;
	/** Per type, every extension a file of it may carry: what recognises a file when the browser gives no type. */
	recognised: Record<string, string[]>;
}

const FILE_TYPE_SERVICE = "org.jahia.modules.formidable.engine.files.FileTypeService";

// The engine's service, reached over OSGi; its arrays come back as Java arrays. The field goes as its node, which
// the service reads its accept values from: no JavaScript array crosses into Java.
interface FileTypeServiceLike {
	allowedFor(field: unknown): ArrayLike<string>;
	shownExtensions(token: string): ArrayLike<string>;
	recognisedExtensions(token: string): ArrayLike<string>;
}

const asStrings = (values: ArrayLike<string> | null | undefined): string[] =>
	Array.from(values ?? [], String);

/**
 * The types a file field accepts and their extensions. Without the engine's service — not deployed, or failing — the
 * field restricts nothing and stays as permissive as the browser: the server checks every file's real type anyway.
 *
 * @param field the file field's node — the engine reads its accept values and names it in the warning for a type no
 *              longer allowed
 */
export const fileTypesOf = (field: unknown): FileTypes => {
	const result: FileTypes = {shown: {}, recognised: {}};
	try {
		const service = server.osgi.getService(FILE_TYPE_SERVICE) as FileTypeServiceLike | null;
		if (!service) {
			return result;
		}
		const tokens = asStrings(service.allowedFor(field));
		for (const token of tokens) {
			result.shown[token] = asStrings(service.shownExtensions(token));
			result.recognised[token] = asStrings(service.recognisedExtensions(token));
		}
		result.tokens = tokens;
	} catch (error) {
		console.error("[Formidable] Could not read the file types of a file field", error);
		return {shown: {}, recognised: {}};
	}
	return result;
};
