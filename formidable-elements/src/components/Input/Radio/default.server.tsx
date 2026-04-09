import {jahiaComponent} from "@jahia/javascript-modules-library";

interface InputRadioProps {
	"jcr:title"?: string;
	defaultChecked?: boolean;
	value: string;
}

jahiaComponent(
	{
		componentType: "view",
		nodeType: "fmdb:inputRadio",
		name: "default"
	},
	(
		{"jcr:title": label, defaultChecked, value}: InputRadioProps,
		{currentNode}
	) => {

		// Generate unique id
		const inputId = `input-${currentNode.getIdentifier()}`;

		// Get parent RadioGroup (required for radio inputs)
		const parent = currentNode.getParent();

		// Radio inputs must be inside a RadioGroup
		if (!parent || !parent.isNodeType("fmdb:radioGroup")) {
			return (
				<div className="fmdb-error">
					Radio inputs must be placed inside a Radio Group
				</div>
			);
		}

		// Get required from parent RadioGroup
		const finalRequired = parent.getProperty("required")?.getBoolean() || false;
		const finalGroupName = parent.getName();

		return (
			<>
				<input
					type="radio"
					id={inputId}
					name={finalGroupName}
					className="fmdb-form-control"
					defaultChecked={defaultChecked}
					value={value}
					required={finalRequired}
				/>
				{label && (
					<label htmlFor={inputId} className="fmdb-radio-label">
						{label}
					</label>
				)}
			</>
		)
	}
);
