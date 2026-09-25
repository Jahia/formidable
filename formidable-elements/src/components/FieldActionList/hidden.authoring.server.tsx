import {AddContentButtons, jahiaComponent, Render} from "@jahia/javascript-modules-library";
import {useTranslation} from "react-i18next";
import AlertIcon from "~/design/AlertIcon";
import {nodeTypeIconUrl} from "~/utils/actionTypeInfo";

/**
 * A field's actions, as a zone of the Page Builder under the field (edit mode only — the element
 * wrapper renders it inside the field's own box, so it moves with the field). The form's actions
 * zone one level down: the checks of this field in their execution order (the list is orderable,
 * the first blocking refusal wins, so dragging a card reorders the checks), a call-out when the
 * switch is on but nothing checks the field yet, and the list's own create button — its module
 * declares the accepted type to jContent, the fmdbmix:fieldAction mixin: one button, then the
 * type chooser listing every deployed field-action type. Authoring chrome, deliberately not
 * styled like the form. Nothing of it exists in live or preview.
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
		const iconUrl = nodeTypeIconUrl(currentNode, renderContext);

		return (
			<aside className="fmdb-authoring-actions fmdb-authoring-field-actions" aria-label={t("heading", {count})}>
				<div className="fmdb-authoring-actions-header">
					<span className="fmdb-authoring-actions-title">
						{iconUrl && <img className="fmdb-authoring-actions-glyph" src={iconUrl} alt="" width={16} height={16}/>}
						{t("heading", {count})}
					</span>
					<span className="fmdb-authoring-actions-hint">{count > 0 ? t("inOrder") : t("notLive")}</span>
				</div>

				{count === 0 && (
					<p className="fmdb-authoring-actions-empty">
						<AlertIcon/>
						{t("empty")}
					</p>
				)}

				{count > 0 && (
					<ol className="fmdb-authoring-actions-list">
						{actionNodes.map((actionNode) => (
							<li key={actionNode.getIdentifier()} className="fmdb-authoring-actions-item">
								<Render node={actionNode} view="hidden.authoring"/>
							</li>
						))}
					</ol>
				)}

				<AddContentButtons/>
			</aside>
		);
	},
);
