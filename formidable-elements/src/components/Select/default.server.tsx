import {jahiaComponent} from "@jahia/javascript-modules-library";
import {parseChoices} from "~/utils/choiceUtils";
import {type BaseValidationMessageProps, validationDataAttributes} from "formidable-shared";
import {HelpText, helpTextId} from "formidable-shared";

interface SelectProps extends BaseValidationMessageProps {
	"jcr:title"?: string;
	helpText?: string;
	options?: string[];
	required?: boolean;
	multiple?: boolean;
	size?: number;
	disabled?: boolean;
	autofocus?: boolean;
}


jahiaComponent(
	{
		componentType: "view",
		nodeType: "fmdb:select",
		name: "default"
	},
	(
		{
			"jcr:title": label,
			helpText,
			options = [],
			required,
			multiple,
			size,
			disabled,
			autofocus,
			...validationMsgs
		}: SelectProps,
		{currentNode}
	) => {

		const selectId = `select-${currentNode.getIdentifier()}`;
		const selectName = currentNode.getName();

		const parsedOptions = parseChoices(options);

		const selectedValues = parsedOptions.filter(o => o.selected).map(o => o.value);
		const defaultValue = multiple ? selectedValues : (selectedValues[0] ?? undefined);

		const helpId = helpText ? helpTextId(currentNode.getIdentifier()) : undefined;

		return (
			<div className="fmdb-form-group">
				{label && (
					<label htmlFor={selectId} className="fmdb-form-label">
						{label}
						{required && <span className="fmdb-required-indicator" aria-hidden="true">*</span>}
					</label>
				)}

				<HelpText id={helpId} text={helpText}/>

				<select
					id={selectId}
					name={selectName}
					aria-describedby={helpId}
					className="fmdb-form-control"
					required={required}
					multiple={multiple}
					size={size}
					disabled={disabled}
					autoFocus={autofocus}
					defaultValue={defaultValue}
					{...validationDataAttributes(validationMsgs)}
				>
					{parsedOptions.map((option) => (
						<option
							key={option.value || option.label}
							value={option.value}
						>
							{option.label}
						</option>
					))}
				</select>
			</div>
		);
	}
);
