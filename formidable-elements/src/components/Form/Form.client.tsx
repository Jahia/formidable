import {type FormEvent, useCallback, useEffect, useRef, useState} from 'react';
import {interpolateMessage} from '~/utils/messageUtils';
import {applyConditionalLogicVisibility} from '~/utils/conditionalLogic';
import clsx from "clsx";
import classes from './Form.client.module.css';
import {type FormProps} from './types';
import Spinner from '~/design/Spinner';
import DOMPurify from 'dompurify';
import Captcha, {type CaptchaHandle} from './Captcha.client';
import {useTranslation} from "react-i18next";

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
	const [message, setMessage] = useState<string | null>(null);
	const [messageType, setMessageType] = useState<'success' | 'error' | null>(null);
	const [isLoading, setIsLoading] = useState(false);
	const [isCaptchaValid, setIsCaptchaValid] = useState(false);
	const [currentStep, setCurrentStep] = useState(0);
	const [visibleStepIndices, setVisibleStepIndices] = useState<number[]>([]);
	const formRef = useRef<HTMLFormElement>(null);
	const captchaRef = useRef<CaptchaHandle>(null);
	const resetVisibilityTimeoutRef = useRef<number | null>(null);

	const {t} = useTranslation('formidable-elements', {keyPrefix: 'fmdb_form'});

	const isMultiStep = stepLabels && stepLabels.length > 0;
	const visibleStepCount = visibleStepIndices.length;
	const currentVisibleIndex = visibleStepIndices.indexOf(currentStep);
	const isLastStep = currentVisibleIndex === visibleStepCount - 1;
	const isFirstVisibleStep = currentVisibleIndex === 0;

	const isSubmitBlocked = isLoading || isSubmitDisabled || (!!captcha && (!isMultiStep || isLastStep) && !isCaptchaValid);
	const showCaptcha = !!captcha && (!isMultiStep || isLastStep);

	const stepElsRef = useRef<HTMLElement[]>([]);
	useEffect(() => {
		if (formRef.current) {
			stepElsRef.current = Array.from(formRef.current.querySelectorAll<HTMLElement>('[data-fmdb-step]'));
		}
	}, []);

	const computeVisibleSteps = useCallback(() => {
		if (!isMultiStep || !stepIds || !formRef.current) return;
		const indices: number[] = [];
		for (let i = 0; i < stepIds.length; i++) {
			const wrapper = formRef.current.querySelector<HTMLElement>(`[data-fmdb-node-id="${stepIds[i]}"]`);
			if (!wrapper || wrapper.dataset.fmdbLogicHidden !== 'true') {
				indices.push(i);
			}
		}
		setVisibleStepIndices(prev => {
			if (prev.length === indices.length && prev.every((v, j) => v === indices[j])) return prev;
			return indices;
		});
		// If the current step is no longer visible, jump to the nearest visible step
		setCurrentStep(current => {
			if (indices.includes(current)) return current;
			const nearest = indices.find(i => i >= current) ?? indices[indices.length - 1] ?? 0;
			return nearest;
		});
	}, [isMultiStep, stepIds]);

	useEffect(() => {
		const form = formRef.current;
		if (!form) return;

		const syncVisibility = () => {
			applyConditionalLogicVisibility(form);
			computeVisibleSteps();
		};
		const handleReset = () => {
			if (resetVisibilityTimeoutRef.current !== null) {
				window.clearTimeout(resetVisibilityTimeoutRef.current);
			}

			resetVisibilityTimeoutRef.current = window.setTimeout(() => {
				syncVisibility();
				resetVisibilityTimeoutRef.current = null;
			}, 0);
		};

		syncVisibility();

		form.addEventListener('input', syncVisibility);
		form.addEventListener('change', syncVisibility);
		form.addEventListener('reset', handleReset);

		return () => {
			form.removeEventListener('input', syncVisibility);
			form.removeEventListener('change', syncVisibility);
			form.removeEventListener('reset', handleReset);
			if (resetVisibilityTimeoutRef.current !== null) {
				window.clearTimeout(resetVisibilityTimeoutRef.current);
				resetVisibilityTimeoutRef.current = null;
			}
		};
	}, [computeVisibleSteps]);

	const prevStepRef = useRef(0);
	useEffect(() => {
		if (!isMultiStep) return;
		const stepEls = stepElsRef.current;
		if (stepEls[prevStepRef.current]) stepEls[prevStepRef.current].style.display = 'none';
		if (stepEls[currentStep]) stepEls[currentStep].style.display = '';
		prevStepRef.current = currentStep;
	}, [currentStep, isMultiStep]);

	useEffect(() => {
		if (formRef.current) {
			applyConditionalLogicVisibility(formRef.current);
			computeVisibleSteps();
		}
	}, [currentStep, computeVisibleSteps]);

	const validateCurrentStep = (): boolean => {
		const current = stepElsRef.current[currentStep];
		if (!current) return true;
		const inputs = current.querySelectorAll<HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement>(
			'input, select, textarea'
		);
		return Array.from(inputs).every(input => input.reportValidity());
	};

	const handleNext = () => {
		if (!validateCurrentStep()) return;
		const nextIndex = visibleStepIndices[currentVisibleIndex + 1];
		if (nextIndex !== undefined) setCurrentStep(nextIndex);
	};

	const handlePrevious = () => {
		const prevIndex = visibleStepIndices[currentVisibleIndex - 1];
		if (prevIndex !== undefined) setCurrentStep(prevIndex);
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
		let serverErrorCode: string | undefined;
		let serverActionsProgress: {completed: number; total: number} | undefined;

		try {
			const formData = new FormData(form);

			// Remove the CAPTCHA widget's hidden field from the body — the token is sent
			// in a dedicated HTTP header so it must not be sent twice.
			if (captcha) {
				formData.delete(captcha.tokenField);
			}

			const interpolatedSubmissionMessage = interpolateMessage(submissionMessage, formData, locale);

			const rawCaptchaToken = captcha ? captchaRef.current?.getToken() : undefined;
			const captchaToken = rawCaptchaToken?.trim() || undefined;
			const targetUrl = submitActionUrl ?? form.action ?? window.location.href;

			// XHR is required here instead of fetch: Jahia's OWASP CSRFGuard patches
			// XMLHttpRequest.prototype.send to inject the CSRF token automatically.
			// The fetch API is not patched by CSRFGuard and would result in a CSRF rejection.
			const response = await new Promise<XMLHttpRequest>((resolve, reject) => {
				const xhr = new XMLHttpRequest();
				xhr.open('POST', targetUrl, true);
				if (captchaToken) {
					xhr.setRequestHeader('X-Formidable-Captcha-Token', captchaToken);
				}
				xhr.withCredentials = true;
				xhr.onload = () => resolve(xhr);
				xhr.onerror = () => reject(new Error('Submission failed'));
				xhr.send(formData);
			});

			if (response.status < 200 || response.status >= 300) {
				try {
					const body = JSON.parse(response.responseText);
					if (typeof body.errorCode === 'string') serverErrorCode = body.errorCode;
					if (typeof body.actionsCompleted === 'number' && typeof body.actionsTotal === 'number') {
						serverActionsProgress = {completed: body.actionsCompleted, total: body.actionsTotal};
					}
				} catch { /* ignore non-JSON bodies */ }
				throw new Error('Submission failed');
			}

			await new Promise(resolve => setTimeout(resolve, 500));

			setMessage(interpolatedSubmissionMessage || 'Form submitted successfully!');
			setMessageType('success');
			form.reset();
			if (isMultiStep) setCurrentStep(0);
		} catch (error) {
			const formData = new FormData(form);
			const interpolatedErrorMessage = interpolateMessage(errorMessage, formData, locale);
			const base = interpolatedErrorMessage || 'An error occurred while submitting the form.';
			const details: string[] = [];
			if (serverErrorCode) {
				details.push(`${t('errorCode')}: ${serverErrorCode}`);
			}
			if (serverActionsProgress) {
				details.push(t('actionsProgress', {completed: serverActionsProgress.completed, total: serverActionsProgress.total}));
			}
			const full = details.length > 0
				? `${base}<br><small>${details.join(' — ')}</small>`
				: base;
			setMessage(full);
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
						{visibleStepIndices.map((stepIdx, visibleIdx) => (
							<span
								key={stepLabels[stepIdx]}
								className={clsx(
									"fmdb-step-indicator",
									classes.stepIndicator,
									stepIdx === currentStep && classes.stepIndicatorActive,
									visibleStepIndices.indexOf(currentStep) > visibleIdx && classes.stepIndicatorDone
								)}
								aria-current={stepIdx === currentStep ? 'step' : undefined}
							>
								<span className={clsx("fmdb-step-number", classes.stepNumber)}>{visibleIdx + 1}</span>
								<span className="fmdb-step-label">{stepLabels[stepIdx]}</span>
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
