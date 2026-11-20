import {jahiaComponent} from "@jahia/javascript-modules-library";
import {useTranslation} from "react-i18next";
import AuthoringActionsZone from "~/design/AuthoringActionsZone";
import {nodeTypeIconUrl} from "~/utils/actionTypeInfo";

/**
 * The actions after release of a form that holds its uploads in quarantine, as a zone of the Page
 * Builder under the form's actions zone (edit mode only). These actions receive the released files:
 * they run once the uploads of a submission are released, in the order of the list, which the Page
 * Builder's drag reorders. The list's own create button offers the action types that may run with no
 * HTTP request — its module declares the accepted type to jContent, the fmdbmix:deferrableAction
 * mixin. Each action renders the card of a form action. Nothing of it exists in live or preview.
 */
jahiaComponent(
	{
		componentType: "view",
		nodeType: "fmdb:deferredActionList",
		name: "hidden.authoring",
	},
	(_props, {currentNode, renderContext}) => {
		const {t} = useTranslation("formidable-elements", {keyPrefix: "fmdb_deferredActionList"});
		// Authoring-only, whatever asks for the view: a live or preview request of it gets nothing.
		if (!renderContext.isEditMode()) {
			return null;
		}
		const actionNodes = Array.from(currentNode.getNodes()).filter((node) => node.isNodeType("fmdbmix:deferrableAction"));
		const count = actionNodes.length;

		return (
			<AuthoringActionsZone
				className="fmdb-authoring-deferred-actions"
				heading={t("heading", {count})}
				hint={count > 0 ? t("inOrder") : t("notLive")}
				empty={t("empty")}
				iconUrl={nodeTypeIconUrl(currentNode, renderContext)}
				actionNodes={actionNodes}
			/>
		);
	},
);
