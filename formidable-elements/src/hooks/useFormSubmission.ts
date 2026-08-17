import {type FormEvent, type RefObject, useRef, useState} from 'react';
import {interpolateMessage} from '~/utils/messageUtils';
import {buildLogicStateHeader} from '~/utils/conditionalLogic';
import {FORM_LOGIC_STATE_HEADER} from '~/utils/logicProviders';
import {type CaptchaHandle} from '~/components/Form/Captcha.client';

interface SubmissionLabels {
	captchaRequired: string;
	errorCode: string;
	actionsProgress: (completed: number, total: number) => string;
	maintenanceUnavailable: string;
}

// Server rejection while the platform is in read-only maintenance (see docs/error-codes.md).
// Covers the window where the mode is switched between render and submit: the visitor gets
// the same maintenance message as the render-time state, not a technical error.
const MAINTENANCE_ERROR_CODE = 'FMDB-014';

interface UseFormSubmissionOptions {
	submitActionUrl?: string;
	submissionMessage?: string;
	errorMessage?: string;
	locale: string;
	captcha?: {siteKey: string; widgetVar: string; tokenField: string};
	isMultiStep: boolean;
	isLastStep: boolean;
	setCurrentStep: (step: number) => void;
	labels: SubmissionLabels;
}

interface UseFormSubmissionReturn {
	message: string | null;
	messageType: 'success' | 'error' | null;
	isLoading: boolean;
	isCaptchaValid: boolean;
	setIsCaptchaValid: (valid: boolean) => void;
	captchaRef: RefObject<CaptchaHandle | null>;
	handleSubmit: (event: FormEvent<HTMLFormElement>, preValidate?: () => boolean) => void;
	showForm: () => void;
}

export function useFormSubmission({
	submitActionUrl,
	submissionMessage,
	errorMessage,
	locale,
	captcha,
	isMultiStep,
	isLastStep,
	setCurrentStep,
	labels,
}: UseFormSubmissionOptions): UseFormSubmissionReturn {
	const [message, setMessage] = useState<string | null>(null);
	const [messageType, setMessageType] = useState<'success' | 'error' | null>(null);
	const [isLoading, setIsLoading] = useState(false);
	const [isCaptchaValid, setIsCaptchaValid] = useState(false);
	const captchaRef = useRef<CaptchaHandle>(null);

	const handleSubmit = async (event: FormEvent<HTMLFormElement>, preValidate?: () => boolean) => {
		event.preventDefault();

		if (isMultiStep && !isLastStep) return;

		if (preValidate && !preValidate()) return;

		setIsLoading(true);

		if (captcha && !captchaRef.current?.getToken()) {
			setMessage(labels.captchaRequired);
			setMessageType('error');
			setIsLoading(false);
			return;
		}

		const form = event.currentTarget;
		let serverErrorCode: string | undefined;
		let serverActionsProgress: {completed: number; total: number} | undefined;

		try {
			const formData = new FormData(form);

			if (captcha) {
				formData.delete(captcha.tokenField);
			}

			const interpolatedSubmissionMessage = interpolateMessage(submissionMessage, formData, locale);

			const rawCaptchaToken = captcha ? captchaRef.current?.getToken() : undefined;
			const captchaToken = rawCaptchaToken?.trim() || undefined;
			// Read once, at the moment of submit: one declared state backs every provider
			// rule, which is what lets the server evaluate them coherently.
			const logicStateHeader = buildLogicStateHeader(form);
			const targetUrl = submitActionUrl ?? form.action ?? window.location.href;

			// XHR is kept here because Jahia's CSRFGuard integrates with XMLHttpRequest rather than fetch.
			// Direct authenticated submissions to this servlet path are still protected server-side and
			// are not made valid purely by switching from fetch to XHR.
			const response = await new Promise<XMLHttpRequest>((resolve, reject) => {
				const xhr = new XMLHttpRequest();
				xhr.open('POST', targetUrl, true);
				if (captchaToken) {
					xhr.setRequestHeader('X-Formidable-Captcha-Token', captchaToken);
				}

				if (logicStateHeader) {
					xhr.setRequestHeader(FORM_LOGIC_STATE_HEADER, logicStateHeader);
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
			if (serverErrorCode === MAINTENANCE_ERROR_CODE) {
				setMessage(labels.maintenanceUnavailable);
				setMessageType('error');
				captchaRef.current?.reset();
				return;
			}
			const formData = new FormData(form);
			const interpolatedErrorMessage = interpolateMessage(errorMessage, formData, locale);
			const base = interpolatedErrorMessage || 'An error occurred while submitting the form.';
			const details: string[] = [];
			if (serverErrorCode) {
				details.push(`${labels.errorCode}: ${serverErrorCode}`);
			}
			if (serverActionsProgress) {
				details.push(labels.actionsProgress(serverActionsProgress.completed, serverActionsProgress.total));
			}
			const full = details.length > 0
				? `${base}<br><small class="fmdb-message-details fmdb-message-error-details">${details.join(' — ')}</small>`
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

	return {
		message,
		messageType,
		isLoading,
		isCaptchaValid,
		setIsCaptchaValid,
		captchaRef,
		handleSubmit,
		showForm,
	};
}
