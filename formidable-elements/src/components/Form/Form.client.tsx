import {useEffect, useRef, useState} from 'react';
import clsx from "clsx";
import classes from './Form.client.module.css';
import '~/design/validation.css';
import {type FormProps} from './types';
import Spinner from '~/design/Spinner';
import DOMPurify from 'dompurify';
import Captcha from './Captcha.client';
import {useTranslation} from "react-i18next";
import {useMultiStep} from '~/hooks/useMultiStep';
import {useCustomFormValidation, validateInputs} from '~/hooks/useCustomFormValidation';
import {useFormSubmission} from '~/hooks/useFormSubmission';

const sanitize = (html: string): string => {
	if (typeof window === 'undefined') return html;
	return DOMPurify.sanitize(html);
};

// D10: a required sourced choice field whose source failed renders this marker
// server-side; the form must not be submittable while it is present.
const BLOCKING_SOURCE_ERROR_SELECTOR = '[data-fmdb-source-error="blocking"]';

export default function Form({
	intro,
	submissionMessage,
	errorMessage,
	maintenanceMessage,
	submitActionUrl,
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
	locale,
	stepLabels,
	stepIds,
	captcha,
	isEditMode = false,
	children
}: FormProps) {
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
		}
	}, []);

	const {
		currentStep,
		setCurrentStep,
		visibleStepIndices,
		isFirstVisibleStep,
		isLastStep,
		isMultiStep,
		handleNext,
		handlePrevious,
	} = useMultiStep({formRef, stepIds, skipStepValidation: isEditMode});

	useCustomFormValidation({formRef});

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
	});

	const isSubmitBlocked = isLoading || isSubmitDisabled || hasBlockingSourceError
		|| (!!captcha && (!isMultiStep || isLastStep) && !isCaptchaValid);
	const submitBlockedTitle = isSubmitDisabled ? t('editModeSubmitDisabled') : undefined;
	const showCaptcha = !!captcha && (!isMultiStep || isLastStep);

	const validateCurrentStep = (): boolean => {
		const form = formRef.current;
		if (!form) return true;
		const stepEls = form.querySelectorAll<HTMLElement>('[data-fmdb-step]');
		const current = stepEls[currentStep];
		if (!current) return true;
		return validateInputs(current);
	};

	const hasMessage = message && messageType;
	const sanitizedIntro = intro ? sanitize(intro) : '';
	const sanitizedMessage = message ? sanitize(message) : '';

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
						<div dangerouslySetInnerHTML={{__html: sanitizedMessage}}/>
						{messageType === 'success' && showNewFormBtn && (
							<button
								type="button"
								className="fmdb-btn fmdb-btn-secondary fmdb-new-form-btn"
								onClick={showForm}
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
				className={clsx("fmdb-form", classes.form, (hasMessage || isLoading) && classes.hidden)}
				method="post"
				action={submitActionUrl}
				encType="multipart/form-data"
				id={formId}
				// Read back from the DOM by the visibility pass: the rules describe the
				// visitor experience, so they must not run while the form is authored.
				data-fmdb-edit-mode={isEditMode ? "true" : undefined}
				onSubmit={e => handleSubmit(e, () => validateInputs(e.currentTarget))}
			>
				{intro && (
					<header className="fmdb-form-intro" dangerouslySetInnerHTML={{__html: sanitizedIntro}}/>
				)}

				{isMultiStep && showStepsNav && (
					<nav className={clsx("fmdb-steps-nav", classes.stepsNav)} aria-label={t('stepsNav')}>
						{visibleStepIndices.map((stepIdx, visibleIdx) => {
							const indicatorClassName = clsx(
								"fmdb-step-indicator",
								classes.stepIndicator,
								stepIdx === currentStep && classes.stepIndicatorActive,
								visibleStepIndices.indexOf(currentStep) > visibleIdx && classes.stepIndicatorDone
							);
							const indicatorContent = (
								<>
									<span className={clsx("fmdb-step-number", classes.stepNumber)}>{visibleIdx + 1}</span>
									<span className="fmdb-step-label">{stepLabels![stepIdx]}</span>
								</>
							);

							// While authoring, the indicators double as a step switcher: the
							// contributor jumps straight to the step to work on. For a visitor
							// they stay a passive progress trail.
							return isEditMode ? (
								<button
									key={stepIds![stepIdx]}
									type="button"
									className={indicatorClassName}
									aria-current={stepIdx === currentStep ? 'step' : undefined}
									onClick={() => setCurrentStep(stepIdx)}
								>
									{indicatorContent}
								</button>
							) : (
								<span
									key={stepIds![stepIdx]}
									className={indicatorClassName}
									aria-current={stepIdx === currentStep ? 'step' : undefined}
								>
									{indicatorContent}
								</span>
							);
						})}
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
								onClick={() => handleNext(validateCurrentStep)}
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
