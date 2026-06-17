import {getNodeProps, Render} from "@jahia/javascript-modules-library";
import {type ConditionalLogicRule, parseConditionalLogicRules} from "~/utils/conditionalLogic";

type LogicAwareRenderNode = Parameters<typeof getNodeProps>[0];

export interface LogicAwareRenderProps {
	node: LogicAwareRenderNode;
	view?: string;
	parameters?: Record<string, string>;
	className?: string;
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

const LogicAwareRender = ({node, view, parameters, className}: LogicAwareRenderProps) => {
	const {logics: rawLogics} = getNodeProps<{logics?: string[]}>(node, ["logics"]);
	const logics = node.isNodeType("fmdbmix:formLogicElement")
		? parseConditionalLogicRules(rawLogics ?? [])
		: [];

	const hasLogic = logics.length > 0;

	if (hasLogic) {
		resolveSourceNodeIds(node, logics);
	}

	return (
		<div
			className={className}
			style={hasLogic ? {display: "none"} : undefined}
			aria-hidden={hasLogic ? "true" : undefined}
			data-fmdb-logic-hidden={hasLogic ? "true" : undefined}
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
