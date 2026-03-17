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

// Mask tokens aligned with useMask hook
const MASK_TOKEN_PATTERNS: Record<string, string> = {
	'9': '[0-9]',
	'A': '[A-Za-z]',
	'a': '[A-Za-z]',
	'X': '[A-Za-z0-9]',
	'x': '[A-Za-z0-9]'
};

// Convert mask to a regex pattern string suitable for <input pattern>
const maskToPattern = (mask?: string): string | undefined => {
	if (!mask) return undefined;

	const pattern = Array.from(mask).map(char => {
		const token = MASK_TOKEN_PATTERNS[char];
		if (token) return token;
		// Escape fixed characters so they match literally in the regex
		return char.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
	}).join('');

	return `^${pattern}$`;
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
