import {type FormEvent, useEffect, useRef, useState} from 'react';
import {interpolateMessage} from '~/utils/messageUtils';
import clsx from "clsx";
import classes from './Form.client.module.css';
import {type FormProps} from './types';
import Spinner from '~/design/Spinner';
import DOMPurify from 'dompurify';
import Captcha, {type CaptchaHandle} from './Captcha.client';

const sanitize = (html: string): string => {
	if (typeof window === 'undefined') return '';
	return DOMPurify.sanitize(html);
};
import {useTranslation} from "react-i18next";

export default function Form({
														 intro,
														 submissionMessage,
														 errorMessage,
														 destinationUrl,
														 submitActionUrl,
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
															 captcha,
															 children
														 }: FormProps) {
	const [message, setMessage] = useState<string | null>(null);
	const [messageType, setMessageType] = useState<'success' | 'error' | null>(null);
	const [isLoading, setIsLoading] = useState(false);
	const [currentStep, setCurrentStep] = useState(0);
	const formRef = useRef<HTMLFormElement>(null);
	const captchaRef = useRef<CaptchaHandle>(null);

	const {t} = useTranslation('formidable-elements', {keyPrefix: 'fmdb_form'});

	const isMultiStep = stepLabels && stepLabels.length > 0;
	const totalSteps = isMultiStep ? stepLabels.length : 0;
	const isLastStep = currentStep === totalSteps - 1;

	const showCaptcha = !!captcha && (!isMultiStep || isLastStep);

	const stepElsRef = useRef<HTMLElement[]>([]);
	useEffect(() => {
		if (formRef.current) {
			stepElsRef.current = Array.from(formRef.current.querySelectorAll<HTMLElement>('[data-fmdb-step]'));
		}
	}, []);

	const prevStepRef = useRef(0);
	useEffect(() => {
		if (!isMultiStep) return;
		const stepEls = stepElsRef.current;
		if (stepEls[prevStepRef.current]) stepEls[prevStepRef.current].style.display = 'none';
		if (stepEls[currentStep]) stepEls[currentStep].style.display = '';
		prevStepRef.current = currentStep;
	}, [currentStep, isMultiStep]);

	const validateCurrentStep = (): boolean => {
		const current = stepElsRef.current[currentStep];
		if (!current) return true;
		const inputs = current.querySelectorAll<HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement>(
			'input, select, textarea'
		);
		return Array.from(inputs).every(input => input.reportValidity());
	};

	const handleNext = () => {
		if (validateCurrentStep()) {
			setCurrentStep(s => s + 1);
		}
	};

	const handlePrevious = () => {
		setCurrentStep(s => s - 1);
	};

	const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
		event.preventDefault();

		if (isMultiStep && !isLastStep) return;

		setIsLoading(true);

		if (captcha && !captchaRef.current?.getToken()) {
			setMessage(t('captchaRequired'));
			setMessageType('error');
			setIsLoading(false);
			return;
		}

		const form = event.currentTarget;

		try {
			const formData = new FormData(form);
			// Each captcha widget (Turnstile / hCaptcha / reCAPTCHA) auto-injects its native
			// hidden field into the form DOM. FormData picks it up automatically.

			const interpolatedSubmissionMessage = interpolateMessage(submissionMessage, formData, locale);

			if (destinationUrl) {
				// Transfer mode: POST full FormData (including files + captcha token) to the
				// third-party destination first. If it rejects, stop here — no side effects run.
				const destinationResponse = await fetch(destinationUrl, {method: 'POST', body: formData});
				if (!destinationResponse.ok) {
					throw new Error('Submission failed');
				}
			}

			if (submitActionUrl) {
				// Jahia pipeline: side effects only in transfer mode (captcha + destination already done),
				// or full pipeline (captcha verify → save2jcr → side effects) in JCR mode.
				const pipelineResponse = await fetch(submitActionUrl, {method: 'POST', body: formData});
				if (!pipelineResponse.ok) {
					throw new Error('Submission failed');
				}
			}

			if (!destinationUrl && !submitActionUrl) {
				// No pipeline — post to the form's action URL (current page if not set).
				const response = await fetch(form.action || window.location.href, {method: 'POST', body: formData});
				if (!response.ok) {
					throw new Error('Submission failed');
				}
			}

			await new Promise(resolve => setTimeout(resolve, 500));

			setMessage(interpolatedSubmissionMessage || 'Form submitted successfully!');
			setMessageType('success');
			form.reset();
			if (isMultiStep) setCurrentStep(0);
		} catch (error) {
			const formData = new FormData(form);
			const interpolatedErrorMessage = interpolateMessage(errorMessage, formData, locale);
			setMessage(interpolatedErrorMessage || 'An error occurred while submitting the form.');
			setMessageType('error');
			captchaRef.current?.reset();
			console.error("formidable submit error:", error);
		} finally {
			setIsLoading(false);
		}
	};

	const showForm = () => {
		setIsLoading(true);
		setTimeout(() => {
			setMessage(null);
			setMessageType(null);
			setIsLoading(false);
		}, 300);
	};

	// Show message after form submission
	const hasMessage = message && messageType;

	// Sanitize HTML content to prevent XSS attacks
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
				onSubmit={handleSubmit}
			>
				{intro && (
					<header className="fmdb-form-intro" dangerouslySetInnerHTML={{__html: sanitizedIntro}}/>
				)}

				{isMultiStep && showStepsNav && (
					<nav className={clsx("fmdb-steps-nav", classes.stepsNav)} aria-label={t('stepsNav')}>
						{stepLabels.map((label, i) => (
							<span
								key={label}
								className={clsx(
									"fmdb-step-indicator",
									classes.stepIndicator,
									i === currentStep && classes.stepIndicatorActive,
									i < currentStep && classes.stepIndicatorDone
								)}
								aria-current={i === currentStep ? 'step' : undefined}
							>
								<span className={clsx("fmdb-step-number", classes.stepNumber)}>{i + 1}</span>
								<span className="fmdb-step-label">{label}</span>
							</span>
						))}
					</nav>
				)}

			{children}

			{showCaptcha && (
				<Captcha ref={captchaRef} siteKey={captcha!.siteKey} provider={captcha!.provider}/>
			)}

			<div className="fmdb-form-actions">
					{isMultiStep ? (
						<>
							{currentStep > 0 && (
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
								onClick={handleNext}
								disabled={isLoading}
							>
								{nextBtnLabel || t('nextBtn')}
							</button>
						)}
						{isLastStep && (
							<button
								type="submit"
								className="fmdb-btn fmdb-btn-primary"
								disabled={isLoading}
							>
								{submitBtnLabel || t('submitBtn')}
							</button>
						)}
						</>
					) : (
						<>
							<button type="submit" className="fmdb-btn fmdb-btn-primary" disabled={isLoading}>
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
