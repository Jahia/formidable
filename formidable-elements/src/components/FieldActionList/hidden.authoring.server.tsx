import {jahiaComponent, server} from "@jahia/javascript-modules-library";
import type {JCRNodeWrapper} from "org.jahia.services.content";
import {useTranslation} from "react-i18next";
import AuthoringActionsZone from "~/design/AuthoringActionsZone";
import {nodeTypeIconUrl} from "~/utils/actionTypeInfo";

const FIELD_ACTION_TYPES_SERVICE = "org.jahia.modules.formidable.engine.api.FieldActionTypes";

// The engine's service, reached over OSGi; it answers a java.util.List.
interface FieldActionTypesLike {
	offeredUnder(actionList: JCRNodeWrapper): {isEmpty(): boolean};
}

/**
 * Whether the site offers a field-action type to add under this list: what the chooser behind the create
 * button would list, as the engine reads it from the modules enabled on the site — the engine ships no
 * concrete type, so a site where no module providing one is enabled has none, and the button would open
 * on nothing and vanish (#404). A question the engine cannot answer counts as yes: the button then shows
 * as it always did, and the log says why.
 */
const siteOffersAType = (actionList: JCRNodeWrapper): boolean => {
	try {
		const service = server.osgi.getService(FIELD_ACTION_TYPES_SERVICE) as FieldActionTypesLike | null;
		if (!service) {
			console.warn("[Formidable] The engine's FieldActionTypes service is not available: the create button shows regardless");
			return true;
		}
		return !service.offeredUnder(actionList).isEmpty();
	} catch (error) {
		console.error(`[Formidable] Could not list the field-action types offered under ${actionList.getPath()}`, error);
		return true;
	}
};

/**
 * A field's actions, as a zone of the Page Builder under the field (edit mode only — the element
 * wrapper renders it inside the field's own box, so it moves with the field). The form's actions
 * zone one level down: the checks of this field in their execution order (the list is orderable,
 * the first blocking refusal wins, so dragging a card reorders the checks), a call-out when the
 * switch is on but nothing checks the field yet, and the list's own create button — its module
 * declares the accepted type to jContent, the fmdbmix:fieldAction mixin: one button, then the
 * type chooser listing the field-action types of the modules enabled on the site. On a site with
 * none, a call-out in the button's place says so, and no button. Nothing of it exists in live or preview.
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
				unavailable={siteOffersAType(currentNode) ? undefined : t("noType")}
				iconUrl={nodeTypeIconUrl(currentNode, renderContext)}
				actionNodes={actionNodes}
			/>
		);
	},
);
