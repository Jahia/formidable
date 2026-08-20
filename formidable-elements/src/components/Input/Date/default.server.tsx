import {Island, jahiaComponent} from "@jahia/javascript-modules-library";
import {type RangeValidationMessageProps, validationDataAttributes} from "formidable-shared";
import {HelpText, helpTextId} from "formidable-shared";
import TodayBoundedInput from "./TodayBoundedInput.client";
import {resolveBound} from "./bounds";

interface InputDateProps extends RangeValidationMessageProps {
	"jcr:title"?: string;
	helpText?: string;
	defaultValue?: string;
	"fmdb:minBoundMode"?: string;
	"fmdb:maxBoundMode"?: string;
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
		{
			"jcr:title": label,
			helpText,
			defaultValue,
			"fmdb:minBoundMode": minBoundMode,
			"fmdb:maxBoundMode": maxBoundMode,
			min,
			max,
			step,
			required,
			...validationMsgs
		}: InputDateProps,
		{currentNode}
	) => {

		// Generate unique id and name for the input
		const inputId = `input-${currentNode.getIdentifier()}`;
		const inputName = currentNode.getName();

		const helpId = helpText ? helpTextId(currentNode.getIdentifier()) : undefined;

		const minBound = resolveBound(minBoundMode, formatDateForInput(min));
		const maxBound = resolveBound(maxBoundMode, formatDateForInput(max));

		return (
			<div className="fmdb-form-group">
				{label && (
					<label htmlFor={inputId} className="fmdb-form-label">
						{label}
						{required && <span className="fmdb-required-indicator" aria-hidden="true">*</span>}
					</label>
				)}

				<HelpText id={helpId} text={helpText}/>

				{minBound.today || maxBound.today ? (
					// A bound relative to the submission day cannot be a server-rendered
					// attribute (the fragment cache would freeze it): the input becomes
					// an island resolving it at hydration, in the visitor's timezone.
					<Island
						component={TodayBoundedInput}
						props={{
							type: "date",
							inputId,
							name: inputName,
							helpId,
							defaultValue: formatDateForInput(defaultValue),
							min: minBound.fixed,
							max: maxBound.fixed,
							minToday: minBound.today,
							maxToday: maxBound.today,
							step,
							required,
							validationAttributes: validationDataAttributes(validationMsgs)
						}}
					/>
				) : (
					<input
						type="date"
						id={inputId}
						name={inputName}
						aria-describedby={helpId}
						className="fmdb-form-control"
						defaultValue={formatDateForInput(defaultValue)}
						min={minBound.fixed}
						max={maxBound.fixed}
						step={step}
						required={required}
						{...validationDataAttributes(validationMsgs)}
					/>
				)}
			</div>
		);
	}
);
