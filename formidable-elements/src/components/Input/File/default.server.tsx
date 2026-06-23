import {Island, jahiaComponent} from "@jahia/javascript-modules-library";
import FileInput from "./File.client";
import {type BaseValidationMessageProps, validationDataAttributes} from "~/utils/validationProps";

interface InputFileProps extends BaseValidationMessageProps {
	"jcr:title"?: string;
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
		{"jcr:title": label, accept, multiple, required, ...validationMsgs}: InputFileProps,
		{currentNode}
	) => {
		// Generate unique id and name
		const inputId = `input-${currentNode.getIdentifier()}`;
		const inputName = currentNode.getName();

		return (
			<div className="fmdb-form-group">
				{label && (
					<label htmlFor={inputId} className="fmdb-file-label">
						{label}
						{required && <span className="fmdb-required-indicator" aria-hidden="true">*</span>}
					</label>
				)}
				<Island
					component={FileInput}
					props={{
						inputId,
						inputName,
						accept,
						multiple,
						required,
						validationAttributes: validationDataAttributes(validationMsgs)
					}}
				/>
			</div>
		);
	}
);
