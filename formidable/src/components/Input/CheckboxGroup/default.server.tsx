import {Island, jahiaComponent, Render} from "@jahia/javascript-modules-library";
import CheckboxGroup from "./CheckboxGroup.client";

interface CheckboxGroupProps {
	"jcr:title"?: string;
	required?: boolean;
}

jahiaComponent(
	{
		componentType: "view",
		nodeType: "fmdb:checkboxGroup",
		name: "default"
	},
	(
		{"jcr:title": label, required}: CheckboxGroupProps,
		{currentNode}
	) => {
		// Get all checkbox children
		const children = Array.from(currentNode.getNodes());

		// Render fieldset with legend if label exists
		return (
			<Island component={CheckboxGroup} props={{label, required}}>
				<div className="fmdb-group-items">
					{children.map((childNode) => (
						<div key={childNode.getIdentifier()} className="fmdb-group-item">
							<Render node={childNode}/>
						</div>
					))}
				</div>
			</Island>
		);
	}
);

