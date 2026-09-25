import {getNodeProps, Render, useServerContext} from "@jahia/javascript-modules-library";
import clsx from "clsx";
import {type ConditionalLogicRule, parseConditionalLogicRules} from "~/utils/conditionalLogic";
import {type FieldActionNodeLike, type FieldActionsOfNode, fieldActionsOf} from "~/utils/fieldActions.server";

type LogicAwareRenderNode = Parameters<typeof getNodeProps>[0];

export interface LogicAwareRenderProps {
	node: LogicAwareRenderNode;
	view?: string;
	parameters?: Record<string, string>;
	className?: string;
	/** Keeps logic-driven elements visible outside edit mode (the cm inspection view). */
	showLogicHidden?: boolean;
}

/**
 * Enriches rendered rules with source node UUIDs from logicsSrc weakreferences.
 * Each logicId in the parsed rules maps to a child node under logicsSrc
 * whose logicNodeSource property points to the actual source field.
 * Mutates rules in place to add or refresh sourceNodeId for runtime evaluation.
 */
const resolveSourceNodeIds = (node: LogicAwareRenderNode, logics: ConditionalLogicRule[]) => {
	try {
		if (!node.hasNode("logicsSrc")) return;
		const logicsSrcNode = node.getNode("logicsSrc");
		for (const rule of logics) {
			if (!rule.logicId) continue;
			try {
				if (!logicsSrcNode.hasNode(rule.logicId)) continue;
				const srcChild = logicsSrcNode.getNode(rule.logicId);
				const sourceNode = srcChild.getProperty("logicNodeSource").getNode();
				rule.sourceNodeId = sourceNode.getIdentifier();
			} catch (e) {
				console.error(`[LogicAwareRender] Broken weakref for logicId '${rule.logicId}' on node '${node.getPath()}':`, e);
			}
		}
	} catch (e) {
		console.error(`[LogicAwareRender] Failed to access logicsSrc on node '${node.getPath()}':`, e);
	}
};

/**
 * The field's actions, read for the marker and the zone; an unreadable node is a field without
 * actions here — the pipeline, which walks the form itself, still judges the submission.
 */
const resolveFieldActions = (node: LogicAwareRenderNode): FieldActionsOfNode => {
	try {
		return fieldActionsOf(node as unknown as FieldActionNodeLike);
	} catch (e) {
		console.error(`[LogicAwareRender] Failed to read the field actions of node '${node.getPath()}':`, e);
		return {hasList: false};
	}
};

const LogicAwareRender = ({node, view, parameters, className, showLogicHidden}: LogicAwareRenderProps) => {
	const {renderContext} = useServerContext();
	const {logics: rawLogics} = getNodeProps<{logics?: string[]}>(node, ["logics"]);
	const logics = node.isNodeType("fmdbmix:formLogicElement")
		? parseConditionalLogicRules(rawLogics ?? [])
		: [];

	const hasLogic = logics.length > 0;

	if (hasLogic) {
		resolveSourceNodeIds(node, logics);
	}

	// In Page Builder, logic-hidden elements must stay visible to remain editable; the cm
	// inspection view keeps them visible too (no script there would ever reveal them).
	const hideForLogic = hasLogic && !renderContext.isEditMode() && !showLogicHidden;

	// Field actions (docs/architecture/field-actions.md): the marker tells the client when to ask
	// the engine about this field, the zone shows the contributor what checks it. Both live on
	// this wrapper rather than in the field views, so a field from any module gets them without
	// calling anything — fields only ever render inside a Formidable container.
	const fieldActions = resolveFieldActions(node);
	const isEditMode = renderContext.isEditMode();

	return (
		<div
			// Stable, server-rendered hook: the element is driven by conditional logic,
			// whatever its current visibility (that state lives in data-fmdb-logic-hidden).
			className={clsx(className, hasLogic && "fmdb-logic-target")}
			style={hideForLogic ? {display: "none"} : undefined}
			aria-hidden={hideForLogic ? "true" : undefined}
			data-fmdb-logic-hidden={hideForLogic ? "true" : undefined}
			data-fmdb-node-id={node.getIdentifier()}
			data-fmdb-node-name={node.getName()}
			data-fmdb-node-type={node.getPrimaryNodeTypeName()}
			data-fmdb-logics={hasLogic ? JSON.stringify(logics) : undefined}
			// Rendered on every surface (harmless: the client hook is off in edit mode), absent when
			// nothing checks the field, so the client never asks about a field with no action.
			data-fmdb-field-action={fieldActions.trigger}
		>
			{view
				? <Render node={node} view={view} parameters={parameters}/>
				: <Render node={node} parameters={parameters}/>}
			{/* The checks of this field, as a zone under it while authoring — inside the wrapper so
			    it moves with the field's Page Builder box. Rendered as soon as the switch is on, empty
			    list included (that is where the create button lives); the views also answer nothing
			    outside edit mode, so nothing of it reaches live or preview. */}
			{isEditMode && fieldActions.hasList && (
				<Render node={node.getNode("actions")} view="hidden.authoring"/>
			)}
		</div>
	);
};

LogicAwareRender.displayName = "LogicAwareRender";

export default LogicAwareRender;
