import {jahiaComponent, Render} from "@jahia/javascript-modules-library";

/**
 * The form as the main resource of its own page (see templates/Form): the form's title, then
 * the form itself. Rendered read-only so the Page Builder gets ONE box for the form node (this
 * view's), and the title lives inside that box: hovering it selects the form, whose Edit opens
 * the title, the intro, the buttons and the responses. Kept as a distinct view name so a
 * template set can override the page rendering of a form without touching how a form renders
 * inside a page.
 */
jahiaComponent(
	{
		componentType: "view",
		nodeType: "fmdb:form",
		name: "fullPage",
		displayName: "Full page"
	},
	({"jcr:title": title}: {"jcr:title"?: string}, {currentNode}) => (
		<>
			{title && <h1 className="fmdb-form-page-title">{title}</h1>}
			<Render node={currentNode} readOnly/>
		</>
	)
);
