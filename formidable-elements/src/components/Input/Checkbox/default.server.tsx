import {Island, jahiaComponent} from "@jahia/javascript-modules-library";
import Checkbox from "./Checkbox.client";
import {parseChoices} from "~/utils/choiceUtils";

interface CheckboxProps {
	"jcr:title"?: string;
	choices?: string[];
	required?: boolean;
}
jahiaComponent(
	{
		componentType: "view",
		nodeType: "fmdb:checkbox",
		name: "default"
	},
	(
		{"jcr:title": label, choices: rawChoices = [], required}: CheckboxProps,
		{currentNode}
	) => {
		const inputName = currentNode.getName();
		const nodeId = currentNode.getIdentifier();
		const parsedChoices = parseChoices(rawChoices);
		if (parsedChoices.length === 1) {
			const choice = parsedChoices[0];
			const inputId = `checkbox-${nodeId}`;
			return (
				<div className="fmdb-form-group">
					<input
						type="checkbox"
						id={inputId}
						name={inputName}
						className="fmdb-form-control"
						value={choice.value}
						defaultChecked={choice.selected}
						required={required}
					/>
					<label htmlFor={inputId} className="fmdb-checkbox-label">
						{choice.label}
						{required && <span className="fmdb-required-indicator">*</span>}
					</label>
				</div>
			);
		}
		return (
			<Island component={Checkbox} props={{ label, required }}>
				<div className="fmdb-group-items">
					{parsedChoices.map((choice, idx) => {
						const inputId = `checkbox-${nodeId}-${idx}`;
						return (
							<div key={choice.value || String(idx)} className="fmdb-group-item">
								<input
									type="checkbox"
									id={inputId}
									name={inputName}
									className="fmdb-form-control"
									value={choice.value}
									defaultChecked={choice.selected}
								/>
								<label htmlFor={inputId} className="fmdb-checkbox-label">
									{choice.label}
								</label>
							</div>
						);
					})}
				</div>
			</Island>
		);
	}
);
