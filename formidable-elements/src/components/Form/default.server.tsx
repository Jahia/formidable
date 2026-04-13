import {
	AddResources,
	buildModuleFileUrl,
	getNodeProps,
	Island,
	jahiaComponent,
	Render,
} from "@jahia/javascript-modules-library";
import Form from "./Form.client";
import { type FormServerProps } from "./types";

jahiaComponent(
	{
		componentType: "view",
		nodeType: "fmdb:form",
		name: "default",
	},
	(
		{
			captchaConfig,
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
			showStepsNav,
			css,
		}: FormServerProps,
		{ currentNode },
	) => {
		const formElements = Array.from(currentNode.getNodes());
		const formId = `form-${currentNode.getIdentifier()}`;

		const stepNodes = formElements.filter((el) => el.isNodeType("fmdb:step"));
		const stepLabels = stepNodes.length > 0
			? stepNodes.map((s, i) => {
				const {label, 'jcr:title': title} = getNodeProps<{label?: string; 'jcr:title'?: string}>(s, ['label', 'jcr:title']);
				return label ?? title ?? `Step ${i + 1}`;
			})
			: undefined;

		const {siteKey: captchaSiteKey, scriptUrl: captchaScriptUrl} = captchaConfig
			? getNodeProps<{siteKey?: string; scriptUrl?: string}>(captchaConfig, ['siteKey', 'scriptUrl'])
			: {};

		return (
			<>
				{css && <style>{css}</style>}
				<AddResources type="css" resources={buildModuleFileUrl("dist/assets/style.css")} />
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
						showStepsNav,
					formId,
					locale: currentNode.getLanguage(),
					stepLabels,
					captchaSiteKey,
					captchaScriptUrl,
					}}
				>
					{formElements.map((element) => {
						const isStep = element.isNodeType("fmdb:step");
						const stepIndex = isStep
							? stepNodes.findIndex((s) => s.getIdentifier() === element.getIdentifier())
							: -1;
						const {['j:view']: nodeView} = getNodeProps<{'j:view'?: string}>(element, ['j:view']);
						const fallbackView = isStep && showStepsNav ? "compact" : "default";
						return (
							<Render
								key={element.getIdentifier()}
								node={element}
								view={nodeView ?? fallbackView}
								parameters={isStep && stepIndex > 0 ? {initiallyHidden: "true"} : undefined}
							/>
						);
					})}
				</Island>
			</>
		);
	},
);
