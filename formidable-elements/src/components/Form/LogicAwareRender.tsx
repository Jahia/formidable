import type {JCRNodeWrapper} from "org.jahia.services.content";
import {getNodeProps, Render} from "@jahia/javascript-modules-library";
import {parseConditionalLogicRules} from "~/utils/conditionalLogic";

interface LogicAwareRenderProps {
	node: JCRNodeWrapper;
	view?: string;
	parameters?: Record<string, string>;
	className?: string;
}

export default function LogicAwareRender({node, view, parameters, className}: LogicAwareRenderProps) {
	const {'logics': rawLogics} = getNodeProps<{logics?: string[]}>(node, ['logics']);
	const logics = node.isNodeType('fmdbmix:formLogicElement')
		? parseConditionalLogicRules(rawLogics ?? [])
		: [];

	return (
		<div
			className={className}
			data-fmdb-node-id={node.getIdentifier()}
			data-fmdb-node-name={node.getName()}
			data-fmdb-node-type={node.getPrimaryNodeTypeName()}
			data-fmdb-logics={logics.length > 0 ? JSON.stringify(logics) : undefined}
		>
			<Render node={node} view={view} parameters={parameters}/>
		</div>
	);
}
