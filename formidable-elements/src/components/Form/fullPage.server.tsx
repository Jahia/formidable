import {jahiaComponent, Render} from "@jahia/javascript-modules-library";

/**
 * The form as the main resource of its own page: the default view, kept as a distinct view
 * name so a template set can override the page rendering of a form without touching how a
 * form renders inside a page. The Formidable page template itself renders hidden.page.
 */
jahiaComponent(
	{
		componentType: "view",
		nodeType: "fmdb:form",
		name: "fullPage",
		displayName: "Full page"
	},
	(_, {currentNode}) => <Render node={currentNode}/>
);
