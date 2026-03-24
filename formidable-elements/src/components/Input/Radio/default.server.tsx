import {jahiaComponent} from "@jahia/javascript-modules-library";
import {parseChoices} from "~/utils/choiceUtils";

interface RadiosProps {
	"jcr:title"?: string;
	choices?: string[];
	required?: boolean;
}

jahiaComponent(
	{
		componentType: "view",
		nodeType: "fmdb:radio",
		name: "default"
	},
	(
		{"jcr:title": label, choices: rawChoices = [], required}: RadiosProps,
		{currentNode}
	) => {
		const inputName = currentNode.getName();
		const nodeId = currentNode.getIdentifier();
		const parsedChoices = parseChoices(rawChoices);

		if (parsedChoices.length === 1) {
			const choice = parsedChoices[0];
			const inputId = `radio-${nodeId}`;
			return (
				<div className="fmdb-form-group">
					<input
						type="radio"
						id={inputId}
						name={inputName}
						className="fmdb-form-control"
						value={choice.value}
						defaultChecked={choice.selected}
						required={required}
					/>
					<label htmlFor={inputId} className="fmdb-radio-label">
						{choice.label}
						{required && <span className="fmdb-required-indicator">*</span>}
					</label>
				</div>
			);
		}

		return (
			<fieldset className="fmdb-form-group fmdb-radio-group">
				{label && (
					<legend className="fmdb-group-legend">
						{label}
						{required && <span className="fmdb-required-indicator">*</span>}
					</legend>
				)}
				<div className="fmdb-group-items">
					{parsedChoices.map((choice, idx) => {
						const inputId = `radio-${nodeId}-${idx}`;
						return (
							<div key={choice.value || String(idx)} className="fmdb-group-item">
								<input
									type="radio"
									id={inputId}
									name={inputName}
									className="fmdb-form-control"
									value={choice.value}
									defaultChecked={choice.selected}
									required={required}
								/>
								<label htmlFor={inputId} className="fmdb-radio-label">
									{choice.label}
								</label>
							</div>
						);
					})}
				</div>
			</fieldset>
		);
	}
);
