import {jahiaComponent, Render} from "@jahia/javascript-modules-library";

/**
 * What the Formidable page template (templates/Form) renders when a form is opened on its
 * own — the jContent Page Builder case. Same shape as the cm view: a view that delegates to
 * the default view through a nested Render (the framework idiom; a view component cannot be
 * imported and called, it needs the server context the framework passes). Hidden (not
 * offered as a view choice), so the standalone page can evolve without touching the default
 * view. The inner render is read-only, so the Page Builder gets ONE module for the form node
 * (this view's) instead of two nested boxes for the same path. Editing the form itself (title, intro, buttons, responses) goes through
 * jContent's own Edit button, the form being the current node.
 */
jahiaComponent(
	{
		componentType: "view",
		nodeType: "fmdb:form",
		name: "hidden.pageBuilder"
	},
	(_, {currentNode}) => <Render node={currentNode} readOnly/>
);
