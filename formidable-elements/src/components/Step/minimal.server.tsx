import {jahiaComponent} from "@jahia/javascript-modules-library";
import LogicAwareRender from "~/components/Form/LogicAwareRender";

jahiaComponent(
	{
		componentType: "view",
		nodeType: "fmdb:step",
		name: "minimal",
		displayName: "Fields only"
	},
	(_props, {currentNode, currentResource}) => {
		const elements = Array.from(currentNode.getNodes());
		const initiallyHidden = currentResource.getModuleParams().get("initiallyHidden")?.toString() === "true";
		return (
			<div data-fmdb-step className="fmdb-step" style={initiallyHidden ? {display: 'none'} : undefined}>
				{elements.map((element) => (
					<LogicAwareRender
						key={element.getIdentifier()}
						node={element}
						className="fmdb-form-element"
					/>
				))}
			</div>
		);
	}
);
