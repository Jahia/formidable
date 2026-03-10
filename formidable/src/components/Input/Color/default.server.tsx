import {jahiaComponent} from "@jahia/javascript-modules-library";

interface InputColorProps {
	"jcr:title"?: string;
	defaultValue?: string;
	alpha?: boolean;
	colorspace?: string;
	required?: boolean;
}

jahiaComponent(
	{
		componentType: "view",
		nodeType: "fmdb:inputColor",
		name: "default"
	},
	(
		{
			"jcr:title": label,
			defaultValue,
			// alpha, //Not standard in HTML5 input type color only supported in ios safari
			// colorspace, //Not standard in HTML5 input type color only supported in ios safari
			required
		}: InputColorProps,
		{currentNode}
	) => {

		// Generate unique id and name for the input
		const inputId = `input-${currentNode.getIdentifier()}`;
		const inputName = currentNode.getName();

		return (
			<div className="fmdb-form-group">
				{label && (
					<label htmlFor={inputId} className="fmdb-form-label">
						{label}
						{required && <span className="fmdb-required-indicator">*</span>}
					</label>
				)}

				<input
					type="color"
					id={inputId}
					name={inputName}
					className="fmdb-form-control"
					defaultValue={defaultValue}
					// alpha={alpha}
					// colorspace={colorspace}
					required={required}
				/>
			</div>
		);
	}
);
