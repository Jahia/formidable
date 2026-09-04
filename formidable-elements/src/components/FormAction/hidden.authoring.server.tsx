import {jahiaComponent} from "@jahia/javascript-modules-library";
import {actionKeyDetail, describeActionType} from "~/utils/actionTypeInfo";

interface FormActionProps {
	"jcr:title"?: string;
}

/**
 * One action as a card of the authoring zone (see ActionList/hidden.authoring): its rank
 * (a CSS counter on the list), the type icon, the contributor's title with the one
 * parameter that tells two actions of a kind apart (recipient, forward target), and
 * under it, smaller, the type's own description — the tooltip its module declares for
 * the Content Editor. A view on the mixin so every action type, third-party included,
 * gets the card; nothing here reaches live.
 */
jahiaComponent(
	{
		componentType: "view",
		nodeType: "fmdbmix:formAction",
		name: "hidden.authoring",
	},
	({"jcr:title": title}: FormActionProps, {currentNode, renderContext}) => {
		const type = describeActionType(currentNode, renderContext);
		const detail = actionKeyDetail(currentNode, type.name);

		return (
			<div className="fmdb-authoring-action" data-fmdb-action-type={type.name}>
				{/* Decorative: the title carries the meaning. An empty alt keeps a missing icon silent. */}
				<img className="fmdb-authoring-action-icon" src={type.iconUrl} alt="" width={16} height={16}/>
				<div className="fmdb-authoring-action-body">
					<div className="fmdb-authoring-action-line">
						<span className="fmdb-authoring-action-title">{title || type.label}</span>
						{detail && <span className="fmdb-authoring-action-detail">{detail}</span>}
					</div>
					{type.description && (
						<div className="fmdb-authoring-action-description" title={type.description}>
							{type.description}
						</div>
					)}
				</div>
			</div>
		);
	},
);
