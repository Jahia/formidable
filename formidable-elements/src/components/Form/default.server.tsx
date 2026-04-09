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
			previousBtnLabel,
			nextBtnLabel,
			css
		}: FormServerProps,
		{currentNode}
	) => {
		const formElements = Array.from(currentNode.getNodes());
		const formId = `form-${currentNode.getIdentifier()}`;

		const stepNodes = formElements.filter(el => el.isNodeType('fmdb:step'));
		const stepLabels = stepNodes.length > 0
			? stepNodes.map((s, i) => s.getProperty('jcr:title')?.getString() ?? `Step ${i + 1}`)
			: undefined;

		return (
			<>
				{css && <style>{css}</style>}
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
						previousBtnLabel,
						nextBtnLabel,
						formId,
						locale: currentNode.getLanguage(),
						stepLabels,
					}}
				>
					{formElements.map((element) => (
						<Render key={element.getIdentifier()} node={element}/>
					))}
				</Island>
			</>
		);
	}
);
