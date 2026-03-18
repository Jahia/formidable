import {Island, jahiaComponent} from "@jahia/javascript-modules-library";
import CheckboxGroup from "../Input/CheckboxGroup/CheckboxGroup.client";
import {parseOptions} from "./utils";

interface OptionGroupInputProps {
	"jcr:title"?: string;
	options: string[];
	required?: boolean;
	multiple?: boolean;
	disabled?: boolean;
}

jahiaComponent(
	{
		componentType: "view",
		nodeType: "fmdb:optionGroup",
		name: "inputGroup"
	},
	(
		{
			"jcr:title": label,
			options,
			required,
			multiple,
			disabled
		}: OptionGroupInputProps,
		{currentNode}
	) => {
		const groupName = currentNode.getName();
		const groupId = currentNode.getIdentifier();
		const parsedOptions = parseOptions(options);

		// Single option: standalone checkbox, no group wrapper needed
		if (parsedOptions.length === 1) {
			const option = parsedOptions[0];
			const inputId = `checkbox-${groupId}-${option.value}`;
			return (
				<div className="fmdb-form-group">
					<input
						type="checkbox"
						id={inputId}
						name={groupName}
						className="fmdb-form-control"
						value={option.value}
						defaultChecked={option.selected}
						disabled={disabled}
						required={required}
					/>
					{option.label && (
						<label htmlFor={inputId} className="fmdb-checkbox-label">
							{option.label}
							{required && <span className="fmdb-required-indicator">*</span>}
						</label>
					)}
				</div>
			);
		}

		// Multiple options + multiple=true: checkbox group (client-side validation)
		if (multiple) {
			return (
				<Island component={CheckboxGroup} props={{label, required}}>
					<div className="fmdb-group-items">
						{parsedOptions.map((option) => {
							const inputId = `checkbox-${groupId}-${option.value}`;
							return (
								<div key={option.value} className="fmdb-group-item">
									<input
										type="checkbox"
										id={inputId}
										name={groupName}
										className="fmdb-form-control"
										value={option.value}
										defaultChecked={option.selected}
										disabled={disabled}
									/>
									{option.label && (
										<label htmlFor={inputId} className="fmdb-checkbox-label">
											{option.label}
										</label>
									)}
								</div>
							);
						})}
					</div>
				</Island>
			);
		}

		// Multiple options + multiple=false: radio group (native HTML5 validation)
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

