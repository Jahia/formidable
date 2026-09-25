import {getNodeProps, jahiaComponent} from "@jahia/javascript-modules-library";
import {useTranslation} from "react-i18next";
import AuthoringActionCard from "~/design/AuthoringActionCard";
import {actionKeyDetail, describeActionType} from "~/utils/actionTypeInfo";

interface FieldActionProps {
	"jcr:title"?: string;
}

/** The four settings of fmdbmix:fieldActionFeedback; a node saved outside the editor may lack them, hence the CND defaults. */
interface FeedbackSettings {
	trigger?: string;
	severity?: string;
	whenUnavailable?: string;
}

/** The platform's generic sheet: it never inherits a mixin's icon, so a type shipping none resolves to nt:base. */
const GENERIC_ICON = "/nt_base.png";

/**
 * One field action as a card of the zone under its field (see FieldActionList/hidden.authoring):
 * the form action's card plus a line of badges reading the feedback settings the contributor
 * chose — when the check runs (as the visitor leaves the field, at submission), what a refusal
 * does (blocks, warns) and what an unanswered check means (accepted, refused). A view on the
 * mixin, so every field-action type, third-party included, gets the card; a type shipping no icon
 * is drawn with the marker's own glyph rather than the platform's generic sheet. Nothing reaches
 * live. Registered below the default priority so that a module shipping its own card for its
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
		const {t} = useTranslation("formidable-elements", {keyPrefix: "fmdb_fieldAction"});
		// Authoring-only, whatever asks for the view: providers and words stay out of live and preview.
		if (!renderContext.isEditMode()) {
			return null;
		}
		const type = describeActionType(currentNode, renderContext);
		const iconUrl = type.iconUrl?.endsWith(GENERIC_ICON)
			? `${renderContext.getRequest().getContextPath()}/modules/formidable-engine/icons/fmdbmix_fieldAction.png`
			: type.iconUrl;
		const {trigger = "blur", severity = "block", whenUnavailable = "accept"} =
			getNodeProps<FeedbackSettings>(currentNode, ["trigger", "severity", "whenUnavailable"]);

		return (
			<AuthoringActionCard
				typeName={type.name}
				iconUrl={iconUrl}
				title={title || type.label}
				detail={actionKeyDetail(currentNode, renderContext)}
				description={type.description}
				badges={[
					{setting: "trigger", label: t(trigger === "submit" ? "trigger_submit" : "trigger_blur")},
					{setting: "severity", label: t(severity === "warn" ? "severity_warn" : "severity_block")},
					{setting: "whenUnavailable", label: t(whenUnavailable === "reject" ? "unavailable_reject" : "unavailable_accept")},
				]}
			/>
		);
	},
);
