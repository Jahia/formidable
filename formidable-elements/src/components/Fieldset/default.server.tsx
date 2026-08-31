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
		{currentNode, currentResource}
	) => {
		const showLogicHidden = currentResource.getModuleParams().get("showLogicHidden")?.toString() === "true";

		return (
			<fieldset className="fmdb-fieldset">
				{/* Fieldset title from mix:title */}
				{title && (
					<legend className="fmdb-fieldset-legend">
						{title}
					</legend>
				)}

				{/* Always rendered, children or not: hidden.logic is what emits the Page
				    Builder placeholder, and an empty fieldset is exactly when the create
				    buttons are indispensable — without them nothing can ever go in. */}
				<Render
					node={currentNode}
					view="hidden.logic"
					readOnly
					parameters={{
						className: "fmdb-fieldset-elements",
						childClassName: "fmdb-form-element",
						...(showLogicHidden ? {showLogicHidden: "true"} : {}),
					}}
				/>
			</fieldset>
		);
	}
);
