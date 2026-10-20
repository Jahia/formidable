// Copy of formidable-elements/src/utils/optionsSource.server.ts: the library does not publish the options
// helpers, and this module reaches formidable-elements through the library only. The rules (the manual
// options aligned on the default language, the cache dependencies of a sourced field) are documented in
// docs/architecture/choice-field-options-sources.md; a change there lands in both copies.
import { server } from "@jahia/javascript-modules-library";
import type { JCRNodeWrapper } from "org.jahia.services.content";
import type { RenderContext } from "org.jahia.services.render";
import { parseChoices, type ParsedChoice } from "./choiceUtils";

const OPTIONS_SOURCE_SERVICE = "org.jahia.modules.formidable.engine.options.FormidableOptionsSourceService";
const MANUAL_OPTIONS_DISPLAY_SERVICE = "org.jahia.modules.formidable.engine.options.ManualOptionsDisplayService";
// Mixins whose options are resolved by the engine at render time.
const RESOLVED_OPTIONS_MIXINS = ["fmdbmix:sourcedOptions", "fmdbmix:categoryOptions", "fmdbmix:contentOptions"];

export interface FieldOptions {
  choices: ParsedChoice[];
  // True when the field is in sourced mode and its source could not deliver: the field renders an error
  // instead of an empty option list.
  sourceError: boolean;
}

/** A Java String[] crossing the GraalVM bridge, as a JS array. */
const toStringArray = (javaArray: { length: number }): string[] => {
  const values: string[] = [];
  for (let i = 0; i < javaArray.length; i++) {
    values.push(String((javaArray as unknown as string[])[i]));
  }

  return values;
};

/** The manual options to render: the default language's values, order and selections, this language's labels. */
const alignManualOptions = (currentNode: JCRNodeWrapper, manualOptions: string[], renderContext: RenderContext): string[] => {
  try {
    const site = currentNode.getResolveSite();
    if (site) {
      server.render.addCacheDependency({ node: site }, renderContext);
    }

    const service = server.osgi.getService(MANUAL_OPTIONS_DISPLAY_SERVICE);
    const aligned = service.forDisplay(currentNode, currentNode.getLanguage());

    return aligned === null || aligned === undefined ? manualOptions : toStringArray(aligned);
  } catch (error) {
    console.error(`[Formidable] Could not align the manual options of field ${currentNode.getPath()}`, error);
    return manualOptions;
  }
};

// The root a category- or content-mode field reads its options under, by mixin.
const SOURCE_ROOT_PROPERTIES = ["optionsRootCategory", "optionsRootNode"];

const escapeRegExp = (value: string): string => value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");

/** A category or content published, renamed or removed under the picked root flushes the field. */
const declareSourceRootDependency = (currentNode: JCRNodeWrapper, renderContext: RenderContext): void => {
  for (const property of SOURCE_ROOT_PROPERTIES) {
    try {
      if (currentNode.hasProperty(property)) {
        const rootPath = currentNode.getProperty(property).getNode().getPath();
        server.render.addCacheDependency({ flushOnPathMatchingRegexp: `${escapeRegExp(rootPath)}(/.*)?` }, renderContext);
      }
    } catch {
      // Resolution below reports the unreadable root.
    }
  }
};

/** Resolves the option list of a choice field at render time, in manual and in sourced modes alike. */
export const resolveFieldOptions = (currentNode: JCRNodeWrapper, manualOptions: string[], renderContext: RenderContext): FieldOptions => {
  try {
    if (!RESOLVED_OPTIONS_MIXINS.some((mixin) => currentNode.isNodeType(mixin))) {
      return { choices: parseChoices(alignManualOptions(currentNode, manualOptions, renderContext)), sourceError: false };
    }
  } catch (error) {
    console.error(`[Formidable] Could not read the options mode of field ${currentNode.getPath()}`, error);
    return { choices: [], sourceError: true };
  }

  declareSourceRootDependency(currentNode, renderContext);

  try {
    const service = server.osgi.getService(OPTIONS_SOURCE_SERVICE);
    const resolved = service.resolveForField(currentNode, currentNode.getLanguage());
    if (resolved === null || resolved === undefined) {
      return { choices: parseChoices(manualOptions), sourceError: false };
    }

    return { choices: parseChoices(toStringArray(resolved)), sourceError: false };
  } catch (error) {
    console.error(`[Formidable] Options source failed for field ${currentNode.getPath()}`, error);
    return { choices: [], sourceError: true };
  }
};
