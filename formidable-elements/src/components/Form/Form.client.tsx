import {useEffect, useRef, useState} from 'react';
import clsx from "clsx";
import classes from './Form.client.module.css';
import '~/design/buttons.css';
import '~/design/validation.css';
import '~/design/logic.css';
import '~/design/prefill.css';
import '~/design/authoring.css';
import {type FormProps} from './types';
import Spinner from '~/design/Spinner';
import Captcha from './Captcha.client';
import {useTranslation} from "react-i18next";
import {useMultiStep} from '~/hooks/useMultiStep';
import {useCustomFormValidation, validateInputs} from '~/hooks/useCustomFormValidation';
import {useFieldActions} from '~/hooks/useFieldActions';
import {useFormSubmission} from '~/hooks/useFormSubmission';

/**
 * Dispatched on the form element once the island has mounted and taken the form over (noValidate set,
 * listeners attached). Detail: {formId}. Until it fires, the form is the server's plain HTML.
 */
export const READY_EVENT = 'formidable:ready';

/**
 * Dispatched on the form element once a reset has been applied — the visitor's Reset button, or the one
 * the island performs after an accepted submission. Detail: {formId}. It fires after the browser restored
 * the default values, not with the native `reset` event, which fires before.
 */
export const RESET_EVENT = 'formidable:reset';

/**
 * Dispatched on the form element when the visitor asks for another form from the message that followed a
 * submission. Detail: {formId}. The form is the same element, emptied: what a script put in it at page
 * load — the jExperience prefill — is gone, and this is what tells it to do its work again.
 */
export const NEW_FORM_EVENT = 'formidable:newForm';

// D10: a required sourced choice field whose source failed renders this marker
// server-side; the form must not be submittable while it is present.
const BLOCKING_SOURCE_ERROR_SELECTOR = '[data-fmdb-source-error="blocking"]';

