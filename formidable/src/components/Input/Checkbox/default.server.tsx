import {jahiaComponent} from "@jahia/javascript-modules-library";

interface InputCheckboxProps {
	"jcr:title"?: string;
	defaultChecked?: boolean;
	required?: boolean;
	value?: string;
}

jahiaComponent(
	{
		componentType: "view",
		nodeType: "fmdb:inputCheckbox",
		name: "default"
	},
	(
		{"jcr:title": label, defaultChecked, required, value}: InputCheckboxProps,
		{currentNode}
	) => {

		// Generate unique id
		const inputId = `input-${currentNode.getIdentifier()}`;

		// Check if parent is a CheckboxGroup
		const parent = currentNode.getParent();
		const isInCheckboxGroup = parent && parent.isNodeType("fmdb:checkboxGroup");

		// finalRequired: false if in group (group handles validation), otherwise current node's required
		const finalRequired = isInCheckboxGroup ? false : (required || false);

		// finalName: parent name if in group, otherwise current node name
		const finalName = isInCheckboxGroup ? parent.getName() : currentNode.getName();

		// Mutualized checkbox input
		const checkboxInput = (
			<>
				<input
					type="checkbox"
					id={inputId}
					name={finalName}
					className="fmdb-form-control"
					defaultChecked={defaultChecked}
					value={value || "on"}
					required={finalRequired}
				/>
				{label && (
					<label htmlFor={inputId} className="fmdb-checkbox-label">
						{label}
						{/* Required indicator only shown if not in group - group handles its own indicator */}
						{!isInCheckboxGroup && finalRequired && <span className="fmdb-required-indicator">*</span>}
					</label>
				)}
			</>
		);


		// Simple render if inside CheckboxGroup (parent handles styling)
		if (isInCheckboxGroup) {
			return checkboxInput;
		}

		// Full render with form-group if standalone
		return (
			<div className="fmdb-form-group">
				{checkboxInput}
			</div>
		);
	}
);
