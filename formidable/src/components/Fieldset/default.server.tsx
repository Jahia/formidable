import {jahiaComponent, Render} from "@jahia/javascript-modules-library";

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
		const hasElements = elementNodes.length > 0;

		return (
			<fieldset className="fmdb-fieldset">
				{/* Fieldset title from mix:title */}
				{title && (
					<legend className="fmdb-fieldset-legend">
						{title}
					</legend>
				)}

				{/* Render form elements */}
				{hasElements ? (
					<div className="fmdb-fieldset-elements">
						{elementNodes.map((elementNode) => (
							<div key={elementNode.getIdentifier()} className="fmdb-form-element">
								<Render node={elementNode}/>
							</div>
						))}
					</div>
				) : (
					<div className="fmdb-fieldset-empty">
						<p>Aucun élément dans ce fieldset</p>
					</div>
				)}
			</fieldset>
		);
	}
);
