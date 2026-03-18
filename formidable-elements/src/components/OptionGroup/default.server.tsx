import {jahiaComponent} from "@jahia/javascript-modules-library";
import {parseOptions} from "./utils";

interface OptionGroupProps {
	"jcr:title"?: string;
	options: string[];
	required?: boolean;
	multiple?: boolean;
	size?: number;
	disabled?: boolean;
	autofocus?: boolean;
}

jahiaComponent(
	{
		componentType: "view",
		nodeType: "fmdb:optionGroup",
		name: "default"
	},
	(
		{
			"jcr:title": label,
			options,
			required,
			multiple,
			size,
			disabled,
			autofocus
		}: OptionGroupProps,
		{currentNode}
	) => {
		const selectId = `select-${currentNode.getIdentifier()}`;
		const selectName = currentNode.getName();
		const parsedOptions = parseOptions(options);
		const selectedValues = parsedOptions.filter(o => o.selected).map(o => o.value);
		const defaultValue = multiple ? selectedValues : (selectedValues[0] ?? undefined);

		return (
			<div className="fmdb-form-group">
				{label && (
					<label htmlFor={selectId} className="fmdb-form-label">
						{label}
						{required && <span className="fmdb-required-indicator">*</span>}
					</label>
				)}
				<select
					id={selectId}
					name={selectName}
					className="fmdb-form-control"
					required={required}
					multiple={multiple}
					size={size}
					disabled={disabled}
					autoFocus={autofocus}
					defaultValue={defaultValue}
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

