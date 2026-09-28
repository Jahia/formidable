import {server} from "@jahia/javascript-modules-library";

/**
 * The extensions of a file field's accept tokens, from the engine's FileTypeService — Apache Tika's registry of
 * MIME types — so that no table of this module maps a type to its extensions.
 */
export interface AcceptExtensions {
	/** Per token, the extensions a visitor is shown: a type's preferred one, a wildcard's allowed types', an extension itself. */
	shown: Record<string, string[]>;
	/** Per token, every extension a file of it may carry: what recognises a file when the browser gives no type. */
	recognised: Record<string, string[]>;
}

const FILE_TYPE_SERVICE = "org.jahia.modules.formidable.engine.files.FileTypeService";

// The engine's service, reached over OSGi; its arrays come back as Java arrays.
interface FileTypeServiceLike {
	shownExtensions(token: string): ArrayLike<string>;
	recognisedExtensions(token: string): ArrayLike<string>;
}

const asStrings = (values: ArrayLike<string> | null | undefined): string[] =>
	Array.from(values ?? [], value => String(value));

/**
 * The extensions of each token. Without the engine's service — not deployed, or failing — every token maps to
 * nothing, and the field stays as permissive as the browser: the server checks every file's real type anyway.
 */
export const acceptExtensionsOf = (accept: string[] | undefined): AcceptExtensions => {
	const tokens = (accept ?? []).map(token => token.trim()).filter(Boolean);
	const result: AcceptExtensions = {shown: {}, recognised: {}};
	if (tokens.length === 0) {
		return result;
	}
	try {
		const service = server.osgi.getService(FILE_TYPE_SERVICE) as FileTypeServiceLike | null;
		if (!service) {
			return result;
		}
		for (const token of tokens) {
			result.shown[token] = asStrings(service.shownExtensions(token));
			result.recognised[token] = asStrings(service.recognisedExtensions(token));
		}
	} catch (error) {
		console.error("[Formidable] Could not read the file types of an accept list", error);
	}
	return result;
};
