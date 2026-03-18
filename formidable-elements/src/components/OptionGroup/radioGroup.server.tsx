import {jahiaComponent} from "@jahia/javascript-modules-library";
import {parseOptions} from "./utils";

interface OptionGroupRadioProps {
	"jcr:title"?: string;
	options: string[];
	required?: boolean;
	disabled?: boolean;
}

jahiaComponent(
	{
		componentType: "view",
		nodeType: "fmdb:optionGroup",
		name: "radioGroup"
	},
	(
		{
			"jcr:title": label,
			options,
			required,
			disabled
		}: OptionGroupRadioProps,
		{currentNode}
	) => {
		const groupName = currentNode.getName();
		const groupId = currentNode.getIdentifier();
		const parsedOptions = parseOptions(options);

		return (
			<fieldset className="fmdb-form-group fmdb-radio-group" disabled={disabled}>
				{label && (
					<legend className="fmdb-group-legend">
						{label}
						{required && <span className="fmdb-required-indicator">*</span>}
					</legend>
				)}
				<div className="fmdb-group-items">
					{parsedOptions.map((option) => {
						const inputId = `radio-${groupId}-${option.value}`;
						return (
							<div key={option.value} className="fmdb-group-item">
								<input
									type="radio"
									id={inputId}
									name={groupName}
									className="fmdb-form-control"
									value={option.value}
									defaultChecked={option.selected}
									required={required}
								/>
								{option.label && (
									<label htmlFor={inputId} className="fmdb-radio-label">
										{option.label}
									</label>
								)}
							</div>
						);
					})}
				</div>
			</fieldset>
		);
	}
);

