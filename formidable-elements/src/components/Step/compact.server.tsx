import {jahiaComponent, Render} from "@jahia/javascript-modules-library";

interface StepProps {
	intro?: string;
}

jahiaComponent(
	{
		componentType: "view",
		nodeType: "fmdb:step",
		name: "compact",
		displayName: "Without title"
	},
	({intro}: StepProps, {currentNode, currentResource}) => {
		const initiallyHidden = currentResource.getModuleParams().get("initiallyHidden")?.toString() === "true";
		return (
			<div data-fmdb-step className="fmdb-step" style={initiallyHidden ? {display: 'none'} : undefined}>
				{intro && <div className="fmdb-step-intro" dangerouslySetInnerHTML={{__html: intro}}/>}
				<Render
					node={currentNode}
					view="logic.hidden"
					parameters={{childClassName: "fmdb-form-element"}}
				/>
			</div>
		);
	}
);
