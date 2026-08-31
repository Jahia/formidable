import {jahiaComponent, Render} from "@jahia/javascript-modules-library";
import {CmShell} from "~/components/FormContainer/CmShell";

/**
 * Inspection view for jContent (preview drawer, Content Editor preview): those surfaces
 * render server markup with no JavaScript, so the live rendering is a dead end there — a
 * multi-step form stays frozen on its first step behind inert buttons, and logic-hidden
 * fields are unreachable. Instead of simulating a visit, this view shows what the form
 * CONTAINS: every step stacked under its title, conditional fields visible, and none of
 * the buttons (navigation, submit) that cannot work without a script. The form's own CSS
 * still applies, so the contributor recognises the form's look.
 *
 * Reached through graphql-core's cm fallback: jContent asks for no view at all while the
 * form is not a displayable node. The day fmdb:form becomes a displayable main resource
 * (the Page Builder work of #232), jContent asks for the default view instead and this
 * inspection is silently bypassed — re-point the preview (a j:view, or a jContent lever)
 * in that change.
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
