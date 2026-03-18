import {Island, jahiaComponent} from "@jahia/javascript-modules-library";
import CheckboxGroup from "../Input/CheckboxGroup/CheckboxGroup.client";
import {parseOptions} from "./utils";

interface OptionGroupCheckboxProps {
	"jcr:title"?: string;
	options: string[];
	required?: boolean;
	disabled?: boolean;
}

jahiaComponent(
	{
		componentType: "view",
		nodeType: "fmdb:optionGroup",
		name: "checkboxGroup"
	},
	(
		{
			"jcr:title": label,
			options,
			required,
			disabled
		}: OptionGroupCheckboxProps,
		{currentNode}
	) => {
		const groupName = currentNode.getName();
		const groupId = currentNode.getIdentifier();
		const parsedOptions = parseOptions(options);

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
);

