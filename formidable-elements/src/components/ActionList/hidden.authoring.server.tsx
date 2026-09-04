import {AddContentButtons, jahiaComponent, Render} from "@jahia/javascript-modules-library";
import {useTranslation} from "react-i18next";

// Lucide-inspired glyphs, drawn here: no icon library.
const ZapIcon = () => (
	<svg className="fmdb-authoring-actions-glyph" viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
		<polygon points="13 2 4 14 11 14 10 22 20 10 13 10 14 2"/>
	</svg>
);

const AlertIcon = () => (
	<svg className="fmdb-authoring-actions-glyph" viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
		<path d="M12 3 2 20h20L12 3z"/>
		<line x1="12" y1="9" x2="12" y2="14"/>
		<line x1="12" y1="17" x2="12.01" y2="17"/>
	</svg>
);

/**
 * The form's actions, as a zone of the Page Builder (edit mode only — the form's default
 * view renders it inside the form, under the buttons). Actions run after the submission
 * and are otherwise invisible on a page: the zone lists them in their execution order
 * (the list is orderable, so the Page Builder's drag reorders the pipeline), calls out a
 * form that has none (its submissions are neither stored nor sent), and carries the
 * list's own create button — the placeholder jContent reads the accepted type from, which
 * is the fmdbmix:formAction mixin: one "New Form Action" button, then the type chooser.
 * Authoring chrome, deliberately not styled like the form: it is not the visitor's form.
 */
jahiaComponent(
	{
		componentType: "view",
		nodeType: "fmdb:actionList",
		name: "hidden.authoring",
	},
	(_props, {currentNode}) => {
		const {t} = useTranslation("formidable-elements", {keyPrefix: "fmdb_actionList"});
		const actionNodes = Array.from(currentNode.getNodes()).filter((node) => node.isNodeType("fmdbmix:formAction"));
		const count = actionNodes.length;

		return (
			<aside className="fmdb-authoring-actions" aria-label={t("heading", {count})}>
				<div className="fmdb-authoring-actions-header">
					<span className="fmdb-authoring-actions-title">
						<ZapIcon/>
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
					<div className="fmdb-authoring-actions-list">
						{actionNodes.map((actionNode) => (
							<Render key={actionNode.getIdentifier()} node={actionNode} view="hidden.authoring"/>
						))}
					</div>
				)}

				<AddContentButtons/>
			</aside>
		);
	},
);
