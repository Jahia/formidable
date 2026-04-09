import {jahiaComponent} from "@jahia/javascript-modules-library";

interface InputEmailProps {
	"jcr:title"?: string;
	placeholder?: string;
	defaultValue?: string;
	list?: string[];
	pattern?: string;
	minLength?: number;
	maxLength?: number;
	required?: boolean;
	autocomplete?: string;
	multiple?: boolean;
}

// Default values declared outside component to prevent re-render issues
const DEFAULT_LIST: string[] = [];

jahiaComponent(
	{
		componentType: "view",
		nodeType: "fmdb:inputEmail",
		name: "default"
	},
	(
		{
			"jcr:title": label,
			placeholder,
			defaultValue,
			list = DEFAULT_LIST,
			pattern,
			minLength,
			maxLength,
			required,
			autocomplete,
			multiple
		}: InputEmailProps,
		{currentNode}
	) => {


		// Generate unique datalist ID for autocomplete functionality
		const generateDatalistId = () => `datalist-${currentNode.getIdentifier()}`;
		const datalistId = list.length > 0 ? generateDatalistId() : undefined;

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
					type="email"
					id={inputId}
					name={inputName}
					className="fmdb-form-control"
					placeholder={placeholder}
					defaultValue={defaultValue}
					list={datalistId}
					pattern={pattern}
					minLength={minLength}
					maxLength={maxLength}
					required={required}
					multiple={multiple}
					autoComplete={autocomplete}
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
