/**
 * The stylesheet of the site's template set, for the inspection previews (the cm views): there the form renders
 * outside any page, so nothing serves the stylesheet the site's Layout adds, and the contributor saw the modules'
 * look instead of the site's. Vite bundles every stylesheet a JavaScript module imports into dist/assets/style.css,
 * the file a template set's Layout serves through AddResources (docs/styling/class-hooks.md, Surfaces): when the
 * site's template set is such a module and ships that file, the previews add it; a template set without it, a
 * JSP one say, adds nothing and the previews keep the modules' look.
 */

/** Where a JavaScript module's bundled stylesheet lives, the output of the Jahia Vite plugin. */
export const TEMPLATE_SET_STYLESHEET = "dist/assets/style.css";

/** The site's template set as the host hands it: a JahiaTemplatesPackage, its module id and its OSGi bundle. */
export interface TemplateSetLike {
	getId(): string;
	getBundle(): {getEntry(path: string): unknown} | null;
}

/** The site node as the host hands it: a JCRSiteNode, with its template set. */
export interface SiteLike {
	getTemplatePackage(): TemplateSetLike | null;
}

/**
 * The URL of the template set's bundled stylesheet, or undefined when the site's template set ships none,
 * when there is no site, or when the host refuses the lookup: the preview then adds nothing.
 */
export const templateSetStylesheetUrl = (site: SiteLike | null | undefined, contextPath: string): string | undefined => {
	try {
		const templateSet = site?.getTemplatePackage();
		if (!templateSet?.getBundle()?.getEntry(TEMPLATE_SET_STYLESHEET)) {
			return undefined;
		}

		return `${contextPath}/modules/${templateSet.getId()}/${TEMPLATE_SET_STYLESHEET}`;
	} catch {
		return undefined;
	}
};
