import type {JCRNodeWrapper} from "org.jahia.services.content";
import {type ReactNode} from 'react';

export type CaptchaProvider = 'turnstile' | 'hcaptcha' | 'recaptcha_v2';

// Props mapped from JCR node properties by the JS modules library
export interface FormServerProps {
	intro?: string;
	submissionMessage?: string;
	errorMessage?: string;
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
	destination?: JCRNodeWrapper;
}

// Props passed to the Form Island (client-side)
export interface FormProps extends Omit<FormServerProps, 'destination'> {
	formId: string;
	locale: string;
	stepLabels?: string[];
	captcha?: {siteKey: string; provider: CaptchaProvider};
	destinationUrl?: string;
	submitActionUrl?: string;
	children: ReactNode;
}
