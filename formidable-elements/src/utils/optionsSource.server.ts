import {server} from "@jahia/javascript-modules-library";
import type {JCRNodeWrapper} from "org.jahia.services.content";
import {parseChoices, type ParsedChoice} from "./choiceUtils";

const OPTIONS_SOURCE_SERVICE = "org.jahia.modules.formidable.engine.options.FormidableOptionsSourceService";
const SOURCED_OPTIONS_MIXIN = "fmdbmix:sourcedOptions";
const OPTIONS_SOURCE_KEY_PROPERTY = "fmdb:optionsSourceKey";

export interface FieldOptions {
	choices: ParsedChoice[];
	// True when the field is in sourced mode and its source could not deliver
	// (unknown key, initializer missing or failing). The field must render an
	// error instead of an empty option list (D10).
	sourceError: boolean;
}

/**
 * Resolves the option list of a choice field at render time.
 *
 * Manual mode reads the stored fmdb:options values. Sourced mode asks the engine's
 * options-source resolver (in-process OSGi call) for the current language; the
 * resolver answers in the manual-options JSON format, so both modes feed the same
 * parsing and rendering path.
 */
export const resolveFieldOptions = (currentNode: JCRNodeWrapper, manualOptions: string[]): FieldOptions => {
	let sourceKey = "";
	try {
		if (!currentNode.isNodeType(SOURCED_OPTIONS_MIXIN)) {
			return {choices: parseChoices(manualOptions), sourceError: false};
		}
		sourceKey = currentNode.hasProperty(OPTIONS_SOURCE_KEY_PROPERTY)
			? currentNode.getProperty(OPTIONS_SOURCE_KEY_PROPERTY).getString()
			: "";
	} catch (error) {
		console.error(`[Formidable] Could not read the options source of field ${currentNode.getPath()}`, error);
		return {choices: [], sourceError: true};
	}

	try {
		const service = server.osgi.getService(OPTIONS_SOURCE_SERVICE);
		const resolved = service.resolve(sourceKey, currentNode.getLanguage());
		const values: string[] = [];
		for (let i = 0; i < resolved.length; i++) {
			values.push(String(resolved[i]));
		}

		return {choices: parseChoices(values), sourceError: false};
	} catch (error) {
		console.error(`[Formidable] Options source '${sourceKey}' failed for field ${currentNode.getPath()}`, error);
		return {choices: [], sourceError: true};
	}
};
