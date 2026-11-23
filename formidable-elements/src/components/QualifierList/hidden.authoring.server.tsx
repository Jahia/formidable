import {jahiaComponent} from "@jahia/javascript-modules-library";
import {useTranslation} from "react-i18next";
import AuthoringActionsZone from "~/design/AuthoringActionsZone";
import {nodeTypeIconUrl} from "~/utils/actionTypeInfo";
import {siteOffersAType} from "~/utils/offeredTypes.server";

const QUALIFIER_TYPES_SERVICE = "org.jahia.modules.formidable.engine.api.QualifierTypes";

/**
 * The checks of a form that holds its uploads in quarantine, as a zone of the Page Builder between the
 * form's actions zone and its actions after release (edit mode only), the order in which they run. The
 * list's own create button offers the check types the modules of the site provide: its module declares
 * the accepted type to jContent, the fmdbmix:qualifier mixin. On a site with none, a call-out in the
 * button's place says so, and no button. Each check renders its card. Nothing of it exists in live or
 * preview.
 */
jahiaComponent(
	{
		componentType: "view",
		nodeType: "fmdb:qualifierList",
		name: "hidden.authoring",
	},
	(_props, {currentNode, renderContext}) => {
		const {t} = useTranslation("formidable-elements", {keyPrefix: "fmdb_qualifierList"});
		// Authoring-only, whatever asks for the view: a live or preview request of it gets nothing.
		if (!renderContext.isEditMode()) {
			return null;
		}
		// the list may hold an i18n child (j:translation_*): only the checks count
		const checkNodes = Array.from(currentNode.getNodes()).filter((node) => node.isNodeType("fmdbmix:qualifier"));
		const count = checkNodes.length;

		return (
			<AuthoringActionsZone
				className="fmdb-authoring-checks"
				heading={t("heading", {count})}
				hint={count > 0 ? t("inOrder") : t("notLive")}
				empty={t("empty")}
				unavailable={siteOffersAType(QUALIFIER_TYPES_SERVICE, "check", currentNode) ? undefined : t("noType")}
				iconUrl={nodeTypeIconUrl(currentNode, renderContext)}
				actionNodes={checkNodes}
			/>
		);
	},
);
