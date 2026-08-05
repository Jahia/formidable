import {useEffect, useRef, useState} from 'react';
import {useTranslation} from 'react-i18next';
import './range.css';

interface RangeInputProps {
	name: string;
	inputId: string;
	helpId?: string;
	datalistId?: string;
	minValue: number;
	maxValue: number;
	step?: number;
	defaultValue?: number;
	minLabel?: string;
	maxLabel?: string;
	required?: boolean;
	title?: string;
	autofocus?: boolean;
	disabled?: boolean;
	form?: string;
	validationAttributes: Record<string, string | undefined>;
}

/**
 * Range slider that submits nothing until the visitor interacts: the visible
 * slider is unnamed and mirrors its value into a hidden named input, empty while
 * unanswered. This keeps an untouched slider distinguishable from an answered one
 * (no pre-answered bias) and gives 'required' a real meaning for this control.
 */
export default function RangeInput({
	name,
	inputId,
	helpId,
	datalistId,
	minValue,
	maxValue,
	step,
	defaultValue,
	minLabel,
	maxLabel,
	required = false,
	title,
	autofocus,
	disabled,
	form,
	validationAttributes
}: RangeInputProps) {
	const {t} = useTranslation('formidable-elements', {keyPrefix: 'fmdb_inputRange'});
	const initialValue = defaultValue !== undefined ? String(defaultValue) : '';
	const [value, setValue] = useState<string>(initialValue);
	const answered = value !== '';
	const rangeRef = useRef<HTMLInputElement>(null);
	const hiddenRef = useRef<HTMLInputElement>(null);
	const mountedRef = useRef(false);

	// The thumb needs a position even while unanswered; the midpoint mirrors the
	// browser default for a valueless range.
	const displayValue = answered ? value : String(minValue + ((maxValue - minValue) / 2));

	// Selecting exactly the displayed position fires no change event, so any
	// completed interaction (pointer release, key release) also counts as an answer.
	const confirmCurrentPosition = () => {
		if (!answered && rangeRef.current) {
			setValue(rangeRef.current.value);
		}
	};

	// An untouched slider holds no submitted value: surface that through the
	// constraint-validation API so the shared form validation (validateInputs /
	// invalid events) shows an inline error, exactly like the checkbox group does.
	useEffect(() => {
		const range = rangeRef.current;
		if (!range) {
			return;
		}

		if (required && !answered) {
			const message = range.getAttribute('data-fmdb-msg-value-missing') || t('required');
			range.setCustomValidity(message);
		} else {
			range.setCustomValidity('');
		}
	}, [required, answered, t]);

	// Conditional logic evaluates on the bubbling input event, BEFORE React commits
	// the new value to the hidden input — re-dispatch a change event after the commit
	// so rules see the fresh value.
	useEffect(() => {
		if (!mountedRef.current) {
			mountedRef.current = true;
			return;
		}

		hiddenRef.current?.dispatchEvent(new Event('change', {bubbles: true}));
	}, [value]);

	// A controlled slider does not follow native form reset: restore the initial state.
	useEffect(() => {
		const formElement = rangeRef.current?.form;
		if (!formElement) {
			return;
		}

		const handleReset = () => setValue(initialValue);
		formElement.addEventListener('reset', handleReset);
		return () => formElement.removeEventListener('reset', handleReset);
	}, [initialValue]);

	return (
		<>
			<div className="fmdb-range-row">
				{minLabel && <span className="fmdb-range-end-label">{minLabel}</span>}
				<input
					ref={rangeRef}
					type="range"
					id={inputId}
					className="fmdb-form-control fmdb-range"
					aria-describedby={helpId}
					aria-valuetext={answered ? value : t('unanswered')}
					min={minValue}
					max={maxValue}
					step={step}
					list={datalistId}
					value={displayValue}
					title={title}
					autoFocus={autofocus}
					disabled={disabled}
					form={form}
					onChange={event => setValue(event.target.value)}
					onPointerUp={confirmCurrentPosition}
					onKeyUp={confirmCurrentPosition}
					{...validationAttributes}
				/>
				{maxLabel && <span className="fmdb-range-end-label">{maxLabel}</span>}
				<output htmlFor={inputId} className="fmdb-range-output" aria-hidden="true">
					{answered ? value : '–'}
				</output>
			</div>
			<input ref={hiddenRef} type="hidden" name={name} value={value} form={form}/>
		</>
	);
}
