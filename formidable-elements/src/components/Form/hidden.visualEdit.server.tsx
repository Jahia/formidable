import {
	AddResources,
	buildModuleFileUrl,
	jahiaComponent,
	Render,
} from "@jahia/javascript-modules-library";

/**
 * Page Builder view of a form opened from its content folder (jmix:visuallyEditable, Jahia
 * 8.2.5.0+). The platform renders such a form through its generic content template, an otherwise
 * empty page, so nothing around the form says which form is being authored. This view supplies that
 * frame — the form's title as a heading — and includes the default view inline. The inclusion is
 * what keeps the Page Builder to the one box the platform already draws around the form: rendering
 * the node again would nest a second box on the same path.
 *
 * Never reaches preview or live: the platform picks a hidden.visualEdit view in edit mode only.
 */
jahiaComponent(
	{
		componentType: "view",
		nodeType: "fmdb:form",
		name: "hidden.visualEdit",
	},
	(_, { currentNode }) => (
		<>
			<AddResources type="css" resources={buildModuleFileUrl("dist/assets/style.css")} />
			<div className="fmdb-visual-edit">
				<header className="fmdb-visual-edit-header">
					<h1 className="fmdb-visual-edit-title">{currentNode.getDisplayableName()}</h1>
				</header>
				<Render advanceRenderingConfig="INCLUDE" view="default" />
			</div>
		</>
	),
);
