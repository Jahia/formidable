import {jahiaComponent} from "@jahia/javascript-modules-library";
import AuthoringActionCard from "~/design/AuthoringActionCard";
import {actionKeyDetail, describeActionType} from "~/utils/actionTypeInfo";

interface FieldActionProps {
	"jcr:title"?: string;
}

/** The platform's generic sheet: it never inherits a mixin's icon, so a type shipping none resolves to nt:base. */
const GENERIC_ICON = "/nt_base.png";

/**
 * One field action as a card of the zone under its field (see FieldActionList/hidden.authoring):
 * the form action's card, exactly — the settings the contributor chose (when the check runs, what
 * a refusal does, what an unanswered check means) are read in the editor, as a form action's are.
 * A view on the mixin, so every field-action type, third-party included, gets the card; a type
 * shipping no icon is drawn with the marker's own glyph rather than the platform's generic sheet.
 * Nothing reaches live. Registered below the default priority so that a module shipping its own card for its
 * type wins for sure (see FormAction/hidden.authoring).
 */
jahiaComponent(
	{
		componentType: "view",
		nodeType: "fmdbmix:fieldAction",
		name: "hidden.authoring",
		priority: -1,
	},
	({"jcr:title": title}: FieldActionProps, {currentNode, renderContext}) => {
		// Authoring-only, whatever asks for the view: providers and words stay out of live and preview.
		if (!renderContext.isEditMode()) {
			return null;
		}
		const type = describeActionType(currentNode, renderContext);
		const iconUrl = type.iconUrl?.endsWith(GENERIC_ICON)
			? `${renderContext.getRequest().getContextPath()}/modules/formidable-engine/icons/fmdbmix_fieldAction.png`
			: type.iconUrl;
		return (
			<AuthoringActionCard
				typeName={type.name}
				iconUrl={iconUrl}
				title={title || type.label}
				detail={actionKeyDetail(currentNode, renderContext)}
				description={type.description}
			/>
		);
	},
);
