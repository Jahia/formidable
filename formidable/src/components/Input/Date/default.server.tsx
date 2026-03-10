import {jahiaComponent} from "@jahia/javascript-modules-library";

interface InputDateProps {
	"jcr:title"?: string;
	defaultValue?: string;
	min?: string;
	max?: string;
	step?: number;
	required?: boolean;
}

// Convert ISO date string to HTML date format (YYYY-MM-DD)
const formatDateForInput = (isoDate?: string): string | undefined => {
	if (!isoDate) return undefined;

	try {
		// Parse ISO date and extract only the date part
		const date = new Date(isoDate);
		return date.toISOString().split('T')[0];
	} catch {
		// If parsing fails, return undefined to let browser handle invalid dates
		return undefined;
	}
};

jahiaComponent(
	{
		componentType: "view",
		nodeType: "fmdb:inputDate",
		name: "default"
	},
	(
		{"jcr:title": label, defaultValue, min, max, step, required}: InputDateProps,
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
					type="date"
					id={inputId}
					name={inputName}
					className="fmdb-form-control"
					defaultValue={formatDateForInput(defaultValue)}
					min={formatDateForInput(min)}
					max={formatDateForInput(max)}
					step={step}
					required={required}
				/>
			</div>
		);
	}
);
