import {jahiaComponent, Render} from "@jahia/javascript-modules-library";

jahiaComponent(
	{
		componentType: "view",
		nodeType: "fmdb:step",
		name: "minimal",
		displayName: "Fields only"
	},
	(_props, {currentNode, currentResource}) => {
		const initiallyHidden = currentResource.getModuleParams().get("initiallyHidden")?.toString() === "true";
		return (
			<div data-fmdb-step className="fmdb-step" style={initiallyHidden ? {display: 'none'} : undefined}>
				<Render
					node={currentNode}
					view="logic.hidden"
					parameters={{childClassName: "fmdb-form-element"}}
				/>
			</div>
		);
	}
);
