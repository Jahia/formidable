import {jahiaComponent} from "@jahia/javascript-modules-library";
import {type RangeValidationMessageProps, validationDataAttributes} from "~/utils/validationProps";
import HelpText, {helpTextId} from "~/design/HelpText";

interface InputNumberProps extends RangeValidationMessageProps {
	"jcr:title"?: string;
	helpText?: string;
	placeholder?: string;
	defaultValue?: number;
	minValue?: number;
	maxValue?: number;
	step?: number;
	required?: boolean;
}

jahiaComponent(
	{
		componentType: "view",
		nodeType: "fmdb:inputNumber",
		name: "default"
	},
	(
		{
			"jcr:title": label,
			helpText,
			placeholder,
			defaultValue,
			minValue,
			maxValue,
			step,
			required,
			...validationMsgs
		}: InputNumberProps,
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
					type="number"
					id={inputId}
					name={inputName}
					aria-describedby={helpId}
					className="fmdb-form-control"
					placeholder={placeholder}
					defaultValue={defaultValue}
					min={minValue}
					max={maxValue}
					step={step}
					required={required}
					{...validationDataAttributes(validationMsgs)}
				/>
			</div>
		);
	}
);
