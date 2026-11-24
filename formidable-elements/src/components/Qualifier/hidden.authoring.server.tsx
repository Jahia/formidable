import {jahiaComponent} from "@jahia/javascript-modules-library";
import AuthoringActionCard from "~/design/AuthoringActionCard";
import {actionKeyDetail, describeActionType} from "~/utils/actionTypeInfo";

interface QualifierProps {
	"jcr:title"?: string;
}

/**
 * One check of the "Quarantine checks" list as a card of its authoring zone (see
 * QualifierList/hidden.authoring), drawn as a form action's card: its rank, the type icon, the
 * contributor's title (or the type label) with the first small text or choice its type declares, and
 * the type's own description. A view on the mixin, so a check type of any module gets the card.
 * Registered below the default priority, so a module's own `hidden.authoring` view for its type wins.
 */
jahiaComponent(
	{
		componentType: "view",
		nodeType: "fmdbmix:qualifier",
		name: "hidden.authoring",
		priority: -1,
	},
	({"jcr:title": title}: QualifierProps, {currentNode, renderContext}) => {
		// Authoring-only, whatever asks for the view: nothing of a check reaches live or preview.
		if (!renderContext.isEditMode()) {
			return null;
		}
		const type = describeActionType(currentNode, renderContext);

		return (
			<AuthoringActionCard
				typeName={type.name}
				iconUrl={type.iconUrl}
				title={title || type.label}
				detail={actionKeyDetail(currentNode, renderContext)}
				description={type.description}
			/>
		);
	},
);
