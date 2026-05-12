import {jahiaComponent} from "@jahia/javascript-modules-library";
import LogicAwareRender from "~/components/Form/LogicAwareRender";

interface FieldsetProps {
	"jcr:title"?: string;
}

jahiaComponent(
	{
		componentType: "view",
		nodeType: "fmdb:fieldset",
		name: "default"
	},
	(
		{"jcr:title": title}: FieldsetProps,
		{currentNode}
	) => {
		// Convert NodeIterator to Array for fieldset elements
		const elementNodes = Array.from(currentNode.getNodes());

		return (
			<fieldset className="fmdb-fieldset">
				{/* Fieldset title from mix:title */}
				{title && (
					<legend className="fmdb-fieldset-legend">
						{title}
					</legend>
				)}

				{/* Render form elements */}
				{elementNodes.length > 0 && (
					<div className="fmdb-fieldset-elements">
						{elementNodes.map((elementNode) => (
							<LogicAwareRender
								key={elementNode.getIdentifier()}
								node={elementNode}
								className="fmdb-form-element"
							/>
						))}
					</div>
				)}
			</fieldset>
		);
	}
);
