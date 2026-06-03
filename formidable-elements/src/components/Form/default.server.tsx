import {
	AddResources,
	buildModuleFileUrl,
	getNodeProps,
	Island,
	jahiaComponent,
	Render,
} from "@jahia/javascript-modules-library";
import Form from "./Form.client";
import {type FormServerProps} from "./types";


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
		{ currentNode, renderContext },
	) => {
		const fieldListNode = currentNode.getNode("fields");
		const formElements = fieldListNode ? Array.from(fieldListNode.getNodes()) : [];
		const formId = `form-${currentNode.getIdentifier()}`;

		const stepNodes = formElements.filter((el) => el.isNodeType("fmdb:step"));
		const stepLabels = stepNodes.length > 0
			? stepNodes.map((s, i) => {
				const {label, 'jcr:title': title} = getNodeProps<{label?: string; 'jcr:title'?: string}>(s, ['label', 'jcr:title']);
				return label ?? title ?? `Step ${i + 1}`;
			})
			: undefined;
		const stepIds = stepNodes.length > 0
			? stepNodes.map((s) => s.getIdentifier())
			: undefined;

		// Captcha config is injected as request attributes by CaptchaRenderFilter (Java)
		// when the fmdbmix:captcha mixin is applied to this form node.
		const hasCaptchaMixin = currentNode.isNodeType('fmdbmix:captcha');
		const siteKey     = renderContext.getRequest().getAttribute('formidable.captcha.siteKey') as string | null;
		const scriptUrl   = renderContext.getRequest().getAttribute('formidable.captcha.scriptUrl') as string | null;
		const widgetVar   = renderContext.getRequest().getAttribute('formidable.captcha.widgetVar') as string | null;
		const tokenField  = renderContext.getRequest().getAttribute('formidable.captcha.tokenField') as string | null;
		const captcha = siteKey && scriptUrl && widgetVar && tokenField
			? {siteKey, widgetVar, tokenField}
			: undefined;

		if (hasCaptchaMixin && !captcha) {
			console.warn(`[Formidable] fmdbmix:captcha is applied on form '${currentNode.getPath()}' but CAPTCHA is not fully configured (siteKey, scriptUrl, widgetVar or tokenField missing). The widget will not be rendered.`);
		}

		const isSubmitDisabled = renderContext.isEditMode() || renderContext.isPreviewMode();
		const submitActionUrl = `/modules/formidable-engine/form-submit?fid=${currentNode.getIdentifier()}&lang=${currentNode.getLanguage()}`;

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
				submitActionUrl,
				isSubmitDisabled,
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
				stepIds,
				captcha,
			}}
			>
				{fieldListNode && (
					<Render
						node={fieldListNode}
						view="logic.hidden"
						parameters={{
							preferCompactStepView: showStepsNav ? "true" : "false",
							hideStepsAfterFirst: showStepsNav ? "true" : "false",
							childView: "default",
						}}
					/>
				)}
				</Island>
			</>
		);
	},
);
