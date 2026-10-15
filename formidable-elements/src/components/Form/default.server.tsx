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
import type {RenderContext} from "org.jahia.services.render";
import {useTranslation} from "react-i18next";
import AlertIcon from "~/design/AlertIcon";
import LockIcon from "~/design/LockIcon";
import ShieldCheckIcon from "~/design/ShieldCheckIcon";
import Form from "./Form.client";
import {type FormServerProps} from "./types";
import {resolveUrlPlaceholders, styleTagCss} from "~/utils/richTextUtils";

const SETTINGS_BEAN_SERVICE = "org.jahia.api.settings.SettingsBean";
const CAPTCHA_CONFIG_SERVICE = "org.jahia.modules.formidable.engine.config.captcha.CaptchaConfigService";
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

/**
 * Whether the captcha of the instance is set up end to end, the widget and its server-side verification, as
 * the engine reads it from its captcha configuration. Asked while authoring only, to warn the contributor of
 * a form that cannot be sent: preview and live read the widget's settings off the request attributes
 * CaptchaRenderFilter sets. A setup that cannot be read counts as complete — the line then says nothing
 * wrong, and the engine log does.
 */
const isCaptchaSetUp = (): boolean => {
	try {
		const service = server.osgi.getService(CAPTCHA_CONFIG_SERVICE);
		return Boolean(service.isCaptchaWidgetConfigured()) && Boolean(service.isCaptchaVerificationConfigured());
	} catch (error) {
		console.error("[Formidable] Could not read the captcha configuration", error);
		return true;
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


/**
 * The captcha settings CaptchaRenderFilter (Java) sets as request attributes on a form carrying the
 * fmdbmix:captcha mixin: the script of the provider, and what the island needs to render the widget,
 * undefined unless the four of them are set.
 */
const readCaptchaConfig = (renderContext: RenderContext) => {
	const request = renderContext.getRequest();
	const siteKey = request.getAttribute('formidable.captcha.siteKey') as string | null;
	const scriptUrl = request.getAttribute('formidable.captcha.scriptUrl') as string | null;
	const widgetVar = request.getAttribute('formidable.captcha.widgetVar') as string | null;
	const tokenField = request.getAttribute('formidable.captcha.tokenField') as string | null;
	// Defensive parsing: the attribute is a Java host value; anything that does not
	// coerce to a finite positive number must not reach the client (NaN would break
	// the poll deadline there).
	const rawWidgetTimeout = Number(request.getAttribute('formidable.captcha.widgetTimeoutSeconds'));
	const widgetTimeoutSeconds = Number.isFinite(rawWidgetTimeout) && rawWidgetTimeout > 0 ? rawWidgetTimeout : undefined;
	const config = siteKey && scriptUrl && widgetVar && tokenField
		? {siteKey, widgetVar, tokenField, widgetTimeoutSeconds}
		: undefined;
	return {scriptUrl, config};
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
			maintenanceMessage,
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
		const {t} = useTranslation("formidable-elements", {keyPrefix: "fmdb_form"});
		const fieldListNode = currentNode.getNode("fields");
		const actionListNode = currentNode.hasNode("actions") ? currentNode.getNode("actions") : null;
		const formElements = fieldListNode ? Array.from(fieldListNode.getNodes()) : [];
		// The form's UUID is its one identity: the DOM id and name, what jExperience calls the form
		// (goals, mapping rules, the submission event) and the value of its data-form-id opt-out.
		const formId = currentNode.getIdentifier();
		// the title the author gave, not the fallback getDisplayableName() makes from the node name
		const formTitle = getNodeProps<{'jcr:title'?: string}>(currentNode, ['jcr:title'])['jcr:title'];

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

		const isEditMode = renderContext.isEditMode();
		const {scriptUrl, config: captchaConfig} = readCaptchaConfig(renderContext);
		// While authoring, the widget and its script are left out: a line in their place says that preview and live show it.
		const captcha = isEditMode ? undefined : captchaConfig;
		const hasCaptcha = currentNode.isNodeType('fmdbmix:captcha');
		// Read once per authoring render: the line below says whether the form can be sent at all.
		const captchaSetUp = isEditMode && hasCaptcha ? isCaptchaSetUp() : true;
		// The engine-owned semantic the submission pipeline reads (fmdbmix:requireAuthentication extends it).
		const requiresSignIn = currentNode.isNodeType('fmdbmix:authenticatedOnlyForm');

		if (hasCaptcha && !captchaConfig) {
			console.warn(`[Formidable] fmdbmix:captcha is applied on form '${currentNode.getPath()}' but CAPTCHA is not fully configured (siteKey, scriptUrl, widgetVar or tokenField missing). The widget will not be rendered.`);
		}

		const isSubmitDisabled = isEditMode || renderContext.isPreviewMode();
		// /modules/… is a mapping inside the Jahia webapp: under a context path the bare path 404s
		const submitActionUrl = `${renderContext.getRequest().getContextPath()}/modules/formidable-engine/form-submit?fid=${currentNode.getIdentifier()}&lang=${currentNode.getLanguage()}`;
		// The field-action pre-check, same shape: the island asks it about a field carrying actions as the
		// visitor leaves the field or before the submission (docs/architecture/field-actions.md). The
		// language is always sent — the engine renders the contributor's message in it.
		const fieldActionUrl = `${renderContext.getRequest().getContextPath()}/modules/formidable-engine/field-action?fid=${currentNode.getIdentifier()}&lang=${currentNode.getLanguage()}`;

		// Maintenance state: only for forms whose actions write to the repository, and only
		// in live (contributors keep seeing the real form in edit/preview). Render-time
		// detection is a UX layer — cached fragments can be stale in both directions, so the
		// maintenance fragment gets a short TTL and the submission pipeline stays authoritative.
		const isMaintenance = !isSubmitDisabled && isPlatformReadOnly() && hasRepositoryWritingAction(currentNode);
		if (isMaintenance) {
			// The Java request exposes setAttribute; the TS stub only types the getters.
			(renderContext.getRequest() as unknown as {setAttribute(name: string, value: string): void})
				.setAttribute("expiration", "60");

			// The state is known server-side: the form markup is not emitted at all,
			// only the contributor's message — static HTML, no island, no entrance
			// animation. The message is contributor richtext (bundle text for forms
			// created before the property existed); no visitor input ever reaches it.
			return (
				<>
					{css && <style dangerouslySetInnerHTML={{__html: styleTagCss(css)}}/>}
					<AddResources type="css" resources={buildModuleFileUrl("dist/assets/style.css")} />
					<div className="fmdb-message fmdb-message-maintenance" role="status">
						<div
							className="fmdb-message-content"
							dangerouslySetInnerHTML={{__html: resolveUrlPlaceholders(maintenanceMessage, renderContext) || t('maintenanceUnavailable')}}
						/>
					</div>
				</>
			);
		}

		return (
			<>
				{css && <style dangerouslySetInnerHTML={{__html: styleTagCss(css)}}/>}
				<AddResources type="css" resources={buildModuleFileUrl("dist/assets/style.css")} />
				{captcha && scriptUrl && (
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
				maintenanceMessage: resolveUrlPlaceholders(maintenanceMessage, renderContext),
				submitActionUrl,
				fieldActionUrl,
				isSubmitDisabled,
				isEditMode,
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
				formTitle,
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
							// Authoring renders the form flat: every step stacked with its title,
							// none hidden, so the contributor sees and reaches everything. The
							// island stays out of it (useMultiStep is disabled in edit mode).
							// The two parameters depend on showStepsNav ASYMMETRICALLY, per the
							// option's documented contract: the compact view drops the step title
							// because the nav bar carries it, so without the nav each step keeps
							// its own title and description — but a multi-step form must show one
							// step at a time either way (tying the hiding to the nav stacked every
							// step and bypassed per-step validation).
							preferCompactStepView: showStepsNav && !isEditMode ? "true" : "false",
							hideStepsAfterFirst: !isEditMode ? "true" : "false",
							childView: "default",
							// Authoring zone around the field list (authoring.css); no extra markup in live.
							...(isEditMode ? {className: "fmdb-form-fields"} : {}),
						}}
					/>
				)}
				{/* The settings of the form that show nothing of themselves while authoring take one line
				    each, drawn as the line of a hidden field (authoring.css): a form for signed-in visitors
				    only, and the captcha, whose widget is left out of the Page Builder. While the instance
				    has no captcha set up, the captcha line turns loud instead: the form cannot be sent, and
				    the administrator is the one to tell — what to set is their job, not the contributor's. */}
				{isEditMode && requiresSignIn && (
					<div className="fmdb-authoring-hidden-field fmdb-authoring-sign-in" data-fmdb-authoring="sign-in">
						<LockIcon/>
						<span className="fmdb-authoring-hidden-field-label">{t("signInAuthoringTitle")}</span>
						<span className="fmdb-authoring-hidden-field-value">{t("signInAuthoringDescription")}</span>
					</div>
				)}
				{isEditMode && hasCaptcha && captchaSetUp && (
					<div className="fmdb-authoring-hidden-field fmdb-authoring-captcha" data-fmdb-authoring="captcha">
						<ShieldCheckIcon/>
						<span className="fmdb-authoring-hidden-field-label">{t("captchaAuthoringTitle")}</span>
						<span className="fmdb-authoring-hidden-field-value">{t("captchaAuthoringDescription")}</span>
					</div>
				)}
				{isEditMode && hasCaptcha && !captchaSetUp && (
					<div className="fmdb-authoring-hidden-field fmdb-authoring-captcha fmdb-authoring-captcha-alert" data-fmdb-authoring="captcha" data-fmdb-authoring-state="unconfigured">
						<AlertIcon/>
						<span className="fmdb-authoring-hidden-field-label">{t("captchaAuthoringTitle")}</span>
						<span className="fmdb-authoring-hidden-field-value" title={t("captchaAuthoringUnconfigured")}>{t("captchaAuthoringUnconfigured")}</span>
					</div>
				)}
				{/* The actions are invisible on a page otherwise: while authoring, the list
				    renders as a zone of its own (cards + create button), inside the form so a
				    business stylesheet's card keeps it, ordered under the buttons by
				    authoring.css. Nothing of it exists in live or preview. */}
				{isEditMode && actionListNode && (
					<Render node={actionListNode} view="hidden.authoring"/>
				)}
				</Island>
			</>
		);
	},
);
