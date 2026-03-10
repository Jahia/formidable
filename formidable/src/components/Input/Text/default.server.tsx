import {jahiaComponent} from "@jahia/javascript-modules-library";

interface InputTextProps {
	"jcr:title"?: string;
	placeholder?: string;
	defaultValue?: string;
	list?: string[];
	minLength?: number;
	maxLength?: number;
	// Advanced settings from mixin
	mask?: string;
	required?: boolean;
	autocomplete?: string;
	readonly?: boolean;
	autofocus?: boolean;
	disabled?: boolean;
	form?: string;
	dirname?: string;
	spellcheck?: boolean;
	pattern?: string;
	size?: number;
	title?: string;
}

// Default values declared outside component to prevent re-render issues
const DEFAULT_LIST: string[] = [];

// Convert mask to regex pattern for validation
const maskToPattern = (mask?: string): string | undefined => {
	if (!mask) return undefined;

	// Convert common mask characters to regex
	return mask
		.replace(/9/g, '[0-9]')      // 9 = digit
		.replace(/A/g, '[A-Za-z]')   // A = letter
		.replace(/\*/g, '[A-Za-z0-9]') // * = alphanumeric
		.replace(/\?/g, '.');         // ? = any character
};

jahiaComponent(
	{
		componentType: "view",
		nodeType: "fmdb:inputText",
		name: "default"
	},
	(
		{
			"jcr:title": label,
			placeholder,
			defaultValue,
			list = DEFAULT_LIST,
			mask,
			minLength,
			maxLength,
			required,
			autocomplete,
			readonly,
			autofocus,
			disabled,
			form,
			dirname,
			spellcheck = true,
			pattern: customPattern,
			size,
			title
		}: InputTextProps,
		{currentNode}
	) => {

		// Generate unique datalist ID for autocomplete functionality
		const generateDatalistId = () => `datalist-${currentNode.getIdentifier()}`;
		const datalistId = list.length > 0 ? generateDatalistId() : undefined;

		// Generate unique id and name for the input
		const inputId = `input-${currentNode.getIdentifier()}`;
		const inputName = currentNode.getName();

		// Use custom pattern if provided, otherwise use mask-derived pattern
		const finalPattern = customPattern || maskToPattern(mask);

		return (
			<div className="fmdb-form-group">
				{label && (
					<label htmlFor={inputId} className="fmdb-form-label">
						{label}
						{required && <span className="fmdb-required-indicator">*</span>}
					</label>
				)}

				<input
					type="text"
					id={inputId}
					name={inputName}
					className="fmdb-form-control"
					placeholder={placeholder}
					defaultValue={defaultValue}
					list={datalistId}
					minLength={minLength}
					maxLength={maxLength}
					pattern={finalPattern}
					required={required}
					data-mask={mask} // For optional client-side enhancement
					autoComplete={autocomplete}
					readOnly={readonly}
					autoFocus={autofocus}
					disabled={disabled}
					form={form}
					dir={dirname}
					spellCheck={spellcheck}
					size={size}
					title={title}
				/>

				{/* Render datalist for autocomplete if options are provided */}
				{list.length > 0 && (
					<datalist id={datalistId}>
						{list.map((option) => (
							<option key={option} value={option}/>
						))}
					</datalist>
				)}
			</div>
		);
	}
);
