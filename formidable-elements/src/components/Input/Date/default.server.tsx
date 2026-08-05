import {jahiaComponent} from "@jahia/javascript-modules-library";
import {type RangeValidationMessageProps, validationDataAttributes} from "formidable-ui-contract";
import {HelpText, helpTextId} from "formidable-ui-contract";

interface InputDateProps extends RangeValidationMessageProps {
	"jcr:title"?: string;
	helpText?: string;
	defaultValue?: string;
	min?: string;
	max?: string;
	step?: number;
	required?: boolean;
}

// Convert ISO date string to HTML date format (YYYY-MM-DD)
const formatDateForInput = (isoDate?: string): string | undefined => {
	if (!isoDate) return undefined;

	// Extract YYYY-MM-DD directly to avoid timezone shifting via Date object
	const match = isoDate.match(/^(\d{4}-\d{2}-\d{2})/);
	return match ? match[1] : undefined;
};

jahiaComponent(
	{
		componentType: "view",
		nodeType: "fmdb:inputDate",
		name: "default"
	},
	(
		{"jcr:title": label, helpText, defaultValue, min, max, step, required, ...validationMsgs}: InputDateProps,
		{currentNode}
	) => {

		// Generate unique id and name for the input
		const inputId = `input-${currentNode.getIdentifier()}`;
		const inputName = currentNode.getName();

		const helpId = helpText ? helpTextId(currentNode.getIdentifier()) : undefined;

		return (
			<div className="fmdb-form-group">
				{label && (
					<label htmlFor={inputId} className="fmdb-form-label">
						{label}
						{required && <span className="fmdb-required-indicator" aria-hidden="true">*</span>}
					</label>
				)}

				<HelpText id={helpId} text={helpText}/>

				<input
					type="date"
					id={inputId}
					name={inputName}
					aria-describedby={helpId}
					className="fmdb-form-control"
					defaultValue={formatDateForInput(defaultValue)}
					min={formatDateForInput(min)}
					max={formatDateForInput(max)}
					step={step}
					required={required}
					{...validationDataAttributes(validationMsgs)}
				/>
			</div>
		);
	}
);
