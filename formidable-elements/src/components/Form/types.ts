import type {JCRNodeWrapper} from "org.jahia.services.content";
import {type ReactNode} from 'react';

export type CaptchaProvider = 'turnstile' | 'hcaptcha' | 'recaptcha_v2';

// Base interface for server-side props
export interface FormServerProps {
	intro?: string;
	submissionMessage?: string;
	errorMessage?: string;
	customTarget?: string;
	submitActionUrl?: string;
	showResetBtn?: boolean;
	showNewFormBtn?: boolean;
	showTryAgainBtn?: boolean;
	submitBtnLabel?: string;
	resetBtnLabel?: string;
	newFormBtnLabel?: string;
	tryAgainBtnLabel?: string;
	previousBtnLabel?: string;
	nextBtnLabel?: string;
	showStepsNav?: boolean;
	css?: string;
	captchaConfig?: JCRNodeWrapper;
}

// Client-side props extend server props with additional properties
export interface FormProps extends Omit<FormServerProps, 'captchaConfig'> {
	formId: string;
	locale: string;
	stepLabels?: string[];
	captcha?: {siteKey: string; provider: CaptchaProvider};
	children: ReactNode;
}
