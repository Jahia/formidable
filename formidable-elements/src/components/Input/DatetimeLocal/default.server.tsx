import {jahiaComponent} from "@jahia/javascript-modules-library";

interface InputDatetimeLocalProps {
	"jcr:title"?: string;
	defaultValue?: string;
	min?: string;
	max?: string;
	step?: number;
	required?: boolean;
}

// Convert ISO datetime string to HTML datetime-local format (YYYY-MM-DDTHH:mm)
const formatDatetimeForInput = (isoDatetime?: string): string | undefined => {
	if (!isoDatetime) return undefined;

	try {
		// Parse ISO datetime and format for datetime-local input
		const date = new Date(isoDatetime);
		// Get local datetime in YYYY-MM-DDTHH:mm format
		const year = date.getFullYear();
		const month = String(date.getMonth() + 1).padStart(2, '0');
		const day = String(date.getDate()).padStart(2, '0');
		const hours = String(date.getHours()).padStart(2, '0');
		const minutes = String(date.getMinutes()).padStart(2, '0');

		return `${year}-${month}-${day}T${hours}:${minutes}`;
	} catch {
		// If parsing fails, return undefined to let browser handle invalid datetimes
		return undefined;
	}
};

jahiaComponent(
	{
		componentType: "view",
		nodeType: "fmdb:inputDatetimeLocal",
		name: "default"
	},
	(
		{"jcr:title": label, defaultValue, min, max, step, required}: InputDatetimeLocalProps,
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
					type="datetime-local"
					id={inputId}
					name={inputName}
					className="fmdb-form-control"
					defaultValue={formatDatetimeForInput(defaultValue)}
					min={formatDatetimeForInput(min)}
					max={formatDatetimeForInput(max)}
					step={step}
					required={required}
				/>
			</div>
		);
	}
);
