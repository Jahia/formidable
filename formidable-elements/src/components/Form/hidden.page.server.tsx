import {jahiaComponent, Render} from "@jahia/javascript-modules-library";

/**
 * What the Formidable page template (templates/Form) renders: the form's title, then the
 * form. Hidden (not offered as a view choice), so it is free to serve the standalone page
 * — and its authoring in the Page Builder — without touching the fullPage or default views:
 *
 * - the default view is rendered read-only, so the Page Builder gets ONE box for the form
 *   node (this view's module) instead of one per nested render;
 * - the title lives inside that box: clicking it selects the form, whose Edit opens the
 *   title, the intro, the buttons and the responses;
 * - the wrapper carries page-specific classes (src/design/page.css) a template set never
 *   inherits inside its own pages.
 */
jahiaComponent(
	{
		componentType: "view",
		nodeType: "fmdb:form",
		name: "hidden.page"
	},
	({"jcr:title": title}: {"jcr:title"?: string}, {currentNode}) => (
		<article className="fmdb-form-page-content">
			{title && <h1 className="fmdb-form-page-title">{title}</h1>}
			<Render node={currentNode} readOnly/>
		</article>
	)
);
