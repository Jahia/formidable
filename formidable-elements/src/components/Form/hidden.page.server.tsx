import {jahiaComponent, Render} from "@jahia/javascript-modules-library";

/**
 * What the Formidable page template (templates/Form) renders. Hidden (not offered as a view
 * choice), so it is free to serve the standalone page — and its authoring in the Page
 * Builder — without touching the fullPage or default views:
 *
 * - the default view is rendered read-only, so the Page Builder gets ONE box for the form
 *   node (this view's module) instead of one per nested render;
 * - the wrapper is padded, so that box always has a strip of its own around the form to
 *   click on: selecting it gives Edit on the form (title, intro, buttons, responses). No
 *   title here: jContent already shows it above the Page Builder;
 * - the wrapper carries page-specific classes (src/design/page.css) a template set never
 *   inherits inside its own pages.
 */
jahiaComponent(
	{
		componentType: "view",
		nodeType: "fmdb:form",
		name: "hidden.page"
	},
	(_, {currentNode}) => (
		<article className="fmdb-form-page-content">
			<Render node={currentNode} readOnly/>
		</article>
	)
);
