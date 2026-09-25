import {jahiaComponent} from "@jahia/javascript-modules-library";
import AuthoringActionCard from "~/design/AuthoringActionCard";
import {actionKeyDetail, describeActionType} from "~/utils/actionTypeInfo";

interface FormActionProps {
	"jcr:title"?: string;
}

/**
 * One action as a card of the authoring zone (see ActionList/hidden.authoring): its rank
 * (a CSS counter on the list), the type icon, the contributor's title with the action's
 * key parameter — the first small text or choice its type declares after the title
 * (recipient, forward target), a choice shown by its label — and
 * under it, smaller, the type's own description — the tooltip its module declares for
 * the Content Editor. A view on the mixin so every action type, third-party included,
 * gets the card; nothing here reaches live.
 *
 * Registered below the default priority so that a module shipping its own `hidden.authoring`
 * view for its action type wins for sure: the engine picks among the candidate views of a node
 * (its type's and its mixins') by priority first, then by module name — a card view left at the
 * default priority would only beat this one when its module's name sorts first.
 */
jahiaComponent(
	{
		componentType: "view",
		nodeType: "fmdbmix:formAction",
		name: "hidden.authoring",
		priority: -1,
	},
	({"jcr:title": title}: FormActionProps, {currentNode, renderContext}) => {
		// Authoring-only, whatever asks for the view: recipients and targets stay out of live and preview.
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
