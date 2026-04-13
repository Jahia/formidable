import type {JCRNodeWrapper} from "org.jahia.services.content";
import {type ReactNode} from 'react';

// Base interface for server-side props
export interface FormServerProps {
	intro?: string;
	submissionMessage?: string;
	errorMessage?: string;
	customTarget?: string;
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
	captchaSiteKey?: string;
	captchaScriptUrl?: string;
	children: ReactNode;
}
