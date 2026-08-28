import {jahiaComponent, Render} from "@jahia/javascript-modules-library";

/**
 * What the Formidable page template (templates/Form) renders: the form, as fullPage does.
 * Hidden (not offered as a view choice), so the standalone page — and its authoring in the
 * Page Builder — can evolve without touching the fullPage or default views. The one
 * difference with fullPage: the inner render is read-only, so the Page Builder gets ONE
 * module for the form node (this view's) instead of two nested boxes for the same path.
 * Editing the form itself (title, intro, buttons, responses) goes through jContent's own
 * Edit button, the form being the current node.
 */
jahiaComponent(
	{
		componentType: "view",
		nodeType: "fmdb:form",
		name: "hidden.page"
	},
	(_, {currentNode}) => <Render node={currentNode} readOnly/>
);
