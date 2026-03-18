import {useCallback, useEffect, useRef, useState} from 'react';
import type {CheckboxGroupClientProps} from './types';
import {useTranslation} from "react-i18next";

export default function CheckboxGroup({
																				label,
																				required = false,
																				errorMessage,
																				children
																			}: CheckboxGroupClientProps) {
	const [isValid, setIsValid] = useState(true);
	const fieldsetRef = useRef<HTMLFieldSetElement>(null);
	const {t} = useTranslation('formidable-elements', {keyPrefix: 'fmdb_inputCheckboxGroup'});

	// Validation logic extracted to a callback
	const validateGroup = useCallback((checkboxes: HTMLInputElement[]) => {
		const hasChecked = checkboxes.some(cb => cb.checked);

		// Use custom error message or fallback to translation
		const validationMessage = errorMessage || t('error');

		// Update custom validity on all checkboxes in the group
		checkboxes.forEach(checkbox => {
			checkbox.setCustomValidity(hasChecked ? '' : validationMessage);
		});

		return hasChecked;
	}, [errorMessage,t]);

	useEffect(() => {
		if (!required || !fieldsetRef.current) return;

		const fieldset = fieldsetRef.current;
		const checkboxes = Array.from(
			fieldset.querySelectorAll<HTMLInputElement>('input[type="checkbox"]')
		);

		if (checkboxes.length === 0) return;

		// Handle checkbox change events
		const handleCheckboxChange = () => {
			const valid = validateGroup(checkboxes);
			setIsValid(valid);
		};

		// Attach change listeners to all checkboxes
		checkboxes.forEach(checkbox => {
			checkbox.addEventListener('change', handleCheckboxChange);
		});

		// Initial validation
		validateGroup(checkboxes);

		// Cleanup
		return () => {
			checkboxes.forEach(checkbox => {
				checkbox.removeEventListener('change', handleCheckboxChange);
			});
		};
	}, [required, validateGroup]);

	return (
		<fieldset
			ref={fieldsetRef}
			className="fmdb-form-group fmdb-checkbox-group"
			aria-invalid={!isValid}
			aria-required={required}
		>
			{label && (
				<legend className="fmdb-group-legend">
					{label}
					{required && <span className="fmdb-required-indicator" aria-label="required">*</span>}
				</legend>
			)}
			{children}
		</fieldset>
	);
}
