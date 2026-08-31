import {AddResources, buildModuleFileUrl, jahiaComponent, Render} from "@jahia/javascript-modules-library";

/**
 * Inspection view for jContent (preview drawer, Content Editor preview): those surfaces
 * render server markup with no JavaScript, so the live rendering is a dead end there — a
 * multi-step form stays frozen on its first step behind inert buttons, and logic-hidden
 * fields are unreachable. Instead of simulating a visit, this view shows what the form
 * CONTAINS: every step stacked under its title, conditional fields visible, and none of
 * the buttons (navigation, submit) that cannot work without a script. The form's own CSS
 * still applies, so the contributor recognises the form's look.
 */
jahiaComponent(
	{
		componentType: "view",
		nodeType: "fmdb:form",
		name: "cm",
		displayName: "jContent internal view"
	},
	({intro, css}: {intro?: string; css?: string}, {currentNode}) => {
		const fieldListNode = currentNode.getNode("fields");

		return (
			<>
				{css && <style>{css}</style>}
				<AddResources type="css" resources={buildModuleFileUrl("dist/assets/style.css")}/>
				{/* Not a <form>: nothing here submits. The class keeps the business stylesheet's look. */}
				<div className="fmdb-form" data-fmdb-cm-view="true">
					{intro && <div className="fmdb-form-intro" dangerouslySetInnerHTML={{__html: intro}}/>}
					{fieldListNode && (
						<Render
							node={fieldListNode}
							view="hidden.logic"
							parameters={{childView: "default", showLogicHidden: "true"}}
						/>
					)}
				</div>
			</>
		);
	}
);
