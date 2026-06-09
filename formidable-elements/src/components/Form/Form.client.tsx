import {useRef} from 'react';
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

export default function Form({
	intro,
	submissionMessage,
	errorMessage,
	submitActionUrl,
	isSubmitDisabled = false,
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
	children
}: FormProps) {
	const formRef = useRef<HTMLFormElement>(null);
	const {t} = useTranslation('formidable-elements', {keyPrefix: 'fmdb_form'});

	const {
		currentStep,
		setCurrentStep,
		visibleStepIndices,
		isFirstVisibleStep,
		isLastStep,
		isMultiStep,
		handleNext,
		handlePrevious,
	} = useMultiStep({formRef, stepIds});

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
		},
	});

	const isSubmitBlocked = isLoading || isSubmitDisabled || (!!captcha && (!isMultiStep || isLastStep) && !isCaptchaValid);
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
				id={formId}
				noValidate
				onSubmit={e => handleSubmit(e, () => validateInputs(e.currentTarget))}
			>
				{intro && (
					<header className="fmdb-form-intro" dangerouslySetInnerHTML={{__html: sanitizedIntro}}/>
				)}

				{isMultiStep && showStepsNav && (
					<nav className={clsx("fmdb-steps-nav", classes.stepsNav)} aria-label={t('stepsNav')}>
						{visibleStepIndices.map((stepIdx, visibleIdx) => (
							<span
								key={stepLabels![stepIdx]}
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
								title={isSubmitDisabled ? t('editModeSubmitDisabled') : undefined}
							>
								{submitBtnLabel || t('submitBtn')}
							</button>
						)}
						</>
					) : (
						<>
							<button type="submit" className="fmdb-btn fmdb-btn-primary" disabled={isSubmitBlocked} title={isSubmitDisabled ? t('editModeSubmitDisabled') : undefined}>
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