export default function Form({
	intro,
	submissionMessage,
	errorMessage,
	maintenanceMessage,
	submitActionUrl,
	fieldActionUrl,
	isSubmitDisabled = false,
	isEditMode = false,
	showResetBtn = false,
	showNewFormBtn = false,
	showTryAgainBtn = false,
	submitBtnLabel,
	resetBtnLabel,
	newFormBtnLabel,
	tryAgainBtnLabel,
	previousBtnLabel,
	nextBtnLabel,
	showStepsNav = true,
	formId,
	formTitle,
	locale,
	stepLabels,
	stepIds,
	captcha,
	children
}: Readonly<FormProps>) {
	const formRef = useRef<HTMLFormElement>(null);
	const {t} = useTranslation('formidable-elements', {keyPrefix: 'fmdb_form'});
	const [hasBlockingSourceError, setHasBlockingSourceError] = useState(false);

	// Contributor-configurable maintenance message (fmdbmix:responses), shown when a
	// submission hits FMDB-014 (mode switched between render and submit); the bundle
	// text keeps covering forms created before the property existed.
	const maintenanceText = maintenanceMessage || t('maintenanceUnavailable');

	useEffect(() => {
		if (formRef.current) {
			formRef.current.noValidate = true;
			// The marker is rendered server-side inside the island children: it can only
			// be read from the DOM once mounted, hence the state initialization here.
			// eslint-disable-next-line @eslint-react/hooks-extra/no-direct-set-state-in-use-effect
			setHasBlockingSourceError(Boolean(formRef.current.querySelector(BLOCKING_SOURCE_ERROR_SELECTOR)));
			// The island is in charge from here: a script that writes into the fields (the jExperience
			// prefill) waits for this, so that no island resets what it wrote. The twin of
			// formidable:submitted — bubbling, the form's UUID in the detail, nothing else.
			formRef.current.dispatchEvent(new CustomEvent(READY_EVENT, {bubbles: true, detail: {formId}}));
		}
	}, [formId]);

	// A reset empties the form, so whatever a script had written into it is gone — the jExperience prefill
	// included. The native event fires BEFORE the browser restores the defaults, so the announcement waits
	// for the end of the task: a listener that fills the form again must not be undone by the reset itself.
	useEffect(() => {
		const form = formRef.current;
		if (!form) {
			return;
		}

		const announce = () => {
			setTimeout(() => form.dispatchEvent(new CustomEvent(RESET_EVENT, {bubbles: true, detail: {formId}})), 0);
		};
		form.addEventListener('reset', announce);
		return () => form.removeEventListener('reset', announce);
	}, [formId]);

	const {
		currentStep,
		setCurrentStep,
		visibleStepIndices,
		isFirstVisibleStep,
		isLastStep,
		isMultiStep,
		handleNext,
		handlePrevious,
		stepIndexOf,
	} = useMultiStep({formRef, stepIds, disabled: isEditMode});

	// A refused control to bring on screen: the step holding it, then the focus — once the spinner has
	// gone, since a hidden control cannot take the focus. A fresh object each time, so the same control
	// refused twice is revealed twice.
	const [reveal, setReveal] = useState<{control: HTMLElement} | null>(null);

	useCustomFormValidation({formRef});
	// The field actions (docs/architecture/field-actions.md): asked as the visitor leaves a field that
	// carries some, and settled before the submission below. Off while authoring, like the logic.
	const {settleFieldActions} = useFieldActions({
		formRef,
		fieldActionUrl,
		enabled: !isEditMode && !!fieldActionUrl,
		labels: {checking: t('checking')},
	});

	const {
		message,
		messageType,
		isLoading,
		isCaptchaValid,
		setIsCaptchaValid,
		captchaRef,
		handleSubmit,
		showForm,
	} = useFormSubmission({
		formId,
		submitActionUrl,
		submissionMessage,
		errorMessage,
		locale,
		captcha,
		isMultiStep,
		isLastStep,
		setCurrentStep,
		labels: {
			captchaRequired: t('captchaRequired'),
			errorCode: t('errorCode'),
			actionsProgress: (completed, total) => t('actionsProgress', {completed, total}),
			maintenanceUnavailable: maintenanceText,
		},
		onRefused: control => setReveal({control}),
	});

	// A refused control — the settle before a submission or the next step, or the pipeline's FMDB-015 —
	// is brought on screen and focused once the form is back on screen: the spinner hides it
	// (display:none) while a request runs, and a step other than the current one is hidden too, so the
	// step is shown first and the focus follows on the next run, once the step's display has changed.
	// An effect of the island's state, which no call inside a request could time.
	useEffect(() => {
		if (!reveal || isLoading) return;
		const step = stepIndexOf(reveal.control);
		if (step !== null && step !== currentStep) {
			setCurrentStep(step);
			return;
		}
		reveal.control.focus();
		// eslint-disable-next-line @eslint-react/hooks-extra/no-direct-set-state-in-use-effect -- the reveal is consumed once the focus has landed
		setReveal(null);
	}, [reveal, isLoading, currentStep, stepIndexOf, setCurrentStep]);

	// Another form, after an accepted submission: the island emptied it on the 2xx, so a script that
	// filled it at page load — the jExperience prefill — is told to do its work again. Only this path
	// says it: after an error the form keeps everything the visitor typed (see below), and a script
	// told the form was new would write over their corrections.
	const startAnother = () => {
		showForm();
		formRef.current?.dispatchEvent(new CustomEvent(NEW_FORM_EVENT, {bubbles: true, detail: {formId}}));
	};

	const isSubmitBlocked = isLoading || isSubmitDisabled || hasBlockingSourceError
		|| (!!captcha && (!isMultiStep || isLastStep) && !isCaptchaValid);
	const submitBlockedTitle = isSubmitDisabled ? t('editModeSubmitDisabled') : undefined;
	const showCaptcha = !!captcha && (!isMultiStep || isLastStep);

	// Leaving a step: its constraints, then its field actions — the blur-checked values again (free, the
	// engine's cache) and the ones set to run at submission, asked now, when the visitor leaves their step.
	// A refusal keeps the visitor on the step, the message under the field, the field focused.
	const validateCurrentStep = async (): Promise<boolean> => {
		const form = formRef.current;
		if (!form) return true;
		const stepEls = form.querySelectorAll<HTMLElement>('[data-fmdb-step]');
		const current = stepEls[currentStep];
		if (!current) return true;
		if (!validateInputs(current)) return false;
		const refused = await settleFieldActions(current);
		if (refused) setReveal({control: refused});
		return !refused;
	};

	// Sending: the whole form's constraints, then every field action of the form with the submit
	// trigger — asked only when every constraint holds, so a refused value is never sent to a provider
	// for nothing. A refusal is brought on screen, whatever step holds it.
	const validateForm = async (form: HTMLFormElement): Promise<boolean> => {
		if (!validateInputs(form)) return false;
		const refused = await settleFieldActions(form);
		if (refused) setReveal({control: refused});
		return !refused;
	};

	const hasMessage = message && messageType;
	// Contributor rich text is trusted, as in every other view of the module (step
	// intro, compact view, help text) and everywhere else in Jahia. The previous
	// client-only sanitize was a no-op on the SSR pass that actually produces the
	// markup, so it could only cause hydration mismatches, not safety.
	const introHtml = intro ?? '';
	const messageHtml = message ?? '';

	return (
		<>
			{isLoading && (
				<Spinner
					overlay
					text={messageType === null ? t('submitting') : t('loading')}
					className="fmdb-spinner"
				/>
			)}

			{hasMessage && !isLoading &&
				<div className={clsx(`fmdb-message fmdb-message-${messageType}`, classes.message)} role="alert">
					<div className="fmdb-message-content">
						<div dangerouslySetInnerHTML={{__html: messageHtml}}/>
						{messageType === 'success' && showNewFormBtn && (
							<button
								type="button"
								className="fmdb-btn fmdb-btn-secondary fmdb-new-form-btn"
								onClick={startAnother}
							>
								{newFormBtnLabel || t('newFormBtn')}
							</button>
						)}
						{messageType === 'error' && showTryAgainBtn && (
							<button
								type="button"
								className="fmdb-btn fmdb-btn-secondary fmdb-new-form-btn"
								onClick={showForm}
							>
								{tryAgainBtnLabel || t('tryAgainBtn')}
							</button>
						)}
					</div>
				</div>
			}
			<form
				ref={formRef}
				// An error keeps the form on screen: hiding it stranded the visitor's
				// typed data behind a message whose retry button is off by default —
				// the only way back was a reload, which lost everything. A successful
				// submission, the maintenance rejection (nothing to retry: the platform
				// is read-only) and the submit in flight hide the form.
				className={clsx("fmdb-form", classes.form,
					(isLoading || (hasMessage && messageType !== 'error')) && classes.hidden)}
				method="post"
				action={submitActionUrl}
				encType="multipart/form-data"
				id={formId}
				name={formId}
				// The accessible name of the form landmark, when the author gave the form a title: nothing
				// inside the form repeats it. Without a title there is no name — getDisplayableName() would
				// fall back to the node name, and three untitled forms would be announced "form", "form-2",
				// "form-3"; an unnamed landmark is not announced as one, which is better than a wrong name.
				aria-label={formTitle || undefined}
				// jExperience's tracker attaches its own submit listener to any form whose name or id a
				// goal or a mapping rule watches, and sends the raw DOM fields before validation. Two
				// attributes keep it off, one per code path, and they live in different files: the
				// initial scan is the Unomi tracker's, bundled into wem.min.js, and it skips a form
				// carrying data-form-id (the Jahia Forms convention); the observer of late forms is
				// jExperience's own wem.js, and it skips a form whose dataset.wemObserved is set.
				// Submissions reach jCustomer through formidable-jexperience-engine only, with the
				// values the pipeline accepted. Asserted by the Cypress spec of the form's attributes.
				data-form-id={formId}
				data-wem-observed="true"
				// Read back from the DOM by the visibility pass: the rules describe the
				// visitor experience, so they must not run while the form is authored.
				data-fmdb-edit-mode={isEditMode ? "true" : undefined}
				// The form is read before anything awaits: React nulls the synthetic event's currentTarget
				// once the handler returns.
				onSubmit={e => {
					const form = e.currentTarget;
					void handleSubmit(e, () => validateForm(form));
				}}
			>
				{intro && (
					<header className="fmdb-form-intro" dangerouslySetInnerHTML={{__html: introHtml}}/>
				)}

				{isMultiStep && showStepsNav && (
					<nav className={clsx("fmdb-steps-nav", classes.stepsNav)} aria-label={t('stepsNav')}>
						{visibleStepIndices.map((stepIdx, visibleIdx) => (
							<span
								key={stepIds![stepIdx]}
								className={clsx(
									"fmdb-step-indicator",
									classes.stepIndicator,
									stepIdx === currentStep && classes.stepIndicatorActive,
									visibleStepIndices.indexOf(currentStep) > visibleIdx && classes.stepIndicatorDone
								)}
								aria-current={stepIdx === currentStep ? 'step' : undefined}
							>
								<span className={clsx("fmdb-step-number", classes.stepNumber)}>{visibleIdx + 1}</span>
								<span className="fmdb-step-label">{stepLabels![stepIdx]}</span>
							</span>
						))}
					</nav>
				)}

			{children}

			{showCaptcha && (
				<Captcha
					ref={captchaRef}
					siteKey={captcha!.siteKey}
					widgetVar={captcha!.widgetVar}
					widgetTimeoutSeconds={captcha!.widgetTimeoutSeconds}
					onVerify={() => setIsCaptchaValid(true)}
					onExpire={() => setIsCaptchaValid(false)}
				/>
			)}

			<div className="fmdb-form-actions">
					{isMultiStep ? (
						<>
							{!isFirstVisibleStep && (
								<button
									type="button"
									className="fmdb-btn fmdb-btn-secondary fmdb-prev-btn"
									onClick={handlePrevious}
									disabled={isLoading}
								>
									{previousBtnLabel || t('previousBtn')}
								</button>
							)}
						{!isLastStep && (
							<button
								type="button"
								className="fmdb-btn fmdb-btn-primary fmdb-next-btn"
								onClick={() => void handleNext(validateCurrentStep)}
								disabled={isLoading}
							>
								{nextBtnLabel || t('nextBtn')}
							</button>
						)}
						{isLastStep && (
							<button
								type="submit"
								className="fmdb-btn fmdb-btn-primary"
								disabled={isSubmitBlocked}
								title={submitBlockedTitle}
							>
								{submitBtnLabel || t('submitBtn')}
							</button>
						)}
						</>
					) : (
						<>
							<button type="submit" className="fmdb-btn fmdb-btn-primary" disabled={isSubmitBlocked} title={submitBlockedTitle}>
							{submitBtnLabel || t('submitBtn')}
						</button>
							{showResetBtn && (
								<button type="reset" className="fmdb-btn fmdb-btn-secondary" disabled={isLoading}>
									{resetBtnLabel || t('resetBtn')}
								</button>
							)}
						</>
					)}
				</div>
			</form>

		</>

	);
}
