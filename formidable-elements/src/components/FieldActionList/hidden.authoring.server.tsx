import {jahiaComponent} from "@jahia/javascript-modules-library";
import {useTranslation} from "react-i18next";
import AuthoringActionsZone from "~/design/AuthoringActionsZone";
import {nodeTypeIconUrl} from "~/utils/actionTypeInfo";

/**
 * A field's actions, as a zone of the Page Builder under the field (edit mode only — the element
 * wrapper renders it inside the field's own box, so it moves with the field). The form's actions
 * zone one level down: the checks of this field in their execution order (the list is orderable,
 * the first blocking refusal wins, so dragging a card reorders the checks), a call-out when the
 * switch is on but nothing checks the field yet, and the list's own create button — its module
 * declares the accepted type to jContent, the fmdbmix:fieldAction mixin: one button, then the
 * type chooser listing every deployed field-action type. Nothing of it exists in live or preview.
 */
jahiaComponent(
	{
		componentType: "view",
		nodeType: "fmdb:fieldActionList",
		name: "hidden.authoring",
	},
	(_props, {currentNode, renderContext}) => {
		const {t} = useTranslation("formidable-elements", {keyPrefix: "fmdb_fieldActionList"});
		// Authoring-only, whatever asks for the view: a live or preview request of it gets nothing.
		if (!renderContext.isEditMode()) {
			return null;
		}
		const actionNodes = Array.from(currentNode.getNodes()).filter((node) => node.isNodeType("fmdbmix:fieldAction"));
		const count = actionNodes.length;

		return (
			<AuthoringActionsZone
				className="fmdb-authoring-field-actions"
				heading={t("heading", {count})}
				hint={count > 0 ? t("inOrder") : t("notLive")}
				empty={t("empty")}
				iconUrl={nodeTypeIconUrl(currentNode, renderContext)}
				actionNodes={actionNodes}
			/>
		);
	},
);
