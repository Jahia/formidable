import {Island, jahiaComponent} from "@jahia/javascript-modules-library";
import FileInput from "./File.client";
import {type BaseValidationMessageProps, validationDataAttributes} from "~/utils/validationProps";
import HelpText, {helpTextId} from "~/design/HelpText";

interface InputFileProps extends BaseValidationMessageProps {
	"jcr:title"?: string;
	helpText?: string;
	accept?: string[];
	multiple?: boolean;
	required?: boolean;
}

jahiaComponent(
	{
		componentType: "view",
		nodeType: "fmdb:inputFile",
		name: "default"
	},
	(
		{"jcr:title": label, helpText, accept, multiple, required, ...validationMsgs}: InputFileProps,
		{currentNode}
	) => {
		// Generate unique id and name
		const inputId = `input-${currentNode.getIdentifier()}`;
		const inputName = currentNode.getName();

		const helpId = helpText ? helpTextId(currentNode.getIdentifier()) : undefined;

		return (
			<div className="fmdb-form-group">
				{label && (
					<label htmlFor={inputId} className="fmdb-file-label">
						{label}
						{required && <span className="fmdb-required-indicator" aria-hidden="true">*</span>}
					</label>
				)}
				<HelpText id={helpId} text={helpText}/>
				<Island
					component={FileInput}
					props={{
						inputId,
						inputName,
						accept,
						multiple,
						required,
						describedBy: helpId,
						validationAttributes: validationDataAttributes(validationMsgs)
					}}
				/>
			</div>
		);
	}
);
