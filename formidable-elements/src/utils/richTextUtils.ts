import type { RenderContext } from "org.jahia.services.render";

// Jahia stores internal links in rich text values with URL placeholders
// (/cms/{mode}/{lang}/..., /files/{workspace}/...). The render chain only
// rewrites them inside the HTML output attributes (href/src traverser), so a
// value serialized into island props escapes that rewriting and would reach
// the client unresolved. Resolve the placeholders explicitly before passing a
// rich text value to an Island.
export const resolveUrlPlaceholders = (
	html: string | undefined,
	renderContext: RenderContext,
): string | undefined => {
	if (!html) {
		return html;
	}

	const urlGenerator = renderContext.getURLGenerator();
	return html
		.replaceAll(urlGenerator.getBasePlaceholders(), urlGenerator.getBase())
		.replaceAll(urlGenerator.getFilesPlaceholders(), urlGenerator.getFiles());
};
