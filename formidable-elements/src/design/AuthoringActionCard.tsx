export interface AuthoringActionCardProps {
	/** The action's node type, as `data-fmdb-action-type`. */
	typeName: string;
	/** The type icon; none draws nothing rather than a broken image. */
	iconUrl?: string;
	/** The contributor's title, or the type label without one. */
	title: string;
	/** The action's key parameter: the first small text or choice its type declares after the title. */
	detail?: string;
	/** The type's own description: the tooltip its module declares for the Content Editor. */
	description?: string;
}

/**
 * One action as a card of an authoring zone (see AuthoringActionsZone): its rank — a CSS counter on
 * the list —, the type icon, the title with the key parameter, and under it, smaller, the type
 * description. One card for the two kinds of action: a field action's settings are read in its
 * editor, as a form action's are. Rendered by the `hidden.authoring` views on the two action
 * mixins, so every action type, third-party included, gets the card.
 */
export default function AuthoringActionCard({typeName, iconUrl, title, detail, description}: Readonly<AuthoringActionCardProps>) {
	return (
		<div className="fmdb-authoring-action" data-fmdb-action-type={typeName}>
			{/* Decorative: the title carries the meaning, so the alt is empty. */}
			{iconUrl && (
				<img className="fmdb-authoring-action-icon" src={iconUrl} alt="" width={16} height={16}/>
			)}
			<div className="fmdb-authoring-action-body">
				<div className="fmdb-authoring-action-line">
					<span className="fmdb-authoring-action-title">{title}</span>
					{detail && <span className="fmdb-authoring-action-detail">{detail}</span>}
				</div>
				{description && (
					<div className="fmdb-authoring-action-description" title={description}>
						{description}
					</div>
				)}
			</div>
		</div>
	);
}
