import {jahiaComponent, server} from "@jahia/javascript-modules-library";
import type {JCRNodeWrapper} from "org.jahia.services.content";
import {useTranslation} from "react-i18next";
import AuthoringActionsZone from "~/design/AuthoringActionsZone";
import {nodeTypeIconUrl} from "~/utils/actionTypeInfo";

const QUALIFIER_TYPES_SERVICE = "org.jahia.modules.formidable.engine.api.QualifierTypes";

// The engine's service, reached over OSGi; offeredUnder answers a java.util.List.
interface QualifierTypesLike {
	offeredUnder(qualifierList: JCRNodeWrapper): {isEmpty(): boolean};
}

/**
 * Whether the site offers a check type to add under this list: what the chooser behind the create button would
 * list, as the engine reads it from the modules enabled on the site. The engine ships no concrete type, so a site
 * where no module providing one is enabled has none, and the button would open on nothing (as #404 was for the
 * field actions). A question the engine cannot answer counts as yes: the button then shows, and the log says why.
 */
const siteOffersACheckType = (qualifierList: JCRNodeWrapper): boolean => {
	try {
		const service = server.osgi.getService(QUALIFIER_TYPES_SERVICE) as QualifierTypesLike | null;
		if (!service) {
			console.warn("[Formidable] The engine's QualifierTypes service is not available: the create button shows regardless");
			return true;
		}
		return !service.offeredUnder(qualifierList).isEmpty();
	} catch (error) {
		console.error(`[Formidable] Could not list the check types offered under ${qualifierList.getPath()}`, error);
		return true;
	}
};

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
				unavailable={siteOffersACheckType(currentNode) ? undefined : t("noType")}
				iconUrl={nodeTypeIconUrl(currentNode, renderContext)}
				actionNodes={checkNodes}
			/>
		);
	},
);
