import {jahiaComponent} from "@jahia/javascript-modules-library";
import {useTranslation} from "react-i18next";
import AuthoringActionCard from "~/design/AuthoringActionCard";
import {describeActionType} from "~/utils/actionTypeInfo";
import {followsAnotherRedirect, type RedirectActionNodeLike, redirectTargetDetail} from "~/utils/redirectAction.server";

interface RedirectActionProps {
	"jcr:title"?: string;
}

/**
 * The card of a redirect action in the form's actions zone: the card every action gets (FormAction/hidden.authoring),
 * with the target as its key parameter, the page's title or the URL, rather than the target type the generic rule
 * would show. A redirect after another one carries a warning: the visitor is sent to the first usable target of the
 * list, so this one is a fallback at best (docs/architecture/redirect-action.md). At the default priority, so it
 * wins over the mixin's card view.
 */
jahiaComponent(
	{
		componentType: "view",
		nodeType: "fmdb:redirectAction",
		name: "hidden.authoring",
	},
	({"jcr:title": title}: RedirectActionProps, {currentNode, renderContext}) => {
		const {t} = useTranslation("formidable-elements", {keyPrefix: "fmdb_redirectAction"});
		// Authoring-only, whatever asks for the view: targets stay out of live and preview.
		if (!renderContext.isEditMode()) {
			return null;
		}
		const type = describeActionType(currentNode, renderContext);
		const node = currentNode as unknown as RedirectActionNodeLike;

		return (
			<AuthoringActionCard
				typeName={type.name}
				iconUrl={type.iconUrl}
				title={title || type.label}
				detail={redirectTargetDetail(node)}
				description={type.description}
				warning={followsAnotherRedirect(node) ? {state: "overridden-redirect", text: t("overridden")} : undefined}
			/>
		);
	},
);
