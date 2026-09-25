import {jahiaComponent} from "@jahia/javascript-modules-library";
import {useTranslation} from "react-i18next";
import AuthoringActionsZone from "~/design/AuthoringActionsZone";
import {nodeTypeIconUrl} from "~/utils/actionTypeInfo";

/**
 * The form's actions, as a zone of the Page Builder (edit mode only — the form's default
 * view renders it inside the form, under the buttons). Actions run after the submission
 * and are otherwise invisible on a page: the zone lists them in their execution order
 * (the list is orderable, so the Page Builder's drag reorders the pipeline), calls out a
 * form that has none (its submissions are neither stored nor sent), and carries the
 * list's own create button — its module declares the accepted type to jContent, the
 * fmdbmix:formAction mixin: one "New Form Action" button, then the type chooser.
 * The chrome itself is AuthoringActionsZone, shared with the field zone.
 */
jahiaComponent(
	{
		componentType: "view",
		nodeType: "fmdb:actionList",
		name: "hidden.authoring",
	},
	(_props, {currentNode, renderContext}) => {
		const {t} = useTranslation("formidable-elements", {keyPrefix: "fmdb_actionList"});
		// Authoring-only, whatever asks for the view: a live or preview request of it gets nothing.
		if (!renderContext.isEditMode()) {
			return null;
		}
		const actionNodes = Array.from(currentNode.getNodes()).filter((node) => node.isNodeType("fmdbmix:formAction"));
		const count = actionNodes.length;

		return (
			<AuthoringActionsZone
				heading={t("heading", {count})}
				hint={count > 0 ? t("inOrder") : t("notLive")}
				empty={t("empty")}
				iconUrl={nodeTypeIconUrl(currentNode, renderContext)}
				actionNodes={actionNodes}
			/>
		);
	},
);
