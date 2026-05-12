import {jahiaComponent} from "@jahia/javascript-modules-library";
import LogicAwareRender from "~/components/Form/LogicAwareRender";

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
		const elements = Array.from(currentNode.getNodes());
		const initiallyHidden = currentResource.getModuleParams().get("initiallyHidden")?.toString() === "true";
		return (
			<div data-fmdb-step className="fmdb-step" style={initiallyHidden ? {display: 'none'} : undefined}>
				{intro && <div className="fmdb-step-intro" dangerouslySetInnerHTML={{__html: intro}}/>}
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

