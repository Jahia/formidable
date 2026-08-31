import {getNodeProps, Render, useServerContext} from "@jahia/javascript-modules-library";
import clsx from "clsx";
import {type ConditionalLogicRule, parseConditionalLogicRules} from "~/utils/conditionalLogic";

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
		>
			{view
				? <Render node={node} view={view} parameters={parameters}/>
				: <Render node={node} parameters={parameters}/>}
		</div>
	);
};

LogicAwareRender.displayName = "LogicAwareRender";

export default LogicAwareRender;
