import {AddResources, buildModuleFileUrl, Island, jahiaComponent, Render} from "@jahia/javascript-modules-library";
import Form from "./Form.client";
import {type FormServerProps} from './types';

jahiaComponent(
	{
		componentType: "view",
		nodeType: "fmdb:form",
		name: "default"
	},
	(
		{
			intro,
			submissionMessage,
			errorMessage,
			customTarget,
			showResetBtn = false,
			showNewFormBtn = false,
			showTryAgainBtn = false,
			submitBtnLabel,
			resetBtnLabel,
			newFormBtnLabel,
			tryAgainBtnLabel,
			css
		}: FormServerProps,
		{currentNode}
	) => {
		// Get all form elements (all children are guaranteed to be fmdbmix:formElement)
		const formElements = Array.from(currentNode.getNodes());
		const formId = `form-${currentNode.getIdentifier()}`;

		return (
			<>
				{/* Add custom CSS if provided */}
				{css && <style>{css}</style>}
				{/* Add module CSS */}
				<AddResources type="css" resources={buildModuleFileUrl("dist/assets/style.css")}/>
				<Island
					component={Form}
					props={{
						intro,
						submissionMessage,
						errorMessage,
						customTarget,
						showResetBtn,
						showNewFormBtn,
						showTryAgainBtn,
						submitBtnLabel,
						resetBtnLabel,
						newFormBtnLabel,
						tryAgainBtnLabel,
						formId,
						locale: currentNode.getLanguage(),
					}}
				>
					{/* Render all form elements in order */}
					{formElements.map((element) => (
						<Render key={element.getIdentifier()} node={element}/>
					))}
				</Island>
			</>
		);
	}
);
