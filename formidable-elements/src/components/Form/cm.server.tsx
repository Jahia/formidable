import {jahiaComponent, Render} from "@jahia/javascript-modules-library";
import {CmShell} from "~/design/CmShell";

/**
 * Inspection view for jContent (preview drawer, Content Editor preview): those surfaces
 * render server markup with no JavaScript, so the live rendering is a dead end there — a
 * multi-step form stays frozen on its first step behind inert buttons, and logic-hidden
 * fields are unreachable. Instead of simulating a visit, this view shows what the form
 * CONTAINS: every step stacked under its title, conditional fields visible, and none of
 * the buttons (navigation, submit) that cannot work without a script. The form's own CSS
 * still applies, so the contributor recognises the form's look.
 *
 * Reached through graphql-core's cm fallback: jContent asks for no view at all because the
 * form is not a displayable node. Opening a form in the Page Builder does not change that:
 * the form stays without a page of its own (jmix:visuallyEditable, rendered through the
 * platform's content template and the hidden.visualEdit view), so this inspection keeps
 * its role in the drawer and the Content Editor preview.
 */
jahiaComponent(
	{
		componentType: "view",
		nodeType: "fmdb:form",
		name: "cm",
		displayName: "jContent internal view"
	},
	({intro, css}: {intro?: string; css?: string}, {currentNode}) => (
		<CmShell css={css}>
			{intro && <div className="fmdb-form-intro" dangerouslySetInnerHTML={{__html: intro}}/>}
			{currentNode.hasNode("fields") && (
				<Render
					node={currentNode.getNode("fields")}
					view="hidden.logic"
					parameters={{childView: "default", showLogicHidden: "true"}}
				/>
			)}
		</CmShell>
	)
);
