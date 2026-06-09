import {type RefObject, useEffect} from 'react';
import {resolveValidationMessage, showFieldError, clearFieldError, clearAllFieldErrors} from '~/utils/validationUtils';

type FormInputElement = HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement;

interface UseCustomValidationOptions {
	formRef: RefObject<HTMLFormElement | null>;
}

export function useCustomFormValidation({formRef}: UseCustomValidationOptions) {
	useEffect(() => {
		const form = formRef.current;
		if (!form) return;

		const handleInvalid = (e: Event) => {
			e.preventDefault();
			const input = e.target as FormInputElement;
			showFieldError(input, resolveValidationMessage(input));
		};

		const handleInput = (e: Event) => {
			const input = e.target as FormInputElement;
			if (input.validity.valid) {
				clearFieldError(input);
			}
		};

		const handleFormReset = () => {
			clearAllFieldErrors(form);
		};

		form.addEventListener('invalid', handleInvalid, true);
		form.addEventListener('input', handleInput);
		form.addEventListener('change', handleInput);
		form.addEventListener('reset', handleFormReset);

		return () => {
			form.removeEventListener('invalid', handleInvalid, true);
			form.removeEventListener('input', handleInput);
			form.removeEventListener('change', handleInput);
			form.removeEventListener('reset', handleFormReset);
		};
	}, [formRef]);
}

export function validateInputs(container: HTMLElement): boolean {
	const allInputs = Array.from(container.querySelectorAll<FormInputElement>('input, select, textarea'));
	const seenGroups = new Set<string>();
	const inputs = allInputs.filter(input => {
		if (!(input instanceof HTMLInputElement)) return true;
		if (input.type !== 'radio' && input.type !== 'checkbox') return true;
		if (!input.name) return true;

		const groupKey = `${input.type}:${input.name}`;
		if (seenGroups.has(groupKey)) return false;
		seenGroups.add(groupKey);
		return true;
	});

	let firstInvalid: HTMLElement | null = null;
	let allValid = true;

	inputs.forEach(input => {
		if (!input.validity.valid) {
			showFieldError(input, resolveValidationMessage(input));
			if (!firstInvalid) firstInvalid = input;
			allValid = false;
		} else {
			clearFieldError(input);
		}
	});

	if (firstInvalid) (firstInvalid as HTMLElement).focus();
	return allValid;
}
