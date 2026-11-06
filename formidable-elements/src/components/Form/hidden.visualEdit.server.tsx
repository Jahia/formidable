import { jahiaComponent, Render } from "@jahia/javascript-modules-library";

/**
 * Page Builder view of a form opened from its content folder. jContent renders such a form through
 * the generic content template of the default module, an otherwise empty page, where the form would
 * span the whole frame. This view puts the form in a readable column and includes the default view
 * inline. The inclusion is what keeps the Page Builder to the one box the platform already draws
 * around the form: rendering the node again would nest a second box on the same path. jContent's
 * header already names the form, so the view adds no title of its own.
 *
 * Never reaches preview or live: the default module (8.11.0+) picks a hidden.visualEdit view in
 * edit mode only, for the main resource of the frame.
 */
jahiaComponent(
	{
		componentType: "view",
		nodeType: "fmdb:form",
		name: "hidden.visualEdit",
	},
	() => (
		<div className="fmdb-visual-edit">
			<Render advanceRenderingConfig="INCLUDE" view="default" />
		</div>
	),
);
