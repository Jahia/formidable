import {jahiaComponent, Render} from "@jahia/javascript-modules-library";

interface RadioGroupProps {
	"jcr:title"?: string;
	required?: boolean;
}

jahiaComponent(
	{
		componentType: "view",
		nodeType: "fmdb:radioGroup",
		name: "default"
	},
	(
		{"jcr:title": label, required}: RadioGroupProps,
		{currentNode}
	) => {
		// Get all radio children
		const children = Array.from(currentNode.getNodes());

		// Render fieldset with legend if label exists
		return (
			<fieldset className="fmdb-form-group fmdb-radio-group">
				{label && (
					<legend className="fmdb-group-legend">
						{label}
						{required && <span className="fmdb-required-indicator">*</span>}
					</legend>
				)}
				<div className="fmdb-group-items">
					{children.map((childNode) => (
						<div key={childNode.getIdentifier()} className="fmdb-group-item">
							<Render node={childNode}/>
						</div>
					))}
				</div>
			</fieldset>
		);
	}
);

