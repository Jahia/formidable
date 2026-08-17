import {
	AddResources,
	buildModuleFileUrl,
	getNodeProps,
	Island,
	jahiaComponent,
	Render,
	server,
} from "@jahia/javascript-modules-library";
import type {JCRNodeWrapper} from "org.jahia.services.content";
import Form from "./Form.client";
import {type FormServerProps} from "./types";
import {resolveUrlPlaceholders} from "~/utils/richTextUtils";

const SETTINGS_BEAN_SERVICE = "org.jahia.api.settings.SettingsBean";
// Absence of the mixin = the action is presumed to write to the repository (the
// declaration is inverted on purpose; see the engine CND). The submission pipeline
// reads the same mixin on the same nodes, so render and submit cannot disagree.
const READ_ONLY_COMPATIBLE_ACTION_MIXIN = "fmdbmix:readOnlyCompatibleAction";

// Fail-open: if the platform state cannot be read, render the form normally — the
// submission pipeline remains the correctness boundary (FMDB-014).
const isPlatformReadOnly = (): boolean => {
	try {
		const settings = server.osgi.getService(SETTINGS_BEAN_SERVICE);
		return Boolean(settings.isReadOnlyMode()) || Boolean(settings.isFullReadOnlyMode());
	} catch (error) {
		console.error("[Formidable] Could not read the platform read-only state", error);
		return false;
	}
};

const hasRepositoryWritingAction = (formNode: JCRNodeWrapper): boolean => {
	try {
		const actionListNode = formNode.getNode("actions");
		if (!actionListNode) return false;
		return Array.from(actionListNode.getNodes())
			.some((action) => !action.isNodeType(READ_ONLY_COMPATIBLE_ACTION_MIXIN));
	} catch (error) {
		console.error(`[Formidable] Could not inspect the actions of form ${formNode.getPath()}`, error);
		// Fail-closed on the scoping side: an unreadable action list is presumed writing.
		return true;
	}
};


const ensureCaptchaExplicit = (url: string): string => {
	// Providers whose script auto-renders at load unless render=explicit is set.
	// The widget is always rendered explicitly by Captcha.client, so opt out of auto-render.
	if (!url.includes('challenges.cloudflare.com') && !url.includes('google.com/recaptcha') && !url.includes('recaptcha.net')) return url;
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
		// Defensive parsing: the attribute is a Java host value; anything that does not
		// coerce to a finite positive number must not reach the client (NaN would break
		// the poll deadline there).
		const rawWidgetTimeout = Number(renderContext.getRequest().getAttribute('formidable.captcha.widgetTimeoutSeconds'));
		const widgetTimeoutSeconds = Number.isFinite(rawWidgetTimeout) && rawWidgetTimeout > 0 ? rawWidgetTimeout : undefined;
		const captcha = siteKey && scriptUrl && widgetVar && tokenField
			? {siteKey, widgetVar, tokenField, widgetTimeoutSeconds}
			: undefined;

		if (hasCaptchaMixin && !captcha) {
			console.warn(`[Formidable] fmdbmix:captcha is applied on form '${currentNode.getPath()}' but CAPTCHA is not fully configured (siteKey, scriptUrl, widgetVar or tokenField missing). The widget will not be rendered.`);
		}

		const isSubmitDisabled = renderContext.isEditMode() || renderContext.isPreviewMode();
		const submitActionUrl = `/modules/formidable-engine/form-submit?fid=${currentNode.getIdentifier()}&lang=${currentNode.getLanguage()}`;

		// Maintenance state: only for forms whose actions write to the repository, and only
		// in live (contributors keep seeing the real form in edit/preview). Render-time
		// detection is a UX layer — cached fragments can be stale in both directions, so the
		// maintenance fragment gets a short TTL and the submission pipeline stays authoritative.
		const isMaintenance = !isSubmitDisabled && isPlatformReadOnly() && hasRepositoryWritingAction(currentNode);
		if (isMaintenance) {
			// The Java request exposes setAttribute; the TS stub only types the getters.
			(renderContext.getRequest() as unknown as {setAttribute(name: string, value: string): void})
				.setAttribute("expiration", "60");
		}

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
				// Rich text island props: resolve Jahia URL placeholders that the
				// HTML-level rewriting cannot reach inside serialized props
				intro: resolveUrlPlaceholders(intro, renderContext),
				submissionMessage: resolveUrlPlaceholders(submissionMessage, renderContext),
				errorMessage: resolveUrlPlaceholders(errorMessage, renderContext),
				submitActionUrl,
				isSubmitDisabled,
				isMaintenance,
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
						view="hidden.logic"
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
