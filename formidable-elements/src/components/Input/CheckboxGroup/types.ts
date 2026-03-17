import type {ReactNode} from "react";

export interface CheckboxOption {
	value: string;
	label: string;
}

export interface CheckboxGroupClientProps {
	label?: string;
	required?: boolean;
	errorMessage?: string;
	children: ReactNode;
}
