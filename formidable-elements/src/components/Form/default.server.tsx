import {
	AddResources,
	buildModuleFileUrl,
	getNodeProps,
	Island,
	jahiaComponent,
	Render,
} from "@jahia/javascript-modules-library";
import Form from "./Form.client";
import {type CaptchaProvider, type FormServerProps} from "./types";

const deriveProvider = (scriptUrl: string): CaptchaProvider => {
	if (scriptUrl.includes('challenges.cloudflare.com')) return 'turnstile';
	if (scriptUrl.includes('hcaptcha.com')) return 'hcaptcha';
	if (scriptUrl.includes('google.com/recaptcha')) return 'recaptcha_v2';
	return 'turnstile';
};

const ensureCaptchaExplicit = (url: string): string => {
	if (!url.includes('challenges.cloudflare.com')) return url;
	if (url.includes('render=explicit')) return url;
	return url + (url.includes('?') ? '&' : '?') + 'render=explicit';
};

jahiaComponent(
	{
		componentType: "view",
		nodeType: "fmdb:form",
		name: "default",
	},
	(
		{
			captcha: captchaNode,
			destination: destinationNode,
			intro,
			submissionMessage,
			errorMessage,
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

		const {siteKey, scriptUrl} = captchaNode
			? getNodeProps<{siteKey?: string; scriptUrl?: string}>(captchaNode, ['siteKey', 'scriptUrl'])
			: {};
		const captcha = siteKey && scriptUrl
			? {siteKey, provider: deriveProvider(scriptUrl)}
			: undefined;

		const isTransferMode = destinationNode?.isNodeType('fmdb:sendDataAction') ?? false;
		const destinationUrl = isTransferMode
			? getNodeProps<{targetUrl?: string}>(destinationNode!, ['targetUrl']).targetUrl
			: undefined;

		// In transfer mode, Jahia pipeline handles side effects only (captcha + destination are client-side).
		// In JCR mode, Jahia pipeline handles captcha, destination and side effects.
		const hasJahiaPipeline = isTransferMode
			? currentNode.hasProperty('actions')
			: (currentNode.hasProperty('captcha') || currentNode.hasProperty('destination') || currentNode.hasProperty('actions'));
		const submitActionUrl = hasJahiaPipeline
			? `/cms/render/live/${currentNode.getLanguage()}${currentNode.getPath()}.formidableSubmit.do`
			: undefined;

		return (
			<>
				{css && <style>{css}</style>}
				<AddResources type="css" resources={buildModuleFileUrl("dist/assets/style.css")} />
				{scriptUrl && (
				<AddResources
					type="javascript"
					resources={ensureCaptchaExplicit(scriptUrl)}
					defer
				/>
			)}
		<Island
			component={Form}
			props={{
				intro,
				submissionMessage,
				errorMessage,
				destinationUrl,
				submitActionUrl,
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
				captcha,
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
