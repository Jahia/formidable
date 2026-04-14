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
	stepLabels?: string[];
}

// Client-side props extend server props with additional properties
export interface FormProps extends FormServerProps {
	formId: string;
	locale: string;
	children: ReactNode;
}
