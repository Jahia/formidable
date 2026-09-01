import type {RenderContext} from 'org.jahia.services.render';

// Jahia stores internal links in rich text values with URL placeholders
// (/cms/{mode}/{lang}/..., /files/{workspace}/...). The render chain only
// rewrites them inside the HTML output attributes (href/src traverser), so a
// value serialized into island props escapes that rewriting and would reach
// the client unresolved. Resolve the placeholders explicitly before passing a
// rich text value to an Island.
export const resolveUrlPlaceholders = (html: string | undefined, renderContext: RenderContext): string | undefined => {
	if (!html) {
		return html;
	}

	const urlGenerator = renderContext.getURLGenerator();
	return html
		.replaceAll(urlGenerator.getBasePlaceholders(), urlGenerator.getBase())
		.replaceAll(urlGenerator.getFilesPlaceholders(), urlGenerator.getFiles());
};

// Contributor CSS for a <style> tag, as raw text. A JSX text child is HTML-escaped
// by the SSR renderer while <style> is a RAWTEXT element the browser never decodes
// entities in — so any rule using '>', '"' or "'" (the documented
// [data-fmdb-node-name="…"] selectors included) was silently dropped. Rendered
// through dangerouslySetInnerHTML instead, same trust model as the intro and help
// texts on this surface. The one sequence that must not pass through verbatim is a
// </style break-out; '\/' is a valid CSS escape for '/', so such a rule survives.
export const styleTagCss = (css: string): string => css.replace(/<\//g, '<\\/');
