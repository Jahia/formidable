import {jahiaComponent} from "@jahia/javascript-modules-library";
import LogicAwareRender from "~/components/Form/LogicAwareRender";

interface StepProps {
	'jcr:title'?: string;
	intro?: string;
}

jahiaComponent(
	{
		componentType: "view",
		nodeType: "fmdb:step",
		name: "default",
		displayName: "Full"
	},
	({'jcr:title': title, intro}: StepProps, {currentNode, currentResource}) => {
		const elements = Array.from(currentNode.getNodes());
		const initiallyHidden = currentResource.getModuleParams().get("initiallyHidden")?.toString() === "true";
		return (
			<div data-fmdb-step className="fmdb-step" style={initiallyHidden ? {display: 'none'} : undefined}>
				{title && <h2 className="fmdb-step-title">{title}</h2>}
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
	},
);
