import {AddContentButtons, Render} from "@jahia/javascript-modules-library";
import clsx from "clsx";
import type {JCRNodeWrapper} from "org.jahia.services.content";
import AlertIcon from "./AlertIcon";

export interface AuthoringActionsZoneProps {
	/** The variant class next to `fmdb-authoring-actions`: the field zone's, when this is one. */
	className?: string;
	/** The header's title, also the zone's accessible name. */
	heading: string;
	/** The header's right-hand hint: the execution-order note with actions, the not-live note without. */
	hint: string;
	/** The call-out drawn when the list is empty. */
	empty: string;
	/** The list's own type icon, in the header — the one jContent shows on the list's box and in the tree. */
	iconUrl?: string;
	/** The actions, in execution order; each renders its own `hidden.authoring` card. */
	actionNodes: JCRNodeWrapper[];
}

/**
 * The chrome the two actions zones share — the form's, under the buttons, and a field's, under the
 * field (ActionList and FieldActionList `hidden.authoring` views): a header with the list's icon and
 * count, the call-out of an empty list, the ordered cards — an ordered list, since the execution order
 * is the meaning and assistive technology gets it too — and the list's own create button, whose module
 * declares the accepted type to jContent. Authoring chrome, deliberately not styled like the form.
 */
export default function AuthoringActionsZone({className, heading, hint, empty, iconUrl, actionNodes}: AuthoringActionsZoneProps) {
	const count = actionNodes.length;

	return (
		<aside className={clsx("fmdb-authoring-actions", className)} aria-label={heading}>
			<div className="fmdb-authoring-actions-header">
				<span className="fmdb-authoring-actions-title">
					{iconUrl && <img className="fmdb-authoring-actions-glyph" src={iconUrl} alt="" width={16} height={16}/>}
					{heading}
				</span>
				<span className="fmdb-authoring-actions-hint">{hint}</span>
			</div>

			{count === 0 && (
				<p className="fmdb-authoring-actions-empty">
					<AlertIcon/>
					{empty}
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
}
