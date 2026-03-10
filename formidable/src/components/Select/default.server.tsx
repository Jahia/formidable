import {jahiaComponent} from "@jahia/javascript-modules-library";

interface SelectOption {
	value: string;
	label: string;
	selected?: boolean;
}

interface SelectProps {
	"jcr:title"?: string;
	options?: string[];
	required?: boolean;
	multiple?: boolean;
	size?: number;
	disabled?: boolean;
	autofocus?: boolean;
}

// Default values declared outside component to prevent re-render issues
const DEFAULT_OPTIONS: string[] = [];

// Parse JSON options with fallback for invalid JSON
const parseOptions = (options: string[] = []): SelectOption[] => {
	return options.map(option => {
		try {
			const parsed = JSON.parse(option);
			if (parsed && typeof parsed.value === 'string' && typeof parsed.label === 'string') {
				return {
					value: parsed.value,
					label: parsed.label,
					selected: parsed.selected === true
				};
			}
		} catch (error) {
			console.error(`Failed to parse option JSON: ${option}`, error);
			// Fallback: treat as simple string option with error indicator
			return {
				value: "",
				label: `Invalid JSON: ${option}`
			};
		}
		// Final fallback: valid JSON but wrong structure (e.g., numbers, booleans, missing properties)
		// This ensures no option is lost and allows admin to see and fix malformed options
		return {value: "", label: `Malformed option structure - Expected {value, label, selected}: ${option}`};
	});
};

jahiaComponent(
	{
		componentType: "view",
		nodeType: "fmdb:select",
		name: "default"
	},
	(
		{
			"jcr:title": label,
			options = DEFAULT_OPTIONS,
			required,
			multiple,
			size,
			disabled,
			autofocus
		}: SelectProps,
		{currentNode}
	) => {

		// Generate unique id and name for the select
		const selectId = `select-${currentNode.getIdentifier()}`;
		const selectName = currentNode.getName();

		const parsedOptions = parseOptions(options);

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
				>
					{parsedOptions.map((option) => (
						<option
							key={option.value || option.label}
							value={option.value}
							selected={option.selected}
						>
							{option.label}
						</option>
					))}
				</select>
			</div>
		);
	}
);
